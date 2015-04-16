package ch.uzh.csg.coinblesk.server.clientinterface;

import java.math.BigDecimal;
import java.util.Date;

import ch.uzh.csg.coinblesk.server.util.Config;
import ch.uzh.csg.coinblesk.server.util.ServerProperties;
import ch.uzh.csg.coinblesk.server.util.exceptions.UserAccountNotFoundException;

import com.azazar.bitcoin.jsonrpcclient.BitcoinAcceptor;
import com.azazar.bitcoin.jsonrpcclient.BitcoinException;
import com.azazar.bitcoin.jsonrpcclient.ConfirmedPaymentListener;
import com.azazar.bitcoin.jsonrpcclient.IBitcoinRPC.Transaction;
import com.google.bitcoin.core.Address;
import com.google.bitcoin.core.AddressFormatException;

public interface IBitcoind {
    
    /**
     * Send defined amount of Bitcoins to defined address.
     * 
     * @param address
     * @param amount
     * @return String with transaction-id.
     * @throws BitcoinException
     */
    String sendCoins(String address, BigDecimal amount) throws BitcoinException;
    
    /**
     * Checks if a BitcoinAddress is valid. Throws a BitcoinException if address is invalid.
     * @param address
     * @return boolean if address is valid or not.
     * @throws BitcoinException
     */
    boolean validateAddress(String address) throws BitcoinException;
    
    boolean offlineValidateAddress(String address)throws BitcoinException;
    

    /**
     * Creates and returns new Bitcoinaddress for assigned account.
     * @return bitcoinaddress
     * @throws BitcoinException
     */
    String getNewAddress() throws BitcoinException;
    
    /**
     * Starts task which continually listens for new incoming
     * Bitcoin-transactions which are smaller than defined threshold and have
     * minconfirmations.
     * 
     * @throws BitcoinException
     */
    public void listenIncomingTransactions() throws BitcoinException;
    
    public void listenIncomingUnverifiedTransactions() throws BitcoinException;
    
    /**
     * Starts task which continually listens for new incoming
     * Bitcoin-transactions which are bigger than defined threshold and have
     * minconfirmations.
     * 
     * @throws BitcoinException
     */
    public void listenIncomingBigTransactions() throws BitcoinException;
    
    /**
     * Starts task which continually listens for outgoing Bitcoin-transactions
     * with more than the defined confirmations.
     * 
     * @throws BitcoinException
     */
    public void listenOutgoingTransactions() throws BitcoinException;

    /**
     * Backup Bitcoin Wallet to destination defined in config file.
     */
    void backupWallet();
    
    boolean isListenTransactions();

    void setListenTransactions(boolean listenTransactions);

    /**
     * Returns the balance of bitcoind (currently available Bitcoins).
     * @return accountBalance (Balance of Bitcoind)
     * @throws BitcoinException
     */
    BigDecimal getAccountBalance() throws BitcoinException;

}