package ch.uzh.csg.coinblesk.server.service;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.xml.bind.DatatypeConverter;

import org.apache.log4j.Logger;
import org.bitcoinj.core.ECKey.ECDSASignature;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.ProtocolException;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.crypto.ChildNumber;
import org.bitcoinj.crypto.DeterministicKey;
import org.bitcoinj.crypto.TransactionSignature;
import org.bitcoinj.kits.WalletAppKit;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.params.RegTestParams;
import org.bitcoinj.params.TestNet3Params;
import org.bitcoinj.params.UnitTestParams;
import org.bitcoinj.script.Script;
import org.springframework.stereotype.Service;

import ch.uzh.csg.coinblesk.bitcoin.BitcoinNet;
import ch.uzh.csg.coinblesk.responseobject.IndexAndDerivationPath;
import ch.uzh.csg.coinblesk.server.bitcoin.DoubleSignatureRequestedException;
import ch.uzh.csg.coinblesk.server.bitcoin.DummyPeerDiscovery;
import ch.uzh.csg.coinblesk.server.bitcoin.InvalidTransactionException;
import ch.uzh.csg.coinblesk.server.bitcoin.P2SHScript;
import ch.uzh.csg.coinblesk.server.bitcoin.SpentOutputsCache;
import ch.uzh.csg.coinblesk.server.clientinterface.IBitcoinWallet;
import ch.uzh.csg.coinblesk.server.util.ServerProperties;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.MoreExecutors;

/**
 * Abstraction of bitcoinJ
 */
@Service
public class BitcoinWalletService implements IBitcoinWallet {

    private static final Logger LOGGER = Logger.getLogger(BitcoinWalletService.class);

    /**
     * Prefix for wallet and blockstore files. This prefix will be prefixed with
     * the name of the network, eg testnet + WALLET_PREFIX
     */
    private static final String WALLET_PREFIX = "_bitcoinj";

    private BitcoinNet bitcoinNet;
    private boolean cleanWallet = false;

    private ReentrantLock lock;

    private WalletAppKit serverAppKit;
    private SpentOutputsCache outputsCache;

    private Map<TransactionInput, Long> signedInputs;

    public BitcoinWalletService() {
        this.outputsCache = new SpentOutputsCache();
        this.lock = new ReentrantLock();
        this.signedInputs = new HashMap<>();
    }

    @PostConstruct
    private void init() {
        if (cleanWallet) {
            clearWalletFiles();
        }
        start().awaitRunning();
        System.out.println("started");
    }

    /**
     * starts the wallet service
     * 
     * @return a {@link com.google.common.util.concurrent.Service} object
     */
    public com.google.common.util.concurrent.Service start() {

        // check if wallet has already been started
        if (serverAppKit != null && serverAppKit.isRunning()) {
            LOGGER.warn("Tried to initialize bitcoin wallet service even though it's already running.");
            return serverAppKit;
        }

        // set up the wallet

        if (bitcoinNet == null) {
            bitcoinNet = BitcoinNet.of(ServerProperties.getProperty("bitcoin.net"));
        }

        serverAppKit = getWalletAppKit();
        serverAppKit.setBlockingStartup(false);

        // configure app kit
        if (bitcoinNet == BitcoinNet.REGTEST) {
            // regtest only works with local bitcoind
            serverAppKit.connectToLocalHost();
        } else if (bitcoinNet == BitcoinNet.UNITTEST) {
            // set dummy discovery
            serverAppKit.setDiscovery(new DummyPeerDiscovery());
        } else {
            // server wallet settings for production
            serverAppKit.setAutoSave(true);
            serverAppKit.setBlockingStartup(false);
            serverAppKit.setUserAgent("CoinBlesk", "0.2");

            LOGGER.debug("Setting up wallet on " + bitcoinNet.toString());
        }

        return serverAppKit.startAsync();
    }

    /**
     * 
     * @param bitcoinNet
     * @return the {@link NetworkParameters} for a specific network
     */
    private NetworkParameters getNetworkParams(BitcoinNet bitcoinNet) {
        switch (bitcoinNet) {
        case UNITTEST:
            return UnitTestParams.get();
        case REGTEST:
            return RegTestParams.get();
        case TESTNET:
            return TestNet3Params.get();
        case MAINNET:
            return MainNetParams.get();
        default:
            throw new RuntimeException("Please set the server property bitcoin.net to (unittest|regtest|testnet|main)");
        }
    }

