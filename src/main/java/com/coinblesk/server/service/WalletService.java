/*
 * Copyright 2016 The Coinblesk team and the CSG Group at University of Zurich
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.coinblesk.server.service;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.BlockChain;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.PeerAddress;
import org.bitcoinj.core.PeerGroup;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionBroadcast;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutput;
import org.apache.commons.lang3.ArrayUtils;
import org.bitcoinj.core.listeners.DownloadProgressTracker;
import org.bitcoinj.core.listeners.TransactionConfidenceEventListener;
import org.bitcoinj.net.discovery.DnsDiscovery;
import org.bitcoinj.script.Script;
import org.bitcoinj.store.BlockStore;
import org.bitcoinj.store.BlockStoreException;
import org.bitcoinj.store.SPVBlockStore;
import org.bitcoinj.wallet.UnreadableWalletException;
import org.bitcoinj.wallet.Wallet;
import org.bitcoinj.wallet.Wallet.BalanceType;
import org.bitcoinj.wallet.WalletTransaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.coinblesk.bitcoin.AddressCoinSelector;
import com.coinblesk.bitcoin.BitcoinNet;
import com.coinblesk.server.config.AppConfig;
import com.coinblesk.server.entity.Keys;
import com.coinblesk.server.entity.TimeLockedAddressEntity;
import com.coinblesk.util.BitcoinUtils;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;


/**
 *
 * @author Thomas Bocek
 */
@Service
public class WalletService {

    private final static Logger LOG = LoggerFactory.getLogger(WalletService.class);

    @Autowired
    private AppConfig appConfig;

    @Autowired
    private KeyService keyService;

    @Autowired
    private TransactionService transactionService;
    
    @Autowired
    private TxQueueService txQueueService;

    private Wallet wallet;

    private BlockChain blockChain;

    private PeerGroup peerGroup;

    private BlockStore blockStore;
    
    private Set<Sha256Hash> removed = Collections.synchronizedSet(new HashSet<Sha256Hash>());

    @PostConstruct
    public void init() throws IOException, UnreadableWalletException, BlockStoreException, InterruptedException {
        final NetworkParameters params = appConfig.getNetworkParameters();

        // Create config directory if necessary
        final File directory = appConfig.getConfigDir().getFile();
        if (!directory.exists()) {
            if (!directory.mkdirs()) {
                throw new IOException("Could not create directory " + directory.getAbsolutePath());
            }
        }
        // Init chain and wallet files
        final File chainFile = new File(directory, "coinblesk2-" + appConfig.getBitcoinNet() + ".spvchain");
        final File walletFile = new File(directory, "coinblesk2-" + appConfig.getBitcoinNet() + ".wallet");

        // Delete chain and wallet file when using UNITTEST network
        if (appConfig.getBitcoinNet() == BitcoinNet.UNITTEST) {
            chainFile.delete();
            walletFile.delete();
            LOG.info("Deleted file {} and {}", chainFile.getName(), walletFile.getName());
        }

        if (walletFile.exists()) {
            wallet = Wallet.loadFromFile(walletFile);
            if (!chainFile.exists()) {
                wallet.reset();
            }
        } else {
            //TODO: add keychaingroup for restoring wallet
            wallet = new Wallet(params);
        }
        wallet.autosaveToFile(walletFile, 5, TimeUnit.SECONDS, null);

        walletWatchKeysP2SH(params);
        walletWatchKeysCLTV(params);
        walletWatchKeysPot(params);
        
        blockStore = new SPVBlockStore(params, chainFile);
        blockChain = new BlockChain(params, blockStore);
        peerGroup = new PeerGroup(params, blockChain);

        // Add own fullnode to list of seeds
        String[] seeds = ArrayUtils.addAll(new String[] {appConfig.getFirstSeedNode()},params.getDnsSeeds());
        if (appConfig.getBitcoinNet() != BitcoinNet.MAINNET) {
            DnsDiscovery discovery = new DnsDiscovery(seeds, params);
            peerGroup.addPeerDiscovery(discovery);
        }

        blockChain.addWallet(wallet);
        peerGroup.addWallet(wallet);

        if (appConfig.getBitcoinNet() != BitcoinNet.UNITTEST) {
            peerGroup.start();
        }

        wallet.addTransactionConfidenceEventListener((wallet, tx) -> {
            if (tx.getConfidence().getDepthInBlocks() >= appConfig.getMinConf() && !removed.contains(tx.getHash())) {
                LOG.debug("remove tx we got from the network {}", tx);
                transactionService.removeTransaction(tx);
                txQueueService.removeTx(tx);
                removed.add(tx.getHash());
            }
        });

        // Download block chain (blocking)
        final DownloadProgressTracker downloadListener = new DownloadProgressTracker() {
            @Override
            protected void doneDownload() {
                LOG.info("downloading done");
                //once we downloaded all the blocks we need to broadcast the stored approved tx
                List<Transaction> txs = transactionService.listApprovedTransactions(params);
                for(Transaction tx:txs) {
                    broadcast(tx);
                }
            }

            @Override
            protected void progress(double pct, int blocksSoFar, Date date) {
                LOG.info("downloading: {}%", (int)pct);
            }

        };

        if (appConfig.getBitcoinNet() != BitcoinNet.UNITTEST) {
            peerGroup.startBlockChainDownload(downloadListener);
            downloadListener.await();
        }

        // Broadcast pending transactions
        pendingTransactions();

        LOG.info("wallet init done.");
    } 

