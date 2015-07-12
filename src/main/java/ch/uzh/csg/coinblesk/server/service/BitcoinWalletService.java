package ch.uzh.csg.coinblesk.server.service;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.xml.bind.DatatypeConverter;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.bitcoinj.core.AbstractBlockChainListener;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey.ECDSASignature;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.ProtocolException;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.StoredBlock;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutPoint;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.core.VerificationException;
import org.bitcoinj.crypto.ChildNumber;
import org.bitcoinj.crypto.DeterministicKey;
import org.bitcoinj.crypto.TransactionSignature;
import org.bitcoinj.kits.WalletAppKit;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.params.RegTestParams;
import org.bitcoinj.params.TestNet3Params;
import org.bitcoinj.params.UnitTestParams;
import org.bitcoinj.script.Script;
import org.bitcoinj.wallet.CoinSelection;
import org.bitcoinj.wallet.CoinSelector;
import org.bitcoinj.wallet.DeterministicKeyChain;
import org.bitcoinj.wallet.DeterministicSeed;
import org.bitcoinj.wallet.KeyChainGroup;
import org.bitcoinj.wallet.MarriedKeyChain;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import ch.uzh.csg.coinblesk.bitcoin.BitcoinNet;
import ch.uzh.csg.coinblesk.responseobject.IndexAndDerivationPath;
import ch.uzh.csg.coinblesk.server.bitcoin.DoubleSignatureRequestedException;
import ch.uzh.csg.coinblesk.server.bitcoin.DummyPeerDiscovery;
import ch.uzh.csg.coinblesk.server.bitcoin.InvalidTransactionException;
import ch.uzh.csg.coinblesk.server.bitcoin.NotEnoughUnspentsException;
import ch.uzh.csg.coinblesk.server.bitcoin.P2SHScript;
import ch.uzh.csg.coinblesk.server.bitcoin.SpentOutputsCache;
import ch.uzh.csg.coinblesk.server.bitcoin.ValidRefundTransactionException;
import ch.uzh.csg.coinblesk.server.clientinterface.IBitcoinWallet;
import ch.uzh.csg.coinblesk.server.dao.SignedInputDAO;