    /**
     * Sets up the wallet kit. If the chosen bitcoin network is
     * {@link BitcoinNet#REGTEST}, all previously persisted regtest files will
     * be removed.
     * 
     * @return
     */
    private WalletAppKit getWalletAppKit() {

        File walletDir = null;

        try {
            walletDir = new File(ServerProperties.getProperty("bitcoin.wallet.dir"));
        } catch (Exception e) {
            LOGGER.fatal("Bitcoin wallet directory must be set in server.properties", e);
            throw new RuntimeException("Bitcoin wallet directory must be set in server.properties");
        }

        for (File f : walletDir.listFiles()) {
            if (f.getName().startsWith(getWalletPrefix(bitcoinNet))) {

                // delete existing regtest wallet and blockstore files
                if (bitcoinNet == BitcoinNet.REGTEST) {
                    f.delete();
                    LOGGER.debug("Deleted file " + f.getName());
                }
            }
        }

        return new WalletAppKit(getNetworkParams(bitcoinNet), walletDir, getWalletPrefix(bitcoinNet));
    }

    /**
     * Returns the wallet prefix for a given {@link BitcoinNet}
     * 
     * @param bitcoinNet
     * @return
     */
    public static String getWalletPrefix(BitcoinNet bitcoinNet) {
        return bitcoinNet.toString().toLowerCase() + WALLET_PREFIX;
    }

