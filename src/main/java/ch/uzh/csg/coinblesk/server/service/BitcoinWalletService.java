package ch.uzh.csg.coinblesk.server.service;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Base64;
import java.util.List;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.xml.bind.DatatypeConverter;

import org.apache.log4j.Logger;
import org.bitcoinj.core.Coin;
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
import org.bitcoinj.script.Script;
import org.springframework.stereotype.Service;

import ch.uzh.csg.coinblesk.bitcoin.BitcoinNet;
import ch.uzh.csg.coinblesk.responseobject.IndexAndDerivationPath;
import ch.uzh.csg.coinblesk.server.bitcoin.InvalidTransactionException;
import ch.uzh.csg.coinblesk.server.bitcoin.P2SHScript;
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

    private WalletAppKit serverAppKit;

    @PostConstruct
    private void init() {
        start().awaitRunning();
    }

    /**
     * starts the wallet service
     * 
     * @return a {@link com.google.common.util.concurrent.Service} object
     */
    public com.google.common.util.concurrent.Service start() {

        // set up the wallet

        if (bitcoinNet == null) {
            bitcoinNet = BitcoinNet.of(ServerProperties.getProperty("bitcoin.net"));
        }

        serverAppKit = getWalletAppKit();

        // check if the app kit has already been started
        try {
            if (serverAppKit.isChainFileLocked()) {
                return serverAppKit;
            }
        } catch (Throwable e) {
            LOGGER.warn("bitcoin wallet service was initialized twice!");
            return serverAppKit;
        }

        // configure app kit
        if (bitcoinNet == BitcoinNet.REGTEST) {
            // regtest only works with local bitcoind
            serverAppKit.connectToLocalHost();
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
        case REGTEST:
            return RegTestParams.get();
        case TESTNET:
            return TestNet3Params.get();
        case MAINNET:
            return MainNetParams.get();
        default:
            throw new RuntimeException("Please set the server property bitcoin.net to (regtest|testnet|main)");
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

        boolean newWallet = true;

        for (File f : walletDir.listFiles()) {
            if (f.getName().startsWith(getWalletPrefix(bitcoinNet))) {

                newWallet = false;

                // delete existing regtest wallet and blockstore files
                if (bitcoinNet == BitcoinNet.REGTEST) {
                    f.delete();
                    LOGGER.debug("Deleted file " + f.getName());
                }
            }
        }

        if (newWallet) {
            setupAccounts();
        }

        return new WalletAppKit(getNetworkParams(bitcoinNet), walletDir, getWalletPrefix(bitcoinNet));
    }

    /**
     * Returns the wallet prefix for a given {@link BitcoinNet}
     * 
     * @param bitcoinNet
     * @return
     */
    private String getWalletPrefix(BitcoinNet bitcoinNet) {
        return bitcoinNet.toString().toLowerCase() + WALLET_PREFIX;
    }

    private void setupAccounts() {
        LOGGER.debug("New wallet, setting up accounts...");
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

    @Override
    public String sendCoins(String address, BigDecimal amount) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public boolean validateAddress(String address) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public boolean offlineValidateAddress(String address) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public String getNewAddress() {
        return serverAppKit.wallet().currentReceiveAddress().toString();
    }

    @Override
    public void listenIncomingTransactions() {
        // TODO Auto-generated method stub

    }

    @Override
    public void listenIncomingUnverifiedTransactions() {
        // TODO Auto-generated method stub

    }

    @Override
    public void listenIncomingBigTransactions() {
        // TODO Auto-generated method stub

    }

    @Override
    public void listenOutgoingTransactions() {
        // TODO Auto-generated method stub

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
    public boolean isListenTransactions() {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public void setListenTransactions(boolean listenTransactions) {
        // TODO Auto-generated method stub

    }

    @Override
    public BigDecimal getAccountBalance() {
        return BigDecimal.valueOf(serverAppKit.wallet().getBalance().getValue()).divide(BigDecimal.valueOf(Coin.SMALLEST_UNIT_EXPONENT));
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
            tx = new Transaction(getNetworkParams(bitcoinNet), Base64.getDecoder().decode(partialTx));
        } catch (ProtocolException e) {
            throw new InvalidTransactionException("Transaction could not be parsed");
        }
        
        LOGGER.info("Signing inputs of transaction " + tx.toString());

        // let the magic happen: Add the missing signature to the inputs
        for (IndexAndDerivationPath indexAndPath : indexAndPaths) {

            // TODO: Check if inputs were already signed!

            // Create the second signature for this input
            TransactionInput txIn = null;
            try {
                txIn = tx.getInputs().get(indexAndPath.getIndex());
            } catch(IndexOutOfBoundsException e) {
                throw new InvalidTransactionException("Tried to access input at index " + indexAndPath.getIndex() + " but there are only " + tx.getInputs().size() + " inputs");
            }
            
            Script inputScript = txIn.getScriptSig();
            
            if(inputScript == null) {
                throw new InvalidTransactionException("No script sig found for input " + txIn.toString());
            }
            
            // now we need to extract the redeem script from the complete input script. The redeem script is
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

        // transaction is fully signed now: let's broadcast it
        serverAppKit.peerGroup().broadcastTransaction(tx).broadcast().addListener(new Runnable() {
            @Override
            public void run() {
                LOGGER.info("Transaction was broadcasted");
            }
        }, MoreExecutors.sameThreadExecutor());

        return true;
    }

    private List<ChildNumber> getChildNumbers(int[] path) {

        List<ChildNumber> childNumbers = Lists.newArrayListWithCapacity(path.length);
        for (int i : path) {
            childNumbers.add(new ChildNumber(i));
        }

        return childNumbers;
    }

    @Override
    public BitcoinNet getBitcoinNet() {
        return bitcoinNet;
    }

}
