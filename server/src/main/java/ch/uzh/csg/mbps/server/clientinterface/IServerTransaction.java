package ch.uzh.csg.mbps.server.clientinterface;

import java.util.List;

import ch.uzh.csg.mbps.server.domain.ServerTransaction;
import ch.uzh.csg.mbps.server.util.exceptions.ServerAccountNotFoundException;
import ch.uzh.csg.mbps.server.util.web.model.HistoryServerAccountTransaction;

public interface IServerTransaction {

	
	//TODO: mehmet: javadoc
	/**
	 * 
	 * @param serverTransaction
	 * @throws ServerAccountNotFoundException
	 */
	public void createServerTransaction(ServerTransaction serverTransaction) throws ServerAccountNotFoundException;
	
	/**
	 * 
	 * @return
	 */
	public List<HistoryServerAccountTransaction> getLast5Transactions();
	
	/**
	 * 
	 * @param url
	 * @return
	 * @throws ServerAccountNotFoundException
	 */
	public List<HistoryServerAccountTransaction> getLast5ServerAccountTransaction(String url) throws ServerAccountNotFoundException;
	
	/**
	 * 
	 * @param page
	 * @return
	 */
	public List<HistoryServerAccountTransaction> getHistory(int page);
	
	/**
	 * 
	 * @param page
	 * @return
	 */
	public List<HistoryServerAccountTransaction> getPayeeHistory(int page);

	/**
	 * 
	 * @param page
	 * @return
	 */
	public List<HistoryServerAccountTransaction> getPayerHistory(int page);

	/**
	 * 
	 * @param url
	 * @param page
	 * @return
	 * @throws ServerAccountNotFoundException 
	 */
	List<HistoryServerAccountTransaction> getServerAccountTransactions(String url, int page) throws ServerAccountNotFoundException;
}