package ch.uzh.csg.mbps.server.clientinterface;

import java.util.ArrayList;

import ch.uzh.csg.mbps.model.HistoryServerAccountTransaction;
import ch.uzh.csg.mbps.server.domain.ServerTransaction;
import ch.uzh.csg.mbps.server.util.exceptions.ServerAccountNotFoundException;

public interface IServerTransaction {

	
	//TODO: mehmet do comments
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
	public ArrayList<HistoryServerAccountTransaction> getLast3Transactions();
	
	/**
	 * 
	 * @param url
	 * @return
	 * @throws ServerAccountNotFoundException
	 */
	public ArrayList<HistoryServerAccountTransaction> getLast3ServerAccountTransaction(String url) throws ServerAccountNotFoundException;
	
	/**
	 * 
	 * @param page
	 * @return
	 */
	public ArrayList<HistoryServerAccountTransaction> getHistory(int page);
	
	/**
	 * 
	 * @param page
	 * @return
	 */
	public ArrayList<HistoryServerAccountTransaction> getPayeeHistory(int page);

	/**
	 * 
	 * @param page
	 * @return
	 */
	public ArrayList<HistoryServerAccountTransaction> getPayerHistory(int page);
}