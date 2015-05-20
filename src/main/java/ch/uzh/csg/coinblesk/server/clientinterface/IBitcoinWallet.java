package ch.uzh.csg.coinblesk.server.clientinterface;

import java.math.BigDecimal;
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
     * @return the B52 serialized watching {@link DeterministicKey} of the
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

    /*
     * ========================== LEGACY INTERFACE BELOW....
     * ==========================
     */

    /**
     * Send defined amount of Bitcoins to defined address.
     * 
     * @param address
     * @param amount
     * @return String with transaction-id.
     * @throws BitcoinException
     */
    String sendCoins(String address, BigDecimal amount);

    /**
     * Checks if a BitcoinAddress is valid. Throws a BitcoinException if address
     * is invalid.
     * 
     * @param address
     * @return boolean if address is valid or not.
     * @throws BitcoinException
     */
    boolean validateAddress(String address);

    boolean offlineValidateAddress(String address);

    /**
     * Creates and returns new Bitcoinaddress for assigned account.
     * 
     * @return bitcoinaddress
     * @throws BitcoinException
     */
    String getNewAddress();

    /**
     * Starts task which continually listens for new incoming
     * Bitcoin-transactions which are smaller than defined threshold and have
     * minconfirmations.
     * 
     * @throws BitcoinException
     */
    public void listenIncomingTransactions();

    public void listenIncomingUnverifiedTransactions();

    /**
     * Starts task which continually listens for new incoming
     * Bitcoin-transactions which are bigger than defined threshold and have
     * minconfirmations.
     * 
     * @throws BitcoinException
     */
    public void listenIncomingBigTransactions();

    /**
     * Starts task which continually listens for outgoing Bitcoin-transactions
     * with more than the defined confirmations.
     * 
     * @throws BitcoinException
     */
    public void listenOutgoingTransactions();

    /**
     * Backup Bitcoin Wallet to destination defined in config file.
     */
    void backupWallet();

    boolean isListenTransactions();

    void setListenTransactions(boolean listenTransactions);

    /**
     * Returns the balance of bitcoind (currently available Bitcoins).
     * 
     * @return accountBalance (Balance of Bitcoind)
     * @throws BitcoinException
     */
    BigDecimal getAccountBalance();
    
    /**
     * @return The {@link BitcoinNet} the server is currently running on
     */
    BitcoinNet getBitcoinNet();

}