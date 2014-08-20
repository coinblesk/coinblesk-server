package ch.uzh.csg.mbps.server.util;

import java.math.BigDecimal;
import java.util.Date;

import javax.annotation.PostConstruct;

import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;

import ch.uzh.csg.mbps.server.service.PayInTransactionService;
import ch.uzh.csg.mbps.server.service.PayOutTransactionService;
import ch.uzh.csg.mbps.server.util.exceptions.UserAccountNotFoundException;

import com.azazar.bitcoin.jsonrpcclient.Bitcoin;
import com.azazar.bitcoin.jsonrpcclient.BitcoinAcceptor;
import com.azazar.bitcoin.jsonrpcclient.BitcoinException;
import com.azazar.bitcoin.jsonrpcclient.BitcoinJSONRPCClient;
import com.azazar.bitcoin.jsonrpcclient.ConfirmedPaymentListener;
import com.google.bitcoin.core.Address;
import com.google.bitcoin.core.AddressFormatException;
import com.google.bitcoin.core.NetworkParameters;

/**
 * Class for controlling Bitcoind-Client. 
 *
 */
@Controller
public class BitcoindController {
	private static Logger LOGGER = Logger.getLogger(BitcoindController.class);
	private static final Bitcoin BITCOIN = new BitcoinJSONRPCClient();
	private static int keyPoolCounter = 0;
	public static boolean TESTING = false;
	
	@Autowired
	private PayInTransactionService payInTransactionService;
	@Autowired
	private PayOutTransactionService payOutTransactionService;
	
	private boolean listenTransactions;
	
	@PostConstruct
	private void init() {
		backupWallet();
		if(isListenTransactions()) {
			//activates receivePayIn/Out Listener
			try {
				listenIncomingTransactions();
				listenOutgoingTransactions();
			} catch (BitcoinException e) {
				LOGGER.error("Bitcoind Exception: Couldn't initialize receivment of Bitcoin PayIN Transactions");
				e.printStackTrace();
			}
		}
	}

	/**
	 * Send defined amount of Bitcoins to defined address.
	 * 
	 * @param address
	 * @param amount
	 * @return String with transaction-id.
	 * @throws BitcoinException
	 */
	public static String sendCoins(String address, BigDecimal amount)throws BitcoinException {
		if(TESTING) {
			return "test-transaction-id";
		}
		try {
			BITCOIN.unlockWallet(SecurityConfig.ENCRYPTION_PASSWORD, 2);
		} catch (Exception e) {
			if(e.getMessage().contains("Error: Wallet is already unlocked.")){
				//do nothing --> wallet is already unlocked
			} else
				throw e;
		}
		keyPoolCheck();
		return BITCOIN.sendFrom(SecurityConfig.ACCOUNT, address, amount.doubleValue());
	}
	
	/**
	 * Checks if a BitcoinAddress is valid. Throws a BitcoinException if address is invalid.
	 * @param address
	 * @return boolean if address is valid or not.
	 * @throws BitcoinException
	 */
	public static boolean validateAddress(String address) throws BitcoinException{
		if(TESTING) {
			return offlineValidateAddress(address);
		} else {
			return BITCOIN.validateAddress(address).isValid();
		}
	}
	
	public static boolean offlineValidateAddress(String address)throws BitcoinException {
		try {
			Address.getParametersFromAddress(address);
			return true;
        } catch (AddressFormatException e) {
	        throw new BitcoinException(e);
        }
	}
	

	/**
	 * Creates and returns new Bitcoinaddress for assigned account.
	 * @return bitcoinaddress
	 * @throws BitcoinException
	 */
	public static String getNewAddress() throws BitcoinException {
		keyPoolCheck();
		String newAddress = BITCOIN.getNewAddress(SecurityConfig.ACCOUNT);
		return newAddress;
	}
	
