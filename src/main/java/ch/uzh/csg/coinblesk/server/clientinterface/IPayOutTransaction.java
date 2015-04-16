package ch.uzh.csg.coinblesk.server.clientinterface;

import java.math.BigDecimal;
import java.util.List;

import com.azazar.bitcoin.jsonrpcclient.BitcoinException;
import com.azazar.bitcoin.jsonrpcclient.IBitcoinRPC.Transaction;

import ch.uzh.csg.coinblesk.model.HistoryPayOutTransaction;
import ch.uzh.csg.coinblesk.responseobject.TransferObject;
import ch.uzh.csg.coinblesk.server.domain.PayOutTransaction;
import ch.uzh.csg.coinblesk.server.domain.UserAccount;
import ch.uzh.csg.coinblesk.server.util.exceptions.UserAccountNotFoundException;

public interface IPayOutTransaction {

	/**
	 * Returns history of {@link PayOutTransaction}s of {@link UserAccount} with username. Only
	 * the Transactions defined by page are returned, not all {@link PayOutTransaction}s.
	 * 
	 * @param username
	 * @param page
	 * @return ArrayListy<PayOutTransaction>
	 * @throws UserAccountNotFoundException
	 */
	public List<HistoryPayOutTransaction> getHistory(String username, int page) throws UserAccountNotFoundException;

	/**
	 * Counts and returns number of {@link PayOutTransaction}s which are saved in the DB
	 * for {@link UserAccount} with username.
	 * 
	 * @param username
	 * @return number of PayOutTransaction
	 * @throws UserAccountNotFoundException
	 */
	public long getHistoryCount(String username) throws UserAccountNotFoundException;

	/**
	 * Creates a new {@link PayOutTransaction} for {@link UserAccount} with username.
	 * 
	 * @param username
	 * @param pot PayOutTransaction
	 * @return CustomResponseObject with information about success/non success of creation and notification message.
	 * @throws BitcoinException
	 * @throws UserAccountNotFoundException
	 */
	public TransferObject createPayOutTransaction(String username, BigDecimal amount, String address) throws BitcoinException, UserAccountNotFoundException;

	/**
	 * Checks if {@link PayOutTransaction} which has min-confirmations from the Bitcoin
	 * network is already verified. If no it set isVerified to true.
	 * 
	 * @param transaction
	 */
	public void check(Transaction transaction);

	/**
	 * Returns five last {@link PayOutTransaction}s for {@link UserAccount}
	 * specified by given username.
	 * 
	 * @param username
	 * @return ArrayListy<PayOutTransaction>
	 * @throws UserAccountNotFoundException
	 */
	public List<HistoryPayOutTransaction> getLast5Transactions(String username) throws UserAccountNotFoundException;

	public void createPayOutTransaction(PayOutTransaction tx) throws UserAccountNotFoundException;
}