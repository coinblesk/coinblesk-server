package ch.uzh.csg.mbps.server.clientinterface;

import java.util.List;

import ch.uzh.csg.mbps.server.domain.ServerTransaction;
import ch.uzh.csg.mbps.server.util.exceptions.ServerAccountNotFoundException;
import ch.uzh.csg.mbps.server.util.web.model.HistoryServerAccountTransaction;

public interface IServerTransaction {

	/**
	 * Creates a new {@link ServerTransaction} on the server/database.
	 * 
	 * @param serverTransaction the ServerTransaction which will be stored in the DB. 
	 * @throws ServerAccountNotFoundException
	 */
	public void createServerTransaction(ServerTransaction serverTransaction) throws ServerAccountNotFoundException;
	
	/**
	 * Returns the last 5 {@link ServerTransaction}s
	 * 
	 * @return List of Server Transactions
	 */
	public List<HistoryServerAccountTransaction> getLast5Transactions();
	
	/**
	 * Returns the last 5 {@link ServerTransaction}s of a given parameter url.
	 * 
	 * @param url
	 * @return List of Server Transactions
	 * @throws ServerAccountNotFoundException
	 */
	public List<HistoryServerAccountTransaction> getLast5ServerAccountTransaction(String url) throws ServerAccountNotFoundException;
	
	/**
	 * Returns all {@link ServerTransaction}s that are made restricted to 50 transaction per page
	 * 
	 * @param page Number of the page
	 * @return List of Server Transactions
	 */
	public List<HistoryServerAccountTransaction> getHistory(int page);
	
	/**
	 * Returns a list of {@link ServerTransaction}s the server as the payee.
	 * 
	 * @param page Number of the page
	 * @return List of Server Transactions
	 */
	public List<HistoryServerAccountTransaction> getPayeeHistory(int page);

	/**
	 * Returns a list of {@link ServerTransaction}s the server as the payer.
	 * 
	 * @param page Number of the page
	 * @return List of Server Transactions
	 */
	public List<HistoryServerAccountTransaction> getPayerHistory(int page);

	/**
	 * Returns a list of transaction by a server account given by the parameter url.
	 * 
	 * @param url
	 * @param page Number of transaction
	 * @return List of Server Transactions
	 * @throws ServerAccountNotFoundException 
	 */
	public List<HistoryServerAccountTransaction> getServerAccountTransactions(String url, int page) throws ServerAccountNotFoundException;

	/**
	 * 
	 * @return the number of server transactions as long
	 */
	public long getHistoryCount();

	/**
	 * Returns the number of {@link ServerTransaction}s of a given parameter url.
	 * 
	 * @param url
	 * @return long 
	 */
	public long getServerAccountHistoryCount(String url);
}