	/**
	 * Starts task which continually listens for new incoming
	 * Bitcoin-transactions which are smaller than defined threshold and have
	 * minconfirmations.
	 * 
	 * @throws BitcoinException
	 */
	public void listenIncomingTransactions() throws BitcoinException {
		listenIncomingBigTransactions();
		
		ConfirmedPaymentListener smallTx = new ConfirmedPaymentListener(Config.MIN_CONFIRMATIONS_SMALL_TRANSACTIONS) {

            @Override
            public void confirmed(Bitcoin.Transaction transaction) {
            	try {
					if(transaction.category().equals("receive") && transaction.amount() <= Config.SMALL_TRANSACTION_LIMIT){
						LOGGER.info("Incoming small transaction: amount: " + transaction.amount() + ", account: " + transaction.account() + ", address: " +  transaction.address());
						payInTransactionService.create(transaction);
					}
				} catch (UserAccountNotFoundException e) {
					LOGGER.error("Couldn't allocate incoming transaction. Useraccount not found. Transaction: " + transaction.toString());
				}
            }

        };
        
        BitcoinAcceptor ba = new BitcoinAcceptor(BITCOIN, smallTx, false);
        
        Thread t = new Thread(ba);
        t.start();
    }
	
	/**
	 * Starts task which continually listens for new incoming
	 * Bitcoin-transactions which are bigger than defined threshold and have
	 * minconfirmations.
	 * 
	 * @throws BitcoinException
	 */
	public void listenIncomingBigTransactions() throws BitcoinException {
		ConfirmedPaymentListener bigTxListener = new ConfirmedPaymentListener(Config.MIN_CONFIRMATIONS_BIG_TRANSACTIONS) {

            @Override
            public void confirmed(Bitcoin.Transaction transaction) {
            	try {
					if(transaction.category().equals("receive")){
						if(transaction.amount() > Config.SMALL_TRANSACTION_LIMIT){
							LOGGER.info("Incoming big transaction: amount: " + transaction.amount() + ", account: " + transaction.account() + ", address: " +  transaction.address());
							payInTransactionService.create(transaction);
						}
					}
				} catch (UserAccountNotFoundException e) {
					LOGGER.error("Couldn't allocate incoming transaction. Useraccount not found. Transaction: " + transaction.toString());
				}
            }

        };
        
        BitcoinAcceptor ba = new BitcoinAcceptor(BITCOIN, bigTxListener, false);
        
        Thread t = new Thread(ba);
        t.start();
    }
	
	/**
	 * Starts task which continually listens for outgoing Bitcoin-transactions
	 * with more than the defined confirmations.
	 * 
	 * @throws BitcoinException
	 */
	public void listenOutgoingTransactions() throws BitcoinException {
		ConfirmedPaymentListener outgoingTxListener = new ConfirmedPaymentListener(Config.MIN_CONFIRMATIONS_SMALL_TRANSACTIONS) {

            @Override
            public void confirmed(Bitcoin.Transaction transaction) {
				 if (transaction.category().equals("send")){
						LOGGER.info("Outgoing transaction: amount: " + transaction.amount() + ", account: " + transaction.account() + ", address: " +  transaction.address());
						payOutTransactionService.check(transaction);
					}
            }

        };
		
        BitcoinAcceptor ba = new BitcoinAcceptor(BITCOIN, outgoingTxListener, true);
   
        Thread t = new Thread(ba);
        t.start();
    }

	/**
	 * Backup Bitcoin Wallet to destination defined in config file.
	 */
	public static void backupWallet() {
		try {
			Date date = new Date();
			BITCOIN.backupWallet(SecurityConfig.BACKUP_DESTINATION + "_" + date.toString());
			LOGGER.info("Successfully saved wallet backup.");
		} catch (BitcoinException e) {
			LOGGER.error("Saving wallet backup failed. ErrorMessage: " + e.getMessage());
		}
	}
	
	private static void keyPoolCheck() throws BitcoinException{
		keyPoolCounter++;
		if (keyPoolCounter % 90 == 0){
			try {
				BITCOIN.unlockWallet(SecurityConfig.ENCRYPTION_PASSWORD, 2);
			} catch (Exception e) {
				if(e.getMessage().equals("{\"result\":null,\"error\":{\"code\":-17,\"message\":\"Error: Wallet is already unlocked.\"},\"id\":\"1\"}")){
					//do nothing --> wallet is already unlocked
				} else
					throw e;
			}
			BITCOIN.keyPoolRefill();
			backupWallet();
		}
	}

	public boolean isListenTransactions() {
	    return listenTransactions;
    }

	public void setListenTransactions(boolean listenTransactions) {
	    this.listenTransactions = listenTransactions;
    }
}