package ch.uzh.csg.mbps.server.util;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.Properties;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;

import ch.uzh.csg.mbps.model.HistoryPayInTransaction;
import ch.uzh.csg.mbps.model.HistoryPayOutTransaction;
import ch.uzh.csg.mbps.model.HistoryTransaction;
import ch.uzh.csg.mbps.server.clientinterface.ITransaction;
import ch.uzh.csg.mbps.server.clientinterface.IUserAccount;
import ch.uzh.csg.mbps.server.service.PayInTransactionService;
import ch.uzh.csg.mbps.server.service.PayOutTransactionService;
import ch.uzh.csg.mbps.server.util.exceptions.UserAccountNotFoundException;

/**
 * Handles sending the transaction history of a user by email. Uses the
 * {@link ch.uzh.csg.mbps.server.util.Emailer} to send the email
 * asynchronously.
 */
@Controller
public class HistoryEmailHandler {
	
	@Autowired
	private IUserAccount userAccountService;
	@Autowired
	private ITransaction transactionService;
	@Autowired
	private PayInTransactionService payInTransactionService;
	@Autowired
	private PayOutTransactionService payOutTransactionService;
	
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
	public void sendHistoryByEmail(String username, int type) throws Exception {
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
		Emailer.sendHistoryCSV(username, userAccountService.getByUsername(username).getEmail(), f);
	}

	private File createFile(String username) throws Exception {
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

	private void writeTransactions(BufferedWriter bufferedWriter, String username) throws UserAccountNotFoundException, IOException {
		boolean hasMore = true;
		
		for (int page=0; hasMore; page++) {
			List<HistoryTransaction> history = transactionService.getHistory(username, page);
			for (HistoryTransaction htx : history) {
				bufferedWriter.write(htx.getTimestamp().toString() + ", " + htx.getBuyer() + ", " + htx.getSeller() + ", " + htx.getAmount().toString() + "\n");
			}
			hasMore = (history.size() > 0) ? true : false;
		}
	}

	private void writePayInTransactions(BufferedWriter bufferedWriter, String username) throws UserAccountNotFoundException, IOException {
		boolean hasMore = true;
		
		for (int page=0; hasMore; page++) {
			List<HistoryPayInTransaction> history = payInTransactionService.getHistory(username, page);
			for (HistoryPayInTransaction htx : history) {
				bufferedWriter.write(htx.getTimestamp().toString() + ", " + htx.getAmount().toString() + "\n");
			}
			hasMore = (history.size() > 0) ? true : false;
		}
	}

	private void writePayOutTransactions(BufferedWriter bufferedWriter, String username) throws UserAccountNotFoundException, IOException {
		boolean hasMore = true;
		
		for (int page=0; hasMore; page++) {
			List<HistoryPayOutTransaction> history = payOutTransactionService.getHistory(username, page);
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