import com.google.common.base.Preconditions;
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

    @Value("${bitcoin.net}")
    private String bitcoinNetProp;
    
    @Value("${bitcoin.wallet.dir}")
    Resource walletDir;
    
    private BitcoinNet bitcoinNet;
    
    private boolean cleanWallet = false;

    private ReentrantLock lock;

    private WalletAppKit serverAppKit;
    private DeterministicKeyChain privateKeyChain;

    private SpentOutputsCache outputsCache;
    private long currentBlockHeight;

    @Autowired
    private SignedInputDAO signedInputDao;

    public BitcoinWalletService() {
        this.outputsCache = new SpentOutputsCache();
        this.lock = new ReentrantLock();
    }

    @PostConstruct
    public void init() {

        if (serverAppKit != null && serverAppKit.isRunning()) {
            return;
        }

        if (cleanWallet) {
            clearWalletFiles();
        }

        start().awaitRunning();

        initBlockListener();
        initPrivateKeyChain();

        System.out.println("started");
    }

    private void initPrivateKeyChain() {
        String filename = getWalletPrefix(bitcoinNet) + ".mnemonic";
        String absFilename = getWalletDir().getAbsolutePath() + System.getProperty("file.separator") + filename;
        File keyFile = new File(absFilename);

        try {
            if (keyFile.exists()) {
                LOGGER.info("Loading private seed from file");
                // load existing key
                String mnemonicAndCreationDate;

                mnemonicAndCreationDate = Files.readAllLines(keyFile.toPath()).get(0);

                String mnemonic = mnemonicAndCreationDate.split(":")[0];
                long creationDate = Long.parseLong(mnemonicAndCreationDate.split(":")[1]);
                List<String> mnemonicWords = new ArrayList<String>(Arrays.asList(mnemonic.split(" ")));

                DeterministicSeed seed = new DeterministicSeed(mnemonicWords, null, "", creationDate);
                privateKeyChain = new KeyChainGroup(getNetworkParams(bitcoinNet), seed).getActiveKeyChain();
            } else {
                LOGGER.info("Writing private seed to file");

                // write the seed in the key file
                privateKeyChain = serverAppKit.wallet().getActiveKeychain();
                String mnemonic = StringUtils.join(privateKeyChain.getMnemonicCode(), " ");
                mnemonic += ":" + privateKeyChain.getEarliestKeyCreationTime();
                FileWriter fw = new FileWriter(keyFile);
                BufferedWriter bw = new BufferedWriter(fw);
                bw.write(mnemonic);
                bw.close();
            }
        } catch (IOException e) {
            LOGGER.fatal(e.getMessage());
            throw new RuntimeException(e);
        }
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
        
        bitcoinNet = BitcoinNet.of(bitcoinNetProp);

        // set up the wallet
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

    private File getWalletDir() {
        Preconditions.checkNotNull(walletDir, "Bitcoin wallet directory must be set in the properties file");
        try {
            return walletDir.getFile();
        } catch (IOException e) {
            throw new RuntimeException(e);
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

        File walletDir = getWalletDir();

        for (File f : walletDir.listFiles()) {
            if (f.getName().startsWith(getWalletPrefix(bitcoinNet))) {

                // delete existing regtest wallet and blockstore files
                if (bitcoinNet == BitcoinNet.REGTEST || bitcoinNet == BitcoinNet.UNITTEST) {
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
        File[] walletFiles = getWalletDir().listFiles(new FilenameFilter() {
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

        // preconditions
        if (tx.isTimeLocked()) {
            String errorMsg = "Tried to create a refund transaction but transaction was not time locked";
            LOGGER.warn(errorMsg);
            throw new InvalidTransactionException("Tried to broadcast a time-locked transaction");
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
    public void addWatchingKey(String base58encodedWatchingKey) {
        DeterministicKey clientKey = DeterministicKey.deserializeB58(base58encodedWatchingKey, getNetworkParams(bitcoinNet));

        // create married key chain with clients watching key
        MarriedKeyChain marriedClientKeyChain = MarriedKeyChain.builder().seed(privateKeyChain.getSeed()).followingKeys(clientKey).threshold(2).build();

        serverAppKit.wallet().addAndActivateHDChain(marriedClientKeyChain);
        serverAppKit.wallet().getActiveKeychain().setLookaheadSize(50);

        // no idea why this is necessary, but it is...
        serverAppKit.wallet().freshReceiveAddress();
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

        Transaction signedTx = null;
        try {
            for (TransactionInput input : tx.getInputs()) {
                byte[] txHash = input.getOutpoint().getHash().getBytes();
                long oupointIndex = input.getOutpoint().getIndex();
                signedInputDao.addSignedInput(txHash, oupointIndex, tx.getLockTime());
            }
            signedTx = signTx(tx, indexAndPath);
        } catch (Exception e) {
            // TODO: roll back
            // rethrow
            throw e;
        }

        return encodeTx(signedTx);
    }

    private Transaction decodeTx(String base64encodedTx) throws ProtocolException, IllegalArgumentException {
        return new Transaction(getNetworkParams(bitcoinNet), Base64.getDecoder().decode(base64encodedTx));
    }

    private String encodeTx(Transaction tx) {
        return Base64.getEncoder().encodeToString(tx.unsafeBitcoinSerialize());
    }

    private boolean inputsUnspent(final Transaction tx) {
        Coin unspentsValue = serverAppKit.wallet().getBalance(new CoinSelector() {
            @Override
            public CoinSelection select(Coin target, List<TransactionOutput> candidates) {
                Map<TransactionOutPoint, TransactionOutput> outPoint = new HashMap<>();

                List<TransactionOutput> gathered = new LinkedList<>();
                Coin valueGathered = Coin.ZERO;

                for (TransactionOutput unspent : candidates) {
                    outPoint.put(unspent.getOutPointFor(), unspent);
                    System.out.println(unspent.getOutPointFor());
                }

                for (TransactionInput txIn : tx.getInputs()) {
                    System.out.println(txIn.getOutpoint());
                    if (outPoint.containsKey(txIn.getOutpoint())) {
                        TransactionOutput unspentOutput = outPoint.get(txIn.getOutpoint());
                        gathered.add(unspentOutput);
                        System.out.println(unspentOutput.getValue());
                        valueGathered = valueGathered.add(unspentOutput.getValue());
                    }
                }

                return new CoinSelection(valueGathered, gathered);
            }
        });

        Coin txValue = Coin.ZERO;
        for (TransactionOutput txOut : tx.getOutputs()) {
            txValue = txValue.add(txOut.getValue());
        }

        return unspentsValue.isGreaterThan(txValue) || unspentsValue.compareTo(txValue) == 0;
    }

    /**
     * Signs a partially signed transaction
     * 
     * @param partialTx
     * @param indexAndPath
     * @return the fully signed transaction
     * @throws InvalidTransactionException
     */
    private Transaction signTx(final Transaction tx, List<IndexAndDerivationPath> indexAndPaths) throws InvalidTransactionException {

        if (!inputsUnspent(tx)) {
            throw new NotEnoughUnspentsException("Not enough unspent bitcoins for this transaction.");
        }

        LOGGER.info("Signing inputs of transaction " + tx.toString());

        // Check if the transaction is an attempted double spend. We need to
        // lock here to prevent race conditions
        try {

            lock.lock();

            if (!tx.isTimeLocked()) {
                // check for attempted double-spend
                if (outputsCache.isDoubleSpend(tx)) {
                    // Ha! E1337 HaxxOr detected :)
                    throw new DoubleSignatureRequestedException("These Outputs have already been signed. Possible double-spend attack!");
                }
                outputsCache.cacheOutputs(tx);
            } else {

            }

            // check if we already signed a refund transaction where at least
            // one
            // of the inputs that has become valid in the meantime
            for (TransactionInput input : tx.getInputs()) {

                long refundTxValidBlock = signedInputDao.getLockTime(input.getOutpoint().getHash().getBytes(), input.getOutpoint().getIndex());

                if (currentBlockHeight >= (refundTxValidBlock - 10)) {
                    // uh-oh. A previously signed refund transaction is (or
                    // almost)
                    // valid -> refuse signing
                    throw new ValidRefundTransactionException("A previously signed refund transaction has become valid.");
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
            DeterministicKey key = privateKeyChain.getKeyByPath(keyPath, true);
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
            // txIn.disconnect();
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

    private void initBlockListener() {

        // initial block height
        setCurrentBlockHeight(serverAppKit.peerGroup().getMostCommonChainHeight());

        // blockchain height updates
        serverAppKit.chain().addListener(new AbstractBlockChainListener() {
            @Override
            public void notifyNewBestBlock(StoredBlock block) throws VerificationException {
                // set the new best height
                setCurrentBlockHeight(block.getHeight());
            }
        });
    }

    /**
     * Sets the current block height. This method noramlly shouldn't be used,
     * the current blockchain is automatically adjusted.
     * 
     * @param blockHeigh
     */
    public void setCurrentBlockHeight(long blockHeigh) {
        this.currentBlockHeight = blockHeigh;
    }

    public WalletAppKit getAppKit() {
        return serverAppKit;
    }

    public void stop() {
        serverAppKit.stopAsync().awaitTerminated();
    }

}
