package ch.uzh.csg.mbps.server.service;

import java.math.BigDecimal;
import java.util.ArrayList;

import ch.uzh.csg.mbps.model.HistoryPayOutTransaction;
import ch.uzh.csg.mbps.responseobject.CustomResponseObject;
import ch.uzh.csg.mbps.server.dao.PayOutTransactionDAO;
import ch.uzh.csg.mbps.server.domain.PayOutTransaction;
import ch.uzh.csg.mbps.server.domain.UserAccount;
import ch.uzh.csg.mbps.server.util.BitcoindController;
import ch.uzh.csg.mbps.server.util.Config;
import ch.uzh.csg.mbps.server.util.exceptions.TransactionException;
import ch.uzh.csg.mbps.server.util.exceptions.UserAccountNotFoundException;

import com.azazar.bitcoin.jsonrpcclient.Bitcoin.Transaction;
import com.azazar.bitcoin.jsonrpcclient.BitcoinException;

/**
 * Service class for {@link PayOutTransaction}s
 *
 */
public class PayOutTransactionService {
	private static PayOutTransactionService payOutTransactionService;

	private PayOutTransactionService() {
	}
	
	/**
	 * Returns new or existing instance of {@link PayOutTransactionService}.
	 * 
	 * @return instance of PayOutTransactionService
	 */
	public static PayOutTransactionService getInstance() {
		if (payOutTransactionService == null)
			payOutTransactionService = new PayOutTransactionService();
		
		return payOutTransactionService;
	}

	/**
	 * Returns history of {@link PayOutTransaction}s of {@link UserAccount} with username. Only
	 * the Transactions defined by page are returned, not all {@link PayOutTransaction}s.
	 * 
	 * @param username
	 * @param page
	 * @return ArrayListy<PayOutTransaction>
	 * @throws UserAccountNotFoundException
	 */
	public ArrayList<HistoryPayOutTransaction> getHistory(String username, int page) throws UserAccountNotFoundException {
		return PayOutTransactionDAO.getHistory(username, page);
	}
	
	/**
	 * Counts and returns number of {@link PayOutTransaction}s which are saved in the DB
	 * for {@link UserAccount} with username.
	 * 
	 * @param username
	 * @return number of PayOutTransaction
	 * @throws UserAccountNotFoundException
	 */
	public long getHistoryCount(String username) throws UserAccountNotFoundException {
		return PayOutTransactionDAO.getHistoryCount(username);
	}

	/**
	 * Creates a new {@link PayOutTransaction} for {@link UserAccount} with username.
	 * 
	 * @param username
	 * @param pot PayOutTransaction
	 * @return CustomResponseObject with information about success/non success of creation and notification message.
	 * @throws BitcoinException
	 * @throws UserAccountNotFoundException
	 */
	public CustomResponseObject createPayOutTransaction(String username, PayOutTransaction pot) throws BitcoinException, UserAccountNotFoundException {
		UserAccount user = UserAccountService.getInstance().getByUsername(username);
		//make sure pot.id == user.id
		pot.setUserID(user.getId());
		
		if(user.getBalance().compareTo(pot.getAmount().add(Config.TRANSACTION_FEE)) >= 0){
			if (BitcoindController.validateAddress(pot.getBtcAddress())) {
				//do payOut in BitcoindController
				String transactionID = BitcoindController.sendCoins(pot.getBtcAddress(), pot.getAmount());
				pot.setTransactionID(transactionID);
				
				BigDecimal amount = pot.getAmount();
				pot.setAmount(pot.getAmount().add(Config.TRANSACTION_FEE));

				//write payOut to DB
				PayOutTransactionDAO.createPayOutTransaction(pot);
				return new CustomResponseObject(true, "Your PayOut Transaction of " + amount  + "BTC " + "(+" + Config.TRANSACTION_FEE + "BTC TxFee)" + " was successfully sent to the Bitcoin Network.");
			} else {
				return new CustomResponseObject(false, "Couldn't pay out the desired amount. The BTC Address is invalid.");	
			}
		} else{
			return new CustomResponseObject(false, "Couldn't pay out the desired amount. Your balance is lower than your specified PayOut amount.");
		}
	}

	/**
	 * Checks if {@link PayOutTransaction} which has min-confirmations from the Bitcoin
	 * network is already verified. If no it set isVerified to true.
	 * 
	 * @param transaction
	 */
	public static void check(Transaction transaction) {
		PayOutTransaction pot = new PayOutTransaction(transaction);
		try {
			PayOutTransactionDAO.verify(pot);
		} catch (TransactionException e) {
		}	
	}
	
}