	private void walletWatchKeysP2SH(final NetworkParameters params) {
		StringBuilder sb = new StringBuilder();
        final List<List<ECKey>> all = keyService.all();
        final List<Script> scripts = new ArrayList<>();
        for (List<ECKey> keys : all) {
            final Script script = BitcoinUtils.createP2SHOutputScript(2, keys);
            script.setCreationTimeSeconds(0);
            scripts.add(script);
            sb.append(script.getToAddress(params)).append("\n");
        }
        wallet.addWatchedScripts(scripts);
        LOG.info("walletWatchKeysP2SH:\n{}", sb.toString());
    }
    
    private void walletWatchKeysCLTV(final NetworkParameters params) {
    	StringBuilder sb = new StringBuilder();
        for (Keys key : keyService.allKeys()) {
        	for (TimeLockedAddressEntity address : key.timeLockedAddresses()) {
        		wallet.addWatchedAddress(address.toAddress(params), 0);
        		sb.append(address.toAddress(params)).append("\n");
        	}
        }
        LOG.info("walletWatchKeysCLTV:\n{}", sb.toString());
    }
    
    private void walletWatchKeysPot(final NetworkParameters params) {
        ECKey potAddress = appConfig.getPotPrivateKeyAddress();
        wallet.addWatchedAddress(potAddress.toAddress(params), appConfig.getPotCreationTime());
        LOG.info("walletWatchKeysPot: {}", potAddress.toAddress(params));
    }
    
    public List<TransactionOutput> potTransactionOutput(final NetworkParameters params) {
        ECKey potAddress = appConfig.getPotPrivateKeyAddress();
        final List<TransactionOutput> retVal = new ArrayList<TransactionOutput>();
        for(TransactionOutput output:wallet.getWatchedOutputs(true)) {
            Address to = output.getScriptPubKey().getToAddress(params);
            if(!to.isP2SHAddress()) {
                if(to.equals(potAddress.toAddress(params))) {
                    retVal.add(output);
                }
            }
        }
        return retVal;
    }

    public BlockChain blockChain() {
        return blockChain;
    }
    
    public void addWatching(Address address) {
        wallet.addWatchedAddress(address);
    }

    public void addWatching(Script script) {
        List<Script> list = new ArrayList<>(1);
        list.add(script);
        wallet.addWatchedScripts(list);
    }

    /**
     * The unspent tx also contains spentOutputs, where the approved tx should mark the unspent as spent!
     *
     * @param params
     * @return
     */
    @Transactional(readOnly = true)
    public Map<Sha256Hash, Transaction> verifiedTransactions(NetworkParameters params) {
        Map<Sha256Hash, Transaction> copy = new HashMap<>(wallet.getTransactionPool(
                WalletTransaction.Pool.UNSPENT));
        Map<Sha256Hash, Transaction> copy2 = new HashMap<>(copy);
        //also add approved Tx
        for (Transaction t : copy.values()) {
            if (t.getConfidence().getDepthInBlocks() < appConfig.getMinConf()) {
                LOG.debug("not enough confirmations for {}", t.getHash());
                copy2.remove(t.getHash());
            }
        }

        for (Transaction t : copy2.values()) {
            LOG.debug("unspent tx from Network: {}", t.getHash());
        }

        List<Transaction> approvedTx = transactionService.listApprovedTransactions(params);
        for (Transaction t : approvedTx) {
            LOG.debug("adding approved tx, which can be used for spending: {}", t);
            copy2.put(t.getHash(), t);
        }
        return copy2;
    }

    @Transactional(readOnly = true)
    public List<TransactionOutput> verifiedOutputs(NetworkParameters params, Address p2shAddress) {
        List<TransactionOutput> retVal = new ArrayList<>();

        Map<Sha256Hash, Transaction> unspent = verifiedTransactions(params);

        for (Transaction t : unspent.values()) {
            for (TransactionOutput out : t.getOutputs()) {
                if (p2shAddress.equals(out.getAddressFromP2SH(appConfig.getNetworkParameters()))) {
                    if (out.isAvailableForSpending()) {
                        //now check if not spent
                        retVal.add(out);
                        LOG
                                .debug("this txout is unspent : {}, point: {}, spent {}", out, out
                                        .getOutPointFor());
                    } else {
                        LOG.debug("this txout is spent!: {}, point: {}out.getOutPointFor()", out, out
                                .getOutPointFor());
                    }
                }
            }
        }

        return retVal;
    }

