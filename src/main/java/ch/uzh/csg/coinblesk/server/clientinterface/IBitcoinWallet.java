package ch.uzh.csg.coinblesk.server.clientinterface;

import java.util.List;

import org.bitcoinj.crypto.DeterministicKey;

import ch.uzh.csg.coinblesk.bitcoin.BitcoinNet;
import ch.uzh.csg.coinblesk.responseobject.IndexAndDerivationPath;
import ch.uzh.csg.coinblesk.server.bitcoin.InvalidTransactionException;

public interface IBitcoinWallet {

    /**
     * This method returns a serialized watching {@link DeterministicKey} of the
     * server. It is a watch-only key, private keys of the server cannot be
     * derived from it. It is therefore save to sahre this with anyone.
     * 
     * @return the Base64 serialized watching {@link DeterministicKey} of the
     *         server.
     */
    String getSerializedServerWatchingKey();

    /**
     * This method is responsible for signing partially signed Bitcoin
     * transactions and broadcast them to the Bitcoin network. If the inputs of
     * the transaction were already signed previously, this method will return
     * false, and the transaction will not be signed/broadcastet.
     * 
     * @param partialTx
     *            the Base64 encoded partially signed transaction
     * @param indexAndPath
     *            the indices and key derivation paths of the partially signed
     *            transaction
     * @return true if the transaction is valid and was broadcastet to the
     *         Bitcoin network
     * @throws InvalidTransactionException
     *             if the partial transaction is not valid
     */
    boolean signTxAndBroadcast(String partialTx, List<IndexAndDerivationPath> indexAndPath) throws InvalidTransactionException;

    /**
     * This method is responsible for signing a partially signed, time locked
     * refund transaction. The signed transaction is not broadcasted but sent to
     * the client.
     * 
     * @param partialTimeLockedTx
     *            the partially signed, time locked transaction
     * @param indexAndPath
     *            the indices and key derivation paths of the partially signed
     *            transaction
     * @return a base64 encoded, fully signed, time-locked refund transaction.
     * @throws InvalidTransactionException
     */
    String signRefundTx(String partialTimeLockedTx, List<IndexAndDerivationPath> indexAndPath) throws InvalidTransactionException;

    /**
     * Backup Bitcoin Wallet to destination defined in config file.
     */
    void backupWallet();

    /**
     * @return The {@link BitcoinNet} the server is currently running on
     */
    BitcoinNet getBitcoinNet();

    /**
     * Sets the bitcoin network this class
     * 
     * @param bitcoinNet
     */
    void setBitcoinNet(String bitcoinNet);

    /**
     * Cleans (deletes) previously existing wallet before starting up. Only the
     * wallet of the selected {@link BitcoinNet} is cleaned, other wallets are
     * left untouched.
     * 
     * @param cleanWallet
     *            if true the wallet is cleaned before startup
     */
    void setCleanWallet(boolean cleanWallet);

}