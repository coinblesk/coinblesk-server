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

import com.coinblesk.server.config.AppConfig;
import com.coinblesk.bitcoin.BitcoinNet;
import com.coinblesk.util.BitcoinUtils;
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
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.PeerAddress;
import org.bitcoinj.core.PeerGroup;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.core.Utils;
import org.bitcoinj.core.listeners.DownloadProgressTracker;
import org.bitcoinj.core.listeners.TransactionConfidenceEventListener;
import org.bitcoinj.net.discovery.DnsDiscovery;
import org.bitcoinj.script.Script;
import org.bitcoinj.store.BlockStore;
import org.bitcoinj.store.BlockStoreException;
import org.bitcoinj.store.SPVBlockStore;
import org.bitcoinj.wallet.UnreadableWalletException;
import org.bitcoinj.wallet.Wallet;
import org.bitcoinj.wallet.WalletTransaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


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

    private Wallet wallet;

    private BlockChain blockChain;

    private PeerGroup peerGroup;

    private BlockStore blockStore;

    @PostConstruct
    public void init() throws IOException, UnreadableWalletException, BlockStoreException {
        final NetworkParameters params = appConfig.getNetworkParameters();
        //create directory if necessary
        final File directory = appConfig.getConfigDir().getFile();
        if (!directory.exists()) {
            if (!directory.mkdirs()) {
                throw new IOException("Could not create directory " + directory.getAbsolutePath());
            }
        }
        //locations
        final File chainFile = new File(directory, "coinblesk2-" + appConfig.getBitcoinNet() + ".spvchain");
        final File walletFile = new File(directory, "coinblesk2-" + appConfig.getBitcoinNet() + ".wallet");

        if (BitcoinNet.of(appConfig.getBitcoinNet()) == BitcoinNet.UNITTEST) {
            chainFile.delete();
            walletFile.delete();
            LOG.debug("Deleted file {} and {}", chainFile.getName(), walletFile.getName());
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
        //TODO: do we nood this?
        //wallet.currentAddress(KeyChain.KeyPurpose.RECEIVE_FUNDS);
        wallet.autosaveToFile(walletFile, 5, TimeUnit.SECONDS, null);
        walletWatchKeys();
        blockStore = new SPVBlockStore(params, chainFile);
        blockChain = new BlockChain(params, blockStore);
        peerGroup = new PeerGroup(params, blockChain);

        if (BitcoinNet.of(appConfig.getBitcoinNet()) == BitcoinNet.UNITTEST) {
            peerGroup.addAddress(new PeerAddress(InetAddress.getLocalHost(), params.getPort()));
        } else {
            //peerGroup handles the shutdown for us
            //TODO: connect to real peers
            DnsDiscovery discovery = new DnsDiscovery(params);
            peerGroup.addPeerDiscovery(discovery);
        }

        blockChain.addWallet(wallet);
        peerGroup.addWallet(wallet);
        installShutdownHook();
        peerGroup.start();
        final DownloadProgressTracker listener = new DownloadProgressTracker();
        peerGroup.startBlockChainDownload(listener);
        //TODO: add wallet listener, and remove burnedoutputs when confirmed tx 
        // has those outputs (maintenance)
        //also remove the approved tx, once we see them in the blockchain (maintenance)
        wallet.addTransactionConfidenceEventListener(new TransactionConfidenceEventListener() {
			@Override
			public void onTransactionConfidenceChanged(Wallet wallet, Transaction tx) {
                if (tx.getConfidence().getDepthInBlocks() >= appConfig.getMinConf()) {
                    transactionService.removeTransaction(tx);
                }
            }
        });
    }

    private void walletWatchKeys() {
        final List<List<ECKey>> all = keyService.all();
        final List<Script> scripts = new ArrayList<>();
        for (List<ECKey> keys : all) {
            final Script script = BitcoinUtils.createP2SHOutputScript(2, keys);
            scripts.add(script);
        }
        wallet.addWatchedScripts(scripts);
    }

    public BlockChain blockChain() {
        return blockChain;
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

        List<Transaction> approvedTx = transactionService.approvedTx(params);
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
                peerGroup.stop();
                peerGroup = null;
            }
        } catch (Exception e) {
            LOG.error("cannot stop peerGroup in shutdown", e);
        }
        try {
            if (blockStore != null) {
                blockStore.close();
                blockStore = null;
            }
        } catch (Exception e) {
            LOG.error("cannot close blockStore in shutdown", e);
        }
        try {
            if (wallet != null) {
            	// TODO: the method name is misleading, it stops auto-save, but does not save.
            	wallet.shutdownAutosaveAndWait();
                wallet = null;
            }
        } catch (Exception e) {
            LOG.error("cannot shutdown wallet in shutdown", e);
        }
    }

    private void installShutdownHook(/*final PeerGroup peerGroup, final BlockStore blockStore, 
            final Wallet wallet*/) {
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override public void run() {
                shutdown();
            }
        });
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
            return null;
        }
        return tx.getOutput(input.getOutpoint().getIndex());
    }
}
