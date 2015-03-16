package ch.uzh.csg.coinblesk.server.clientinterface;

import java.util.List;

import ch.uzh.csg.coinblesk.model.HistoryPayInTransaction;
import ch.uzh.csg.coinblesk.responseobject.TransferObject;
import ch.uzh.csg.coinblesk.server.domain.PayInTransaction;
import ch.uzh.csg.coinblesk.server.domain.UserAccount;
import ch.uzh.csg.coinblesk.server.util.exceptions.UserAccountNotFoundException;

import com.azazar.bitcoin.jsonrpcclient.Bitcoin.Transaction;

public interface IPayInTransaction {

	/**
	 * Creates a new {@link PayInTransaction} for {@link UserAccount} with BTC Address defined
	 * in transaction.
	 * 
	 * @param transaction
	 * @throws UserAccountNotFoundException
	 */
	public void create(Transaction transaction) throws UserAccountNotFoundException;

	/**
	 * Returns history of {@link PayInTransaction} of UserAccount with username. Only
	 * the Transactions defined by page are returned, not all transactions.
	 * 
	 * @param username
	 * @param page defining which PayInTransactions shall be returned
	 * @return ArrayList of HistoryPayInTransactions
	 * @throws UserAccountNotFoundException
	 */
	public List<HistoryPayInTransaction> getHistory(String username, int page) throws UserAccountNotFoundException;

	/**
	 * Counts and returns number of {@link PayInTransaction}s which are saved in the DB
	 * for {@link UserAccount} with username.
	 * 
	 * @param username of UserAccount
	 * @return number of PayInTrasactions
	 * @throws UserAccountNotFoundException
	 */
	public long getHistoryCount(String username) throws UserAccountNotFoundException;

	/**
	 * Returns 5 newest {@link PayInTransaction}s for specified username in descending order.
	 * 
	 * @param username
	 * @return ArrayList<{@link HistoryPayInTransaction>
	 * @throws UserAccountNotFoundException
	 */
	public List<HistoryPayInTransaction> getLast5Transactions(String username) throws UserAccountNotFoundException;

	public TransferObject sendPayInAddressByEmail(String username, String email, String payInAddress);

	public void createPayInTransaction(PayInTransaction tx) throws UserAccountNotFoundException;
}