    public long balance(NetworkParameters params, Address p2shAddress) {
        long balance = 0;
        for (TransactionOutput transactionOutput : verifiedOutputs(params, p2shAddress)) {
            balance += transactionOutput.getValue().value;
        }
        return balance;
    }

    @PreDestroy
    public void shutdown() {
        try {
            if (peerGroup != null && peerGroup.isRunning()) {
                LOG.info("stopping peer group...");
                peerGroup.stop();
                peerGroup = null;
            }
        } catch (Exception e) {
            LOG.error("cannot stop peerGroup in shutdown", e);
        }
        try {
            if (blockStore != null) {
                LOG.info("closing block store...");
                blockStore.close();
                blockStore = null;
            }
        } catch (Exception e) {
            LOG.error("cannot close blockStore in shutdown", e);
        }
        try {
            if (wallet != null) {
            	// TODO: the method name is misleading, it stops auto-save, but does not save.
                LOG.info("shutdown wallet...");
            	wallet.shutdownAutosaveAndWait();
                wallet = null;
            }
        } catch (Exception e) {
            LOG.error("cannot shutdown wallet in shutdown", e);
        }
    }

    public PeerGroup peerGroup() {
        return peerGroup;
    }

    public Transaction receivePending(Transaction fullTx) {
        wallet.receivePending(fullTx, null, false);
        return wallet.getTransaction(fullTx.getHash());
    }

    public TransactionOutput findOutputFor(TransactionInput input) {
        Transaction tx = wallet.getTransaction(input.getOutpoint().getHash());
        if (tx == null) {
            //TODO: useDB?
            /*List<TransactionOutput> touts = wallet.getWatchedOutputs(true);
            for(TransactionOutput to:touts) {
                if(to.getOutPointFor().getHash().equals(input.getOutpoint().getHash()) &&
                        to.getOutPointFor().getIndex() == input.getOutpoint().getIndex()) {
                    LOG.debug("could not get TX from wallet, but we have it..." + to);
                    return to;
                }
            }
            LOG.debug("we only have the following tx hashes:");
            for(TransactionOutput to:touts) {
                LOG.debug("to:"+to.getOutPointFor().getHash()+":"+to.getOutPointFor().getIndex());
            }*/
            return null;
        }
        return tx.getOutput(input.getOutpoint().getIndex());
    }
    
    public void broadcast(final Transaction fullTx) {
        txQueueService.addTx(fullTx);
        //broadcast immediately
        final TransactionBroadcast broadcast = peerGroup().broadcastTransaction(fullTx);
        Futures.addCallback(broadcast.future(), new FutureCallback<Transaction>() {
            @Override
            public void onSuccess(Transaction transaction) {
                LOG.debug("broadcast success, transaction is out {}", fullTx.getHash());
                txQueueService.removeTx(fullTx);
            }

            @Override
            public void onFailure(Throwable throwable) {
                LOG.error("broadcast failed, transaction is " + fullTx.getHash(), throwable);
                try {
                    //wait ten minutes
                    Thread.sleep(10 * 60 * 1000);
                    broadcast(fullTx);
                } catch (InterruptedException ex) {
                    LOG.debug("don't wait for tx {}", fullTx.getHash());
                }

            }
        });

    }

    private void pendingTransactions() {
        final NetworkParameters params = appConfig.getNetworkParameters();
        for(Transaction tx:txQueueService.all(params)) {
            broadcast(tx);
        }
    }

	public Map<Address, Coin> getBalanceByAddresses() {
		final NetworkParameters params = appConfig.getNetworkParameters();
		
		AddressCoinSelector selector = new AddressCoinSelector(null, params);
		wallet.getBalance(selector);
		
		// getBalance considers UTXO: add zero balance for all watched scripts without unspent outputs
		Map<Address, Coin> fullBalances = new HashMap<>(selector.getAddressBalances());
		for (Script watched : getWatchedScripts()) {
			Address address = watched.getToAddress(params);
			if (!fullBalances.containsKey(address)) {
				fullBalances.put(address, Coin.ZERO);
			}
		}
		
		return fullBalances;
	}

	public List<Script> getWatchedScripts() {
		return wallet.getWatchedScripts();
	}

	public Coin getBalance() {
		return wallet.getBalance(BalanceType.ESTIMATED);
	}

	public List<TransactionOutput> getUnspentOutputs() {
		return wallet.getUnspents();
	}   
}
