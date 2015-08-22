package ch.uzh.csg.coinblesk.server.service;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.transaction.Transactional;
import javax.xml.bind.DatatypeConverter;

import org.apache.commons.lang3.StringUtils;
import org.bitcoinj.core.AbstractBlockChainListener;
import org.bitcoinj.core.ECKey.ECDSASignature;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.ProtocolException;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.StoredBlock;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionInput.ConnectMode;
import org.bitcoinj.core.TransactionInput.ConnectionResult;
import org.bitcoinj.core.Wallet.BalanceType;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.core.VerificationException;
import org.bitcoinj.crypto.ChildNumber;
import org.bitcoinj.crypto.DeterministicKey;
import org.bitcoinj.crypto.TransactionSignature;
import org.bitcoinj.kits.WalletAppKit;
import org.bitcoinj.net.discovery.PeerDiscovery;
import org.bitcoinj.net.discovery.PeerDiscoveryException;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.params.RegTestParams;
import org.bitcoinj.params.TestNet3Params;
import org.bitcoinj.params.UnitTestParams;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;
import org.bitcoinj.wallet.DeterministicKeyChain;
import org.bitcoinj.wallet.DeterministicSeed;
import org.bitcoinj.wallet.KeyChainGroup;
import org.bitcoinj.wallet.MarriedKeyChain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.MoreExecutors;

import ch.uzh.csg.coinblesk.bitcoin.BitcoinNet;
import ch.uzh.csg.coinblesk.server.bitcoin.DoubleSignatureRequestedException;
import ch.uzh.csg.coinblesk.server.bitcoin.InvalidClientSignatureException;
import ch.uzh.csg.coinblesk.server.bitcoin.InvalidTransactionException;
import ch.uzh.csg.coinblesk.server.bitcoin.NotEnoughUnspentsException;
import ch.uzh.csg.coinblesk.server.bitcoin.ValidRefundTransactionException;
import ch.uzh.csg.coinblesk.server.config.AppConfig;
import ch.uzh.csg.coinblesk.server.dao.ClientWatchingKeyDAO;
import ch.uzh.csg.coinblesk.server.dao.SignedInputDAO;
import ch.uzh.csg.coinblesk.server.dao.SignedTransactionDAO;
import ch.uzh.csg.coinblesk.server.dao.SpentOutputDAO;

/**
 * Abstraction of bitcoinJ
 */
@Service
public class BitcoinWalletService {

    private static final Logger LOGGER = LoggerFactory.getLogger(BitcoinWalletService.class);

    @Autowired
    private AppConfig appConfig;

    @Autowired
    private SignedInputDAO signedInputDao;

    @Autowired
    private SpentOutputDAO spentOutputDao;

    @Autowired
    private ClientWatchingKeyDAO clientWatchingKeyDAO;

    @Autowired
    private SignedTransactionDAO signedTransactionDao;

    /**
     * Prefix for wallet and blockstore files. This prefix will be prefixed with
     * the name of the network, eg testnet + WALLET_PREFIX
     */
    private static final String WALLET_PREFIX = "_bitcoinj";

    private BitcoinNet bitcoinNet;

    private boolean cleanWallet = false;

    private WalletAppKit serverAppKit;
    private DeterministicKeyChain privateKeyChain;

    private long currentBlockHeight;

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

        LOGGER.info("bitcoin wallet service is ready");
        LOGGER.info("Total number of bitcoins in the CoinBlesk system: {}", serverAppKit.wallet().getBalance(BalanceType.ESTIMATED).toFriendlyString());