    public void clearWalletFiles() {
        File[] walletFiles = new File(ServerProperties.getProperty("bitcoin.wallet.dir")).listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.startsWith(getWalletPrefix(bitcoinNet));
            }
        });
        for (File f : walletFiles) {
            f.delete();
        }
    }

    @PreDestroy
    private void shutdown() {
        LOGGER.info("Sutting down bitcoin wallet...");
        serverAppKit.stopAsync().awaitTerminated();
    }

    @Override
    public void setBitcoinNet(String bitcoinNet) {
        this.bitcoinNet = BitcoinNet.of(bitcoinNet);
    }

    @Override
    public void setCleanWallet(boolean cleanWallet) {
        this.cleanWallet = cleanWallet;
    }

    @Override
    public void backupWallet() {
        try {
            serverAppKit.wallet().saveToFile(new File(ServerProperties.getProperty("bitcoin.backup.dir")));
        } catch (IOException e) {
            LOGGER.error("Failed to create wallet backup", e);
        }
    }

    @Override
    public String getSerializedServerWatchingKey() {
        return serverAppKit.wallet().getWatchingKey().serializePubB58(getNetworkParams(bitcoinNet));
    }

    @Override
    public boolean signTxAndBroadcast(String partialTx, List<IndexAndDerivationPath> indexAndPaths) throws InvalidTransactionException {

        // deserialize the transaction...
        Transaction tx = null;
        try {
            tx = decodeTx(partialTx);
        } catch (ProtocolException | IllegalArgumentException e) {
            LOGGER.error("Signing transaction failed", e);
            throw new InvalidTransactionException("Transaction could not be parsed");
        }

        Transaction signedTx = signTx(tx, indexAndPaths);

        // transaction is fully signed now: let's broadcast it
        serverAppKit.peerGroup().broadcastTransaction(signedTx).broadcast().addListener(new Runnable() {
            @Override
            public void run() {
                LOGGER.info("Transaction was broadcasted");
            }
        }, MoreExecutors.sameThreadExecutor());

        return true;
    }

    @Override
    public BitcoinNet getBitcoinNet() {
        return bitcoinNet;
    }

    @Override
    public String signRefundTx(String partialTimeLockedTx, List<IndexAndDerivationPath> indexAndPath) throws InvalidTransactionException {

        // deserialize the transaction...
        Transaction tx = null;
        try {
            tx = decodeTx(partialTimeLockedTx);
        } catch (ProtocolException | IllegalArgumentException e) {
            LOGGER.error("Signing transaction failed", e);
            throw new InvalidTransactionException("Transaction could not be parsed");
        }

        // preconditions
        if (!tx.isTimeLocked()) {
            String errorMsg = "Tried to create a refund transaction but transaction was not time locked";
            LOGGER.warn(errorMsg);
            throw new InvalidTransactionException("Tried to create a refund transaction but transaction was not time locked");
        }

        Transaction signedTx = signTx(tx, indexAndPath);

        return encodeTx(signedTx);
    }

    private Transaction decodeTx(String base64encodedTx) throws ProtocolException, IllegalArgumentException {
        return new Transaction(getNetworkParams(bitcoinNet), Base64.getDecoder().decode(base64encodedTx));
    }

    private String encodeTx(Transaction tx) {
        return Base64.getEncoder().encodeToString(tx.unsafeBitcoinSerialize());
    }

    /**
     * Signs a partially signed transaction
     * 
     * @param partialTx
     * @param indexAndPath
     * @return the fully signed transaction
     * @throws InvalidTransactionException
     */
    private Transaction signTx(Transaction tx, List<IndexAndDerivationPath> indexAndPaths) throws InvalidTransactionException {

        LOGGER.info("Signing inputs of transaction " + tx.toString());

        // Check if the transaction is an attempted double spend. We need to
        // lock here to prevent race conditions
        try {

            lock.lock();

            if (tx.isTimeLocked()) {
                // refund transaction -> save the lock time of the inputs
                long lockTime = tx.getLockTime();
                for (TransactionInput input : tx.getInputs()) {
                    signedInputs.putIfAbsent(input, lockTime);
                }
            }

            // check for attempted double-spend
            if (outputsCache.isDoubleSpend(tx)) {
                // Ha! E1337 HaxxOr detected :)
                throw new DoubleSignatureRequestedException("These Outputs have already been signed. Possible double-spend attack!");
            }
            outputsCache.cacheOutputs(tx);

            // check i fwe already signed a refund transaction of at least one
            // of the inputs that has become valid in the meantime
            for (TransactionInput input : tx.getInputs()) {
                long currentBlockHeight = serverAppKit.peerGroup().getMostCommonChainHeight();
                if (currentBlockHeight > signedInputs.getOrDefault(input, Long.MAX_VALUE)) {
                    // uh-oh. A previously signed refund transaction is already
                    // valid -> refuse signing
                    throw new DoubleSignatureRequestedException("A previously signed refund transaction has become valid.");
                }
            }

        } finally {
            lock.unlock();
        }

        // let the magic happen: Add the missing signature to the inputs
        for (IndexAndDerivationPath indexAndPath : indexAndPaths) {

            // Create the second signature for this input
            TransactionInput txIn = null;
            try {
                txIn = tx.getInputs().get(indexAndPath.getIndex());
            } catch (IndexOutOfBoundsException e) {
                throw new InvalidTransactionException("Tried to access input at index " + indexAndPath.getIndex() + " but there are only " + tx.getInputs().size() + " inputs");
            }

            Script inputScript = txIn.getScriptSig();

            if (inputScript == null) {
                throw new InvalidTransactionException("No script sig found for input " + txIn.toString());
            }

            // now we need to extract the redeem script from the complete input
            // script. The redeem script is
            // always the last script chunk of the input script
            Script redeemScript = new Script(inputScript.getChunks().get(inputScript.getChunks().size() - 1).data);

            // now let's create the transaction signature
            ImmutableList<ChildNumber> keyPath = ImmutableList.copyOf(getChildNumbers(indexAndPath.getDerivationPath()));
            DeterministicKey key = serverAppKit.wallet().getActiveKeychain().getKeyByPath(keyPath, true);
            Sha256Hash sighash = tx.hashForSignature(indexAndPath.getIndex(), redeemScript, Transaction.SigHash.ALL, false);
            ECDSASignature sig = key.sign(sighash);
            TransactionSignature txSig = new TransactionSignature(sig, Transaction.SigHash.ALL, false);

            int sigIndex = inputScript.getSigInsertionIndex(sighash, key.dropPrivateBytes().dropParent());

            // I believe that is a bug in bitcoinj. Inserting a signature in a
            // partially signed input script is only possible if the
            // getScriptSigWithSignature(...) method is called on a P2SH script
            Script dummyP2SHScript = P2SHScript.dummy();

            inputScript = dummyP2SHScript.getScriptSigWithSignature(inputScript, txSig.encodeToBitcoin(), sigIndex);

            txIn.setScriptSig(inputScript);
        }

        LOGGER.info("Signed transaction: " + tx);
        String hex = DatatypeConverter.printHexBinary(tx.unsafeBitcoinSerialize());
        LOGGER.debug("Hex encoded tx: " + hex);

        return tx;

    }

    private List<ChildNumber> getChildNumbers(int[] path) {

        List<ChildNumber> childNumbers = Lists.newArrayListWithCapacity(path.length);
        for (int i : path) {
            childNumbers.add(new ChildNumber(i));
        }

        return childNumbers;
    }

}
