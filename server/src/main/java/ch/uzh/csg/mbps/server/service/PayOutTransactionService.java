package ch.uzh.csg.mbps.server.service;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ch.uzh.csg.mbps.model.HistoryPayOutTransaction;
import ch.uzh.csg.mbps.responseobject.TransferObject;
import ch.uzh.csg.mbps.server.clientinterface.IUserAccount;
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
@Service
public class PayOutTransactionService {
	
	
	@Autowired 
	private PayOutTransactionDAO payOutTransactionDAO;
	@Autowired
	private IUserAccount userAccountService;

	/**
	 * Returns history of {@link PayOutTransaction}s of {@link UserAccount} with username. Only
	 * the Transactions defined by page are returned, not all {@link PayOutTransaction}s.
	 * 
	 * @param username
	 * @param page
	 * @return ArrayListy<PayOutTransaction>
	 * @throws UserAccountNotFoundException
	 */
	@Transactional(readOnly = true)
	public List<HistoryPayOutTransaction> getHistory(String username, int page) throws UserAccountNotFoundException {
		UserAccount user = userAccountService.getByUsername(username);
		return payOutTransactionDAO.getHistory(user, page);
	}
	
	/**
	 * Counts and returns number of {@link PayOutTransaction}s which are saved in the DB
	 * for {@link UserAccount} with username.
	 * 
	 * @param username
	 * @return number of PayOutTransaction
	 * @throws UserAccountNotFoundException
	 */
	@Transactional(readOnly = true)
	public long getHistoryCount(String username) throws UserAccountNotFoundException {
		UserAccount user = userAccountService.getByUsername(username);
		return payOutTransactionDAO.getHistoryCount(user);
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
	@Transactional
	public TransferObject createPayOutTransaction(String username, BigDecimal amount, String address) throws BitcoinException, UserAccountNotFoundException {
		TransferObject transferObject = new TransferObject();
		UserAccount user = userAccountService.getByUsername(username);
		PayOutTransaction pot = new PayOutTransaction();
		pot.setAmount(amount);
		pot.setBtcAddress(address);
		pot.setUserID(user.getId());
		
		BigDecimal userBalance = user.getBalance();
		
		//check if user wants to pay out complete amount
		if(userBalance.compareTo(pot.getAmount()) == 0){
			BigDecimal payOutAmount = pot.getAmount().subtract(Config.TRANSACTION_FEE);
			if (payOutAmount.compareTo(BigDecimal.ZERO) > 0){
				pot.setAmount(payOutAmount);
			} else {
				transferObject.setSuccessful(false);
				//"Couldn't pay out the desired amount. Your balance is too low."
				transferObject.setMessage("PAYOUT_ERROR_BALANCE");
				return transferObject;
			}
		}
		
		if(userBalance.compareTo(pot.getAmount().add(Config.TRANSACTION_FEE)) >= 0){
			if (BitcoindController.validateAddress(pot.getBtcAddress())) {
				pot.setTimestamp(new Date());
				//do payOut in BitcoindController
				String transactionID = BitcoindController.sendCoins(pot.getBtcAddress(), pot.getAmount());
				pot.setTransactionID(transactionID);
				
				amount = pot.getAmount();
				pot.setAmount(pot.getAmount().add(Config.TRANSACTION_FEE));

				//write payOut to DB
				payOutTransactionDAO.createPayOutTransaction(pot);
				transferObject.setSuccessful(true);
				transferObject.setMessage(amount  + "BTC " + "(+" + Config.TRANSACTION_FEE + "BTC TxFee)");
				return transferObject;
			} else {
				transferObject.setSuccessful(false);
				//"Couldn't pay out the desired amount. The BTC Address is invalid."
				transferObject.setMessage("PAYOUT_ERROR_ADDRESS");
				return transferObject;
				
			}
		} else{
			transferObject.setSuccessful(false);
			//"Couldn't pay out the desired amount. Your balance is lower than your specified PayOut amount."
			transferObject.setMessage("PAYOUT_ERROR_BALANCE");
			return transferObject;
		}
	}

	/**
	 * Checks if {@link PayOutTransaction} which has min-confirmations from the Bitcoin
	 * network is already verified. If no it set isVerified to true.
	 * 
	 * @param transaction
	 */
	@Transactional
	public void check(Transaction transaction) {
		PayOutTransaction pot = new PayOutTransaction(transaction);
		try {
			payOutTransactionDAO.verify(pot);
		} catch (TransactionException e) {
		}	
	}

	/**
	 * Returns five last {@link PayOutTransaction}s for {@link UserAccount}
	 * specified by given username.
	 * 
	 * @param username
	 * @return ArrayListy<PayOutTransaction>
	 * @throws UserAccountNotFoundException
	 */
	@Transactional(readOnly = true)
	public List<HistoryPayOutTransaction> getLast5Transactions(String username) throws UserAccountNotFoundException {
		UserAccount user = userAccountService.getByUsername(username);
		return payOutTransactionDAO.getLast5Transactions(user);
	}

	@Transactional
	public void createPayOutTransaction(PayOutTransaction tx) throws UserAccountNotFoundException {
		payOutTransactionDAO.createPayOutTransaction(tx);
	    
    }

	
	
}
