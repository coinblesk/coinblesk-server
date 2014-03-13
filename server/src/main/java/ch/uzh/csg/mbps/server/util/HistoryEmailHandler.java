package ch.uzh.csg.mbps.server.util;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Properties;

import ch.uzh.csg.mbps.model.HistoryPayInTransaction;
import ch.uzh.csg.mbps.model.HistoryPayOutTransaction;
import ch.uzh.csg.mbps.model.HistoryTransaction;
import ch.uzh.csg.mbps.server.service.PayInTransactionService;
import ch.uzh.csg.mbps.server.service.PayOutTransactionService;
import ch.uzh.csg.mbps.server.service.TransactionService;
import ch.uzh.csg.mbps.server.service.UserAccountService;
import ch.uzh.csg.mbps.server.util.exceptions.UserAccountNotFoundException;

/**
 * Handles sending the transaction history of a user by email. Uses the
 * {@link ch.uzh.csg.mbps.server.util.Emailer} to send the email
 * asynchronously.
 */
public class HistoryEmailHandler {
	
	/**
	 * Sends the transaction history to the registered email address belonging
	 * the the given username. Creates a file on the hd, writes the history to
	 * that file, and then attaches this file to the email to be send to the
	 * user.
	 * 
	 * @param username
	 *            the username of the user to receive the history by email
	 * @param type
	 *            0 for common transactions, 1 for pay in transactions, 2 for
	 *            pay out transactions
	 * @throws Exception
	 *             if no user with the given username is found, or IOException
	 *             while creating/writing the local file
	 */
	public static void sendHistoryByEmail(String username, int type) throws Exception {
		File f = createFile(username);
		
		FileWriter fileWriter = new FileWriter(f);
		BufferedWriter bufferedWriter = new BufferedWriter(fileWriter);
		
		switch (type) {
		case 0:
			writeHeader(bufferedWriter, getTransactionsHeader());
			writeTransactions(bufferedWriter, username);
			break;
		case 1:
			writeHeader(bufferedWriter, getPayInTransactionsHeader());
			writePayInTransactions(bufferedWriter, username);
			break;
		case 2:
			writeHeader(bufferedWriter, getPayOutTransactionsHeader());
			writePayOutTransactions(bufferedWriter, username);
			break;
		default:
			bufferedWriter.close();
			return;
		}
		
		bufferedWriter.close();
		Emailer.sendHistoryCSV(username, UserAccountService.getInstance().getByUsername(username).getEmail(), f);
	}

	private static File createFile(String username) throws Exception {
		Properties properties = System.getProperties();
		String home = properties.get("user.home").toString();
		String separator = properties.get("file.separator").toString();
	    String dirName = "mbps";
	    String fileName = username+"-"+System.currentTimeMillis()+".csv";

	    File dir = new File(home + separator + dirName);
	    if (!dir.exists()) {
	    	if (!dir.mkdir())
	    		throw new Exception("Could not create directory: "+dir.getAbsolutePath());
	    }
	    
	    File f = new File(dir, fileName);
        if (!f.createNewFile())
        	throw new Exception("Could not create file: "+f.getAbsolutePath());
	    
		return f;
	}

	private static void writeHeader(BufferedWriter bufferedWriter, String header) throws IOException {
		bufferedWriter.write(header+"\n");	
	}

	private static void writeTransactions(BufferedWriter bufferedWriter, String username) throws UserAccountNotFoundException, IOException {
		boolean hasMore = true;
		
		for (int page=0; hasMore; page++) {
			ArrayList<HistoryTransaction> history = TransactionService.getInstance().getHistory(username, page);
			for (HistoryTransaction htx : history) {
				bufferedWriter.write(htx.getTimestamp().toString() + ", " + htx.getBuyer() + ", " + htx.getSeller() + ", " + htx.getAmount().toString() + "\n");
			}
			hasMore = (history.size() > 0) ? true : false;
		}
	}

	private static void writePayInTransactions(BufferedWriter bufferedWriter, String username) throws UserAccountNotFoundException, IOException {
		boolean hasMore = true;
		
		for (int page=0; hasMore; page++) {
			ArrayList<HistoryPayInTransaction> history = PayInTransactionService.getInstance().getHistory(username, page);
			for (HistoryPayInTransaction htx : history) {
				bufferedWriter.write(htx.getTimestamp().toString() + ", " + htx.getAmount().toString() + "\n");
			}
			hasMore = (history.size() > 0) ? true : false;
		}
	}

	private static void writePayOutTransactions(BufferedWriter bufferedWriter, String username) throws UserAccountNotFoundException, IOException {
		boolean hasMore = true;
		
		for (int page=0; hasMore; page++) {
			ArrayList<HistoryPayOutTransaction> history = PayOutTransactionService.getInstance().getHistory(username, page);
			for (HistoryPayOutTransaction htx : history) {
				bufferedWriter.write(htx.getTimestamp().toString() + ", " + htx.getBtcAddress() + ", " + htx.getAmount().toString() + "\n");
			}
			hasMore = (history.size() > 0) ? true : false;
		}
	}
	
	private static String getTransactionsHeader() {
		StringBuilder builder = new StringBuilder();
		builder.append("timestamp, ");
		builder.append("username buyer, ");
		builder.append("username seller, ");
		builder.append("amount");
		
		return builder.toString();
	}
	
	private static String getPayInTransactionsHeader() {
		StringBuilder builder = new StringBuilder();
		builder.append("timestamp, ");
		builder.append("amount");
		
		return builder.toString();
	}
	
	private static String getPayOutTransactionsHeader() {
		StringBuilder builder = new StringBuilder();
		builder.append("timestamp, ");
		builder.append("address, ");
		builder.append("amount");
		
		return builder.toString();
	}

}
