package ch.uzh.csg.mbps.server.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ch.uzh.csg.mbps.model.HistoryPayInTransaction;
import ch.uzh.csg.mbps.responseobject.TransferObject;
import ch.uzh.csg.mbps.server.dao.PayInTransactionDAO;
import ch.uzh.csg.mbps.server.dao.UserAccountDAO;
import ch.uzh.csg.mbps.server.domain.PayInTransaction;
import ch.uzh.csg.mbps.server.domain.UserAccount;
import ch.uzh.csg.mbps.server.util.Emailer;
import ch.uzh.csg.mbps.server.util.exceptions.UserAccountNotFoundException;

import com.azazar.bitcoin.jsonrpcclient.Bitcoin.Transaction;

/**
 * Service class for {@link PayInTransaction}.
 * 
 */
@Service
public class PayInTransactionService {
	@Autowired
	private PayInTransactionDAO payInTransactionDAO;
	@Autowired
	private UserAccountDAO userAccountDAO;

	/**
	 * Creates a new {@link PayInTransaction} for {@link UserAccount} with BTC Address defined
	 * in transaction.
	 * 
	 * @param transaction
	 * @throws UserAccountNotFoundException
	 */
	@Transactional
	public void create(Transaction transaction)
			throws UserAccountNotFoundException {
		long userID = userAccountDAO.getByBTCAddress(transaction.address()).getId();
		PayInTransaction pit = new PayInTransaction(userID, transaction);
		if (payInTransactionDAO.isNew(pit)) {
			payInTransactionDAO.createPayInTransaction(pit);
		}
	}

	/**
	 * Returns history of {@link PayInTransaction} of UserAccount with username. Only
	 * the Transactions defined by page are returned, not all transactions.
	 * 
	 * @param username
	 * @param page defining which PayInTransactions shall be returned
	 * @return ArrayList of HistoryPayInTransactions
	 * @throws UserAccountNotFoundException
	 */
	@Transactional(readOnly = true)
	public List<HistoryPayInTransaction> getHistory(String username,
			int page) throws UserAccountNotFoundException {
		return payInTransactionDAO.getHistory(username, page);
	}

	/**
	 * Counts and returns number of {@link PayInTransaction}s which are saved in the DB
	 * for {@link UserAccount} with username.
	 * 
	 * @param username of UserAccount
	 * @return number of PayInTrasactions
	 * @throws UserAccountNotFoundException
	 */
	@Transactional(readOnly = true)
	public long getHistoryCount(String username)
			throws UserAccountNotFoundException {
		UserAccount userAccount = userAccountDAO.getByUsername(username);
		return payInTransactionDAO.getHistoryCount(userAccount);
	}

	/**
	 * Returns 5 newest {@link PayInTransaction}s for specified username in descending order.
	 * 
	 * @param username
	 * @return ArrayList<{@link HistoryPayInTransaction>
	 * @throws UserAccountNotFoundException
	 */
	@Transactional(readOnly = true)
	public List<HistoryPayInTransaction> getLast5Transactions(String username) throws UserAccountNotFoundException {
		UserAccount userAccount = userAccountDAO.getByUsername(username);
		return payInTransactionDAO.getLast5Transactions(userAccount);
	}
	
	//TODO: does this need to be here?
	public TransferObject sendPayInAddressByEmail(String username, String email, String payInAddress) {	
		Emailer.sendPayInAddressAsEmail(username, email, payInAddress);
		TransferObject transferObject = new TransferObject();
		transferObject.setSuccessful(true);
		transferObject.setMessage("Pay in address is send to your email address.");
		return transferObject;
	}
	
	@Transactional
	public void createPayInTransaction(PayInTransaction tx) throws UserAccountNotFoundException {
	    payInTransactionDAO.createPayInTransaction(tx);
	    
    }

}
