package ch.uzh.csg.mbps.server.service;

import java.util.ArrayList;

import ch.uzh.csg.mbps.model.HistoryServerAccountTransaction;
import ch.uzh.csg.mbps.server.clientinterface.IServerTransaction;
import ch.uzh.csg.mbps.server.dao.ServerTransactionDAO;
import ch.uzh.csg.mbps.server.domain.ServerTransaction;
import ch.uzh.csg.mbps.server.util.exceptions.ServerAccountNotFoundException;

public class ServerTransactionService implements IServerTransaction{
	
	//TODO: mehmet move to a config file
	public static final String BALANCE = "Not sufficient funds. Payment rejected.";
	public static final String NEGATIVE_AMOUNT = "The transaction amount can't be negative or equals 0.";
	public static final String HIBERNATE_ERROR = "An error occured while persisting the data. Please try again later.";
	public static final String INTERNAL_ERROR = "An internal error occured. Please try again later.";
	public static final String PAYMENT_REFUSE = "The server refused the payment.";

	private static ServerTransactionService serverTransactionService;

	private ServerTransactionService() {
	}
	
	//TODO: mehmet: javadoc
	/**
	 * 
	 * @return
	 */
	public static ServerTransactionService getInstance() {
		if (serverTransactionService == null) {
			serverTransactionService = new ServerTransactionService();
		}
			
		return serverTransactionService;
	}
	
	@Override
	public void createServerTransaction(ServerTransaction serverTransaction) throws ServerAccountNotFoundException {
		// TODO mehmet what should be passed (ServerTransaction)
		
	}

	@Override
	public ArrayList<HistoryServerAccountTransaction> getLast3Transactions() {
		return ServerTransactionDAO.getLast3Transactions();
	}

	@Override
	public ArrayList<HistoryServerAccountTransaction> getLast3ServerAccountTransaction(String url) throws ServerAccountNotFoundException {
		return ServerTransactionDAO.getLast3ServerAccountTransaction(url);
	}

	@Override
	public ArrayList<HistoryServerAccountTransaction> getHistory(int page) {
		return ServerTransactionDAO.getHistory(page);
	}

	@Override
	public ArrayList<HistoryServerAccountTransaction> getPayeeHistory(int page) {
		return ServerTransactionDAO.getPayeeHistory(page);
	}

	@Override
	public ArrayList<HistoryServerAccountTransaction> getPayerHistory(int page) {
		return ServerTransactionDAO.getPayerHistory(page);
	}

}