        if (bitcoinNet == BitcoinNet.MAINNET && appConfig.getMinConf() < 4) {
            LOGGER.warn("Client transactions are confirmed with {} confirmations. It is adviced to change the minimum number of confirmations to at least 4",
                    appConfig.getMinConf());
        }
    }

    // used for testing
    SpentOutputDAO getSpentOutputDao() {
        return spentOutputDao;
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
            LOGGER.error(e.getMessage());
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

        bitcoinNet = BitcoinNet.of(appConfig.getBitcoinNet());

        // set up the wallet
        serverAppKit = getWalletAppKit();
        serverAppKit.setBlockingStartup(false);

        // configure app kit
        if (bitcoinNet == BitcoinNet.REGTEST) {
            // regtest only works with local bitcoind
            serverAppKit.connectToLocalHost();
        } else if (bitcoinNet == BitcoinNet.UNITTEST) {
            // set dummy discovery, should be fixed in latest bitcoinj
            serverAppKit.setDiscovery(new PeerDiscovery() {
                @Override
                public void shutdown() {
                }

                @Override
                public InetSocketAddress[] getPeers(long timeoutValue, TimeUnit timeoutUnit) throws PeerDiscoveryException {
                    return new InetSocketAddress[0];
                }
            });
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
        Preconditions.checkNotNull(appConfig.getConfigDir(), "Bitcoin wallet directory must be set in the properties file");
        return appConfig.getConfigDir().getFile();
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

    /**
     * Sets the bitcoin network this class
     * 
     * @param bitcoinNet
     */
    public void setBitcoinNet(String bitcoinNet) {
        this.bitcoinNet = BitcoinNet.of(bitcoinNet);
    }

    /**
     * @return The {@link BitcoinNet} the server is currently running on
     */
    public BitcoinNet getBitcoinNet() {
        return bitcoinNet;
    }

    /**
     * Cleans (deletes) previously existing wallet before starting up. Only the
     * wallet of the selected {@link BitcoinNet} is cleaned, other wallets are
     * left untouched.
     * 
     * @param cleanWallet
     *            if true the wallet is cleaned before startup
     */
    public void setCleanWallet(boolean cleanWallet) {
        this.cleanWallet = cleanWallet;
    }

    /**
     * This method returns a serialized watching {@link DeterministicKey} of the
     * server. It is a watch-only key, private keys of the server cannot be
     * derived from it. It is therefore save to sahre this with anyone.
     * 
     * @return the Base64 serialized watching {@link DeterministicKey} of the
     *         server.
     */
    public String getSerializedServerWatchingKey() {
        return serverAppKit.wallet().getWatchingKey().serializePubB58(getNetworkParams(bitcoinNet));
    }

    /**
     * This method is responsible for signing partially signed Bitcoin
     * transactions and broadcast them to the Bitcoin network. If the inputs of
     * the transaction were already signed previously, a
     * {@link DoubleSignatureRequestedException} will be thrown, and the
     * transaction is not broadcasted.
     * 
     * @param partialTx
     *            the Base64 encoded partially signed transaction
     * @param indexAndPath
     *            the indices and key derivation paths of the partially signed
     *            transaction
     * @return The fully signed bitcoin transaction, Base64 encoded.
     * @throws InvalidTransactionException
     *             if the partial transaction is not valid
     */
    @Transactional
    public String signAndBroadcastTx(String partialTx, List<Integer> childNumbers) throws InvalidTransactionException {

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
            String errorMsg = "Tried to sign and broadcast a time-locked transaction.";
            LOGGER.warn(errorMsg);
            throw new InvalidTransactionException(errorMsg);
        }

        Transaction signedTx = completeTx(tx, childNumbers);

        // transaction is fully signed now: let's broadcast it
        serverAppKit.peerGroup().broadcastTransaction(signedTx).broadcast().addListener(new Runnable() {
            @Override
            public void run() {
                LOGGER.info("Transaction {} was broadcasted", signedTx.getHashAsString());
            }
        }, MoreExecutors.sameThreadExecutor());

        signedTransactionDao.addSignedTransaction(signedTx);
        serverAppKit.wallet().maybeCommitTx(signedTx);
        LOGGER.debug("Saved transaction {} in database and commited to wallet", signedTx.getHashAsString());

        return Base64.getEncoder().encodeToString(signedTx.unsafeBitcoinSerialize());
    }

    /**
     * Adds a clients watching key to the server's watching wallet. This means
     * that the server is always up to date about the funds available to the
     * clients, and can therefore know whether the transaction to sign are in
     * fact unspent. If the client's watching key was added to the server
     * before, this method will do nothing.
     * 
     * @param base58encodedWatchingKey
     *            the client's base58 encoded watching key
     */
    @Transactional
    public void addWatchingKey(String base58encodedWatchingKey) {

        if (clientWatchingKeyDAO.exists(base58encodedWatchingKey)) {
            return;
        }

        DeterministicKey clientKey = DeterministicKey.deserializeB58(base58encodedWatchingKey, getNetworkParams(bitcoinNet));

        // create married key chain with clients watching key
        MarriedKeyChain marriedClientKeyChain = MarriedKeyChain.builder().seed(privateKeyChain.getSeed()).followingKeys(clientKey).threshold(2).build();

        serverAppKit.wallet().addAndActivateHDChain(marriedClientKeyChain);
        serverAppKit.wallet().getActiveKeychain().setLookaheadSize(100);

        // no idea why this is necessary, but it is...
        serverAppKit.wallet().freshReceiveAddress();

        clientWatchingKeyDAO.addClientWatchingKey(base58encodedWatchingKey);
    }

    /**
     * This method is responsible for signing a partially signed, time locked
     * refund transaction. The signed transaction is not broadcasted but sent to
     * the client.
     * 
     * This method only accepts time locked transactions. If a non-time-locked
     * transaction is passed, {@link InvalidTransactionException} will be
     * thrown.
     * 
     * @param partialTimeLockedTx
     *            the partially signed, time locked transaction
     * @param childNumbers
     *            the indices and key derivation paths of the partially signed
     *            transaction
     * @return a base64 encoded, fully signed, time-locked refund transaction.
     * @throws InvalidTransactionException
     */
    @Transactional
    public String signRefundTx(String partialTimeLockedTx, List<Integer> childNumbers) throws InvalidTransactionException {

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
            signedTx = completeTx(tx, childNumbers);
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

        // if this transaction is time-locked, we don't really care if the
        // inputs are unspent.
        if (tx.isTimeLocked()) {
            return true;
        }

        // if the inputs are instant transactions that were signed by the
        // server, we know for sure that the inputs are unspent
        if (signedTransactionDao.allInputsServerSigned(tx)) {
            LOGGER.debug("All inputs are from instant transactions");
            return true;
        } else {
            // only for debugging
            if (bitcoinNet != BitcoinNet.MAINNET) {
                for (TransactionInput txIn : tx.getInputs()) {
                    if (txIn.getConnectedOutput() != null && signedTransactionDao.isInstantTransaction(txIn.getConnectedOutput().getHash().toString())) {
                        LOGGER.debug("Input transaction {} was signed by the server", txIn.getOutpoint().getHash().toString());
                    } else {
                        LOGGER.debug("Input transaction {} was NOT signed by the server", txIn.getOutpoint().getHash().toString());
                    }
                }
            }
        }

        // at this point we need to check if the inputs are unspent and have
        // enough confirmations
        for (TransactionInput txIn : tx.getInputs()) {
            Transaction inputTx = serverAppKit.wallet().getTransaction(txIn.getOutpoint().getHash());

            if (inputTx == null) {
                LOGGER.warn("Client tried to spend the output {} we have never seen", txIn.getOutpoint());
                return false;
            }

            // check if transaction was already spent
            if (txIn.connect(inputTx, ConnectMode.DISCONNECT_ON_CONFLICT) == ConnectionResult.ALREADY_SPENT) {
                LOGGER.warn("Client tried to spend output {}, but the output is already spent!", txIn.getOutpoint());
                return false;
            }

            // check if transaction has enough confirmations
            if (inputTx.getConfidence().getDepthInBlocks() < appConfig.getMinConf()) {
                LOGGER.debug("Transaction {} has not enough confirmations. Required: {}. Number of confirmations: {} ({})", inputTx.getHashAsString(), appConfig.getMinConf(),
                        inputTx.getConfidence().getDepthInBlocks(), inputTx.getConfidence());
                return false;
            }

            LOGGER.debug("Client is spending output {}, which has enough confirmations and is unspent", txIn.getOutpoint());

        }

        return true;
    }

    /**
     * Signs a partially signed transaction
     * 
     * @param partialTx
     * @param indexAndPath
     * @return the fully signed transaction
     * @throws InvalidTransactionException
     */

    private Transaction completeTx(final Transaction tx, List<Integer> childNumbers) throws InvalidTransactionException {
        
        LOGGER.debug("Received request to sign transaction:\n{}", tx);

        if (!inputsUnspent(tx)) {
            throw new NotEnoughUnspentsException("Not enough unspent bitcoins for this transaction.");
        }

        LOGGER.info("Signing inputs of transaction {}", tx.getHashAsString());

        // check for double spend
        if (!tx.isTimeLocked() && spentOutputDao.isDoubleSpend(tx)) {
            // Ha!
            LOGGER.warn("Client tried to sign an input that has already been signed before");
            throw new DoubleSignatureRequestedException("These Outputs have already been signed. Possible double-spend attack!");
        }

        // check if we already signed a refund transaction where at least one of
        // the inputs that has become valid in the meantime
        for (TransactionInput input : tx.getInputs()) {

            long refundTxValidBlock = signedInputDao.getLockTime(input.getOutpoint().getHash().getBytes(), input.getOutpoint().getIndex());

            if (currentBlockHeight >= (refundTxValidBlock - 10)) {
                // uh-oh. A previously signed refund transaction is (or almost)
                // valid -> refuse signing
                throw new ValidRefundTransactionException("A previously signed refund transaction has become valid.");
            }
        }

        // let the magic happen: Add the missing signature to the inputs
        for (int i = 0; i < tx.getInputs().size(); i++) {

            // Create the second signature for this input
            TransactionInput txIn = tx.getInputs().get(i);

            Script inputScript = txIn.getScriptSig();

            if (inputScript == null) {
                throw new InvalidTransactionException("No script sig found for input " + txIn.toString());
            }

            // now we need to extract the redeem script from the complete input
            // script. The redeem script is
            // always the last script chunk of the input script
            Script redeemScript = new Script(inputScript.getChunks().get(inputScript.getChunks().size() - 1).data);

            // now let's create the transaction signature
            List<ChildNumber> keyPath = childNumberToPath(childNumbers.get(i));
            DeterministicKey key = privateKeyChain.getKeyByPath(keyPath, true);
            Sha256Hash sighash = tx.hashForSignature(i, redeemScript, Transaction.SigHash.ALL, false);
            ECDSASignature sig = key.sign(sighash);

            TransactionSignature txSig = new TransactionSignature(sig, Transaction.SigHash.ALL, false);

            try {
                int sigIndex = inputScript.getSigInsertionIndex(sighash, key.dropPrivateBytes().dropParent());

                // Inserting a signature in a partially signed input script is
                // only possible if the getScriptSigWithSignature(...) method is
                // called on a P2SH script
                Script dummyP2SHScript = P2SHScript.dummy();

                inputScript = dummyP2SHScript.getScriptSigWithSignature(inputScript, txSig.encodeToBitcoin(), sigIndex);

                txIn.setScriptSig(inputScript);

                TransactionOutput out = serverAppKit.wallet().getTransaction(txIn.getOutpoint().getHash()).getOutput(txIn.getOutpoint().getIndex());
                txIn.getScriptSig().correctlySpends(txIn.getParentTransaction(), i, out.getScriptPubKey());
            } catch (Exception e) {
                LOGGER.error("Failed to sign transaction", e);
                throw new InvalidClientSignatureException(e.getMessage());
            }

        }

        LOGGER.info("Signed transaction " + tx.getHashAsString());
        String hex = DatatypeConverter.printHexBinary(tx.unsafeBitcoinSerialize());
        LOGGER.debug("Hex encoded tx: " + hex);

        if (!tx.isTimeLocked()) {
            spentOutputDao.addOutput(tx);
        }

        return tx;

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

    public static class P2SHScript extends Script {

        public P2SHScript(byte[] programBytes) {
            super(programBytes);
        }

        @Override
        public boolean isPayToScriptHash() {
            return true;
        }

        public static P2SHScript dummy() {
            Script dummyScript = new ScriptBuilder().build();
            return new P2SHScript(dummyScript.getProgram());
        }

    }

    /**
     * Converts a child number to a path
     * 
     * @return
     */
    private List<ChildNumber> childNumberToPath(int i) {
        return Lists.newArrayList(new ChildNumber(0, true), new ChildNumber(0), new ChildNumber(i));
    }

}
