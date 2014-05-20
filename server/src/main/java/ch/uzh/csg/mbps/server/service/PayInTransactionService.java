package ch.uzh.csg.mbps.server.service;

import java.util.ArrayList;

import ch.uzh.csg.mbps.model.HistoryPayInTransaction;
import ch.uzh.csg.mbps.responseobject.CustomResponseObject;
import ch.uzh.csg.mbps.server.dao.PayInTransactionDAO;
import ch.uzh.csg.mbps.server.domain.PayInTransaction;
import ch.uzh.csg.mbps.server.domain.UserAccount;
import ch.uzh.csg.mbps.server.util.Emailer;
import ch.uzh.csg.mbps.server.util.exceptions.UserAccountNotFoundException;

import com.azazar.bitcoin.jsonrpcclient.Bitcoin.Transaction;

/**
 * Service class for {@link PayInTransaction}.
 * 
 */
public class PayInTransactionService {
	private static PayInTransactionService payInTransactionService;

	private PayInTransactionService() {
	}

	/**
	 * Returns new or existing instance of {@link PayInTransactionService}.
	 * 
	 * @return instance of PayInTransactionService
	 */
	public static PayInTransactionService getInstance() {
		if (payInTransactionService == null)
			payInTransactionService = new PayInTransactionService();

		return payInTransactionService;
	}

	/**
	 * Creates a new {@link PayInTransaction} for {@link UserAccount} with BTC Address defined
	 * in transaction.
	 * 
	 * @param transaction
	 * @throws UserAccountNotFoundException
	 */
	public static void create(Transaction transaction)
			throws UserAccountNotFoundException {
		PayInTransaction pit = new PayInTransaction(transaction);
		if (PayInTransactionDAO.isNew(pit))
			PayInTransactionDAO.createPayInTransaction(pit);
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
	public ArrayList<HistoryPayInTransaction> getHistory(String username,
			int page) throws UserAccountNotFoundException {
		return PayInTransactionDAO.getHistory(username, page);
	}

	/**
	 * Counts and returns number of {@link PayInTransaction}s which are saved in the DB
	 * for {@link UserAccount} with username.
	 * 
	 * @param username of UserAccount
	 * @return number of PayInTrasactions
	 * @throws UserAccountNotFoundException
	 */
	public long getHistoryCount(String username)
			throws UserAccountNotFoundException {
		return PayInTransactionDAO.getHistoryCount(username);
	}

	public CustomResponseObject sendPayInAddressByEmail(String username, String email, String payInAddress) {	
		Emailer.sendPayInAddressAsEmail(username, email, payInAddress);
		return new CustomResponseObject(true, "Pay in address is send to your email address.");
	}

	/**
	 * Returns 5 newest {@link PayInTransaction}s for specified username in descending order.
	 * 
	 * @param username
	 * @return ArrayList<{@link HistoryPayInTransaction>
	 * @throws UserAccountNotFoundException
	 */
	public ArrayList<HistoryPayInTransaction> getLast5Transactions(String username) throws UserAccountNotFoundException {
		return PayInTransactionDAO.getLast5Transactions(username);
	}

}
