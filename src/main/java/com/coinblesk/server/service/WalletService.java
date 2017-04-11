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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import com.google.common.annotations.VisibleForTesting;
import org.apache.commons.lang3.ArrayUtils;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.BlockChain;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.PeerGroup;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionBroadcast;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.core.listeners.DownloadProgressTracker;
import org.bitcoinj.net.discovery.DnsDiscovery;
import org.bitcoinj.net.discovery.PeerDiscovery;
import org.bitcoinj.script.Script;
import org.bitcoinj.store.BlockStore;
import org.bitcoinj.store.BlockStoreException;
import org.bitcoinj.store.SPVBlockStore;
import org.bitcoinj.utils.ContextPropagatingThreadFactory;
import org.bitcoinj.wallet.UnreadableWalletException;
import org.bitcoinj.wallet.Wallet;
import org.bitcoinj.wallet.Wallet.BalanceType;
import org.bitcoinj.wallet.WalletTransaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.coinblesk.bitcoin.AddressCoinSelector;
import com.coinblesk.bitcoin.BitcoinNet;
import com.coinblesk.server.config.AppConfig;
import com.coinblesk.server.entity.Account;
import com.coinblesk.server.entity.TimeLockedAddressEntity;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;

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
	private AccountService accountService;

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
			// TODO: add keychaingroup for restoring wallet
			wallet = new Wallet(params);
		}
		wallet.autosaveToFile(walletFile, 5, TimeUnit.SECONDS, null);

		walletWatchKeysCLTV(params);
		walletWatchKeysPot(params);

		blockStore = new SPVBlockStore(params, chainFile);
		blockChain = new BlockChain(params, blockStore);
		peerGroup = new PeerGroup(params, blockChain);

		blockChain.addWallet(wallet);
		peerGroup.addWallet(wallet);

		// If we're in unittest net we don't need any peer discovery or chain download logic
		// and we are done here.
		if (appConfig.getBitcoinNet() == BitcoinNet.UNITTEST) {
			LOG.info("wallet init done.");
			return;
		}

		PeerDiscovery discovery = null;
		String[] ownFullnodes = new String[] { appConfig.getFirstSeedNode() };
		switch (appConfig.getBitcoinNet()) {
			case MAINNET:
				// For mainnet we use the default seed list but with out own fullnode added as the first node
				discovery = new DnsDiscovery(ArrayUtils.addAll(ownFullnodes, params.getDnsSeeds()), params);
				break;
			case TESTNET:
				// For testnet we don't bother with other fullnodes and keep things simple by only connecting to
				// our own fullnode(s).
				discovery = new DnsDiscovery(ownFullnodes, params);
				peerGroup.setMaxConnections(ownFullnodes.length);
				break;
		}
		peerGroup.addPeerDiscovery(discovery);
		peerGroup.start();

		// Download block chain (blocking)
		final DownloadProgressTracker downloadListener = new DownloadProgressTracker() {
			@Override
			protected void doneDownload() {
				LOG.info("downloading done");

				// once we downloaded all the blocks we need to broadcast the
				// stored approved tx
				List<Transaction> txs = transactionService.listApprovedTransactions(params);
				for (Transaction tx : txs) {
					broadcast(tx);
				}

				// Be notified when the confidence of relevant transactions
				// change (number of confirmations).
				addConficenceChangedHandler();
			}

			@Override
			protected void progress(double pct, int blocksSoFar, Date date) {
				LOG.info("downloading: {}%", (int) pct);
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

	private void walletWatchKeysCLTV(final NetworkParameters params) {
		StringBuilder sb = new StringBuilder();
		for (Account key : accountService.allAccounts()) {
			for (TimeLockedAddressEntity address : key.timeLockedAddresses()) {
				wallet.addWatchedAddress(address.toAddress(params), address.getTimeCreated());
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

	/***
	 * Add a listener for when a transaction we are watching's confidence
	 * changed due to a new block.
	 *
	 * After the transaction is {bitcoin.minconf} blocks deep, we remove the tx
	 * from the database, as it is considered safe.
	 *
	 * The method should only be called after complete download of the
	 * blockchain, since the handler is called for every block and transaction
	 * we are watching, which will result in high CPU and memory consumption and
	 * might exceed the JVM memory limit. After download is complete, blocks
	 * arrive only sporadically and this is not a problem.
	 */
	private void addConficenceChangedHandler() {
		// Use a custom thread pool to speed up the processing of transactions.
		// Queue is blocking and limited to 10'000
		// to avoid memory exhaustion. After threshold is reached, the
		// CallerRunsPolicy() forces blocking behavior.
		ContextPropagatingThreadFactory factory = new ContextPropagatingThreadFactory("listenerFactory");
		Executor listenerExecutor = new ThreadPoolExecutor(Runtime.getRuntime().availableProcessors(),
				Runtime.getRuntime().availableProcessors(), 0L, TimeUnit.MILLISECONDS,
				new LinkedBlockingQueue<Runnable>(10000), factory, new ThreadPoolExecutor.CallerRunsPolicy());

		wallet.addTransactionConfidenceEventListener(listenerExecutor, (wallet, tx) -> {
			if (tx.getConfidence().getDepthInBlocks() >= appConfig.getMinConf() && !removed.contains(tx.getHash())) {
				LOG.debug("remove tx we got from the network {}", tx);

				try {
					transactionService.removeTransaction(tx);
				} catch (EmptyResultDataAccessException e) {
					LOG.debug("tx was not in tx table {}", tx);
				}

				try {
					txQueueService.removeTx(tx);
				} catch (EmptyResultDataAccessException e) {
					LOG.debug("tx was not in txqueue table {}", tx);
				}

				removed.add(tx.getHash());
			}
		});
	}

	public List<TransactionOutput> potTransactionOutput(final NetworkParameters params) {
		ECKey potAddress = appConfig.getPotPrivateKeyAddress();
		final List<TransactionOutput> retVal = new ArrayList<TransactionOutput>();
		for (TransactionOutput output : wallet.getWatchedOutputs(true)) {
			Address to = output.getScriptPubKey().getToAddress(params);
			if (!to.isP2SHAddress()) {
				if (to.equals(potAddress.toAddress(params))) {
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

	/**
	 * The unspent tx also contains spentOutputs, where the approved tx should
	 * mark the unspent as spent!
	 *
	 * @param params
	 * @return
	 */
	@Transactional(readOnly = true)
	public Map<Sha256Hash, Transaction> verifiedTransactions(NetworkParameters params) {
		Map<Sha256Hash, Transaction> copy = new HashMap<>(wallet.getTransactionPool(WalletTransaction.Pool.UNSPENT));
		Map<Sha256Hash, Transaction> copy2 = new HashMap<>(copy);
		// also add approved Tx
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
				// TODO: the method name is misleading, it stops auto-save, but
				// does not save.
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
			return null;
		}
		return tx.getOutput(input.getOutpoint().getIndex());
	}

	public void broadcast(final Transaction fullTx) {
		txQueueService.addTx(fullTx);
		// broadcast immediately
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
					// wait ten minutes
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
		for (Transaction tx : txQueueService.all(params)) {
			broadcast(tx);
		}
	}


	/***
	 * Gets the current balance from the wallet including all tracked addresses
	 * and pot itself. Used by AdminController.
	 *
	 * @return Coin
	 */
	public Coin getBalance() {
		return wallet.getBalance(BalanceType.ESTIMATED);
	}

	/***
	 * Returns a Map listing all watched scripts and their corresponding balance.
	 * Used by AdminController.
	 *
	 * @return
	 */
	public Map<Address, Coin> getBalanceByAddresses() {
		final NetworkParameters params = appConfig.getNetworkParameters();

		AddressCoinSelector selector = new AddressCoinSelector(null, params);
		wallet.getBalance(selector);

		// getBalance considers UTXO: add zero balance for all watched scripts
		// without unspent outputs
		Map<Address, Coin> fullBalances = new HashMap<>(selector.getAddressBalances());
		for (Script watched : wallet.getWatchedScripts()) {
			Address address = watched.getToAddress(params);
			if (!fullBalances.containsKey(address)) {
				fullBalances.put(address, Coin.ZERO);
			}
		}

		return fullBalances;
	}

	/***
	 * List all unspent outputs tracked by the wallet.
	 * Used by AdminController.
	 *
	 * @return List of {@link org.bitcoinj.core.TransactionOutput} of all unspent outputs tracked by bitcoinj.
	 */
	public List<TransactionOutput> getUnspentOutputs() {
		return wallet.getUnspents();
	}

	@VisibleForTesting
	public Wallet getWallet() {
		return wallet;
	}
}
