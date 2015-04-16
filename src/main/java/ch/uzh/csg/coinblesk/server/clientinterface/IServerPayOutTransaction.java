package ch.uzh.csg.coinblesk.server.clientinterface;

import java.math.BigDecimal;
import java.util.List;

import ch.uzh.csg.coinblesk.server.domain.ServerAccount;
import ch.uzh.csg.coinblesk.server.domain.ServerPayOutTransaction;
import ch.uzh.csg.coinblesk.server.util.exceptions.ServerAccountNotFoundException;
import ch.uzh.csg.coinblesk.server.util.exceptions.UserAccountNotFoundException;
import ch.uzh.csg.coinblesk.server.web.model.HistoryServerPayOutTransaction;

import com.azazar.bitcoin.jsonrpcclient.BitcoinException;
import com.azazar.bitcoin.jsonrpcclient.IBitcoinRPC.Transaction;

public interface IServerPayOutTransaction {

	/**
	 * Creates a new {@link ServerPayOutTransaction} for {@link ServerAccount} with url.
	 * 
	 * @param url
	 * @param spot ServerPayOutTransaction
	 * @throws BitcoinException
	 * @throws ServerAccountNotFoundException
	 * @throws UserAccountNotFoundException 
	 */
	public void createPayOutTransaction(String url, BigDecimal amount, String address) throws BitcoinException, ServerAccountNotFoundException, UserAccountNotFoundException;

	/**
	 * Returns history of {@link ServerPayOutTransaction}s. Only the
	 * Transactions defined by page are returned, not all
	 * {@link ServerPayOutTransaction}s.
	 * 
	 * @param page
	 * @return List<HistoryServerPayOutTransaction>
	 */
	public List<HistoryServerPayOutTransaction> getHistory(int page);

	/**
	 * Counts and returns number of {@link ServerPayOutTransaction}s which are
	 * saved in the DB.
	 * 
	 * @return number of ServerPayOutTransaction
	 * @throws ServerAccountNotFoundException 
	 */
	public long getHistoryCount(String url) throws ServerAccountNotFoundException;

	/**
	 * Checks if {@link ServerPayOutTransaction} which has min-confirmations
	 * from the Bitcoin network is already verified. If no it set isVerified to
	 * true.
	 * 
	 * @param transaction
	 */
	public void check(Transaction transaction);

	/**
	 * Returns five last {@link ServerPayOutTransaction}s.
	 * 
	 * @return ArrayListy<HistoryServerPayOutTransaction>
	 */
	public List<HistoryServerPayOutTransaction> getLast5Transactions();

}
