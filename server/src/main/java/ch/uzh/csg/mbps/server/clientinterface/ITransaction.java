package ch.uzh.csg.mbps.server.clientinterface;

import java.util.ArrayList;

import ch.uzh.csg.mbps.customserialization.PaymentRequest;
import ch.uzh.csg.mbps.customserialization.ServerPaymentRequest;
import ch.uzh.csg.mbps.customserialization.ServerPaymentResponse;
import ch.uzh.csg.mbps.model.HistoryTransaction;
import ch.uzh.csg.mbps.server.domain.UserAccount;
import ch.uzh.csg.mbps.server.util.exceptions.TransactionException;
import ch.uzh.csg.mbps.server.util.exceptions.UserAccountNotFoundException;

public interface ITransaction {
	
	/**
	 * Returns the history of Transactions of a given UserAccount. The number of
	 * items returned is limited and returned in a pagination approach. So only
	 * the items of the given page will be returned. If a page number is too
	 * large, an empty list might be returned. The returned lists are ordered by
	 * the item's time stamp descending.
	 * 
	 * @param username
	 *            the username of the UserAccount
	 * @param page
	 *            the page number. This must not be negative.
	 * @return ArrayList of HistoryTransactions
	 * @throws UserAccountNotFoundException
	 *             if the username is not found in the database
	 */
	public ArrayList<HistoryTransaction> getHistory(String username, int page) throws UserAccountNotFoundException;
	
	/**
	 * Creates a new Transaction on the server/database.
	 * 
	 * @param authenticatedUser
	 *            the username of the authenticated user
	 * @param toVerify
	 *            the {@link ServerPaymentRequest} containing one or two
	 *            {@link PaymentRequest}
	 * @return If the server has accepted and executed this given Transaction,
	 *         than it signs the object with his private key. The callers can
	 *         then verify the Transaction which has been executed.
	 * @throws TransactionException
	 *             If the {@link PaymentRequest} objects (contained in the
	 *             object {@link ServerPaymentRequest}) received are not
	 *             identical, if the signatures are not valid, or if any other
	 *             transaction specific problem occurs.
	 * @throws UserAccountNotFoundException
	 *             If the a {@link UserAccount} contained in one or both
	 *             Transaction objects cannot be found.
	 */
	public ServerPaymentResponse createTransaction(String authenticatedUser, ServerPaymentRequest toVerify) throws TransactionException, UserAccountNotFoundException;

	/**
	 * Returns the five last Transactions of a given {@link UserAccount}.
	 * The returned lists are ordered by the item's time stamp in descending
	 * order.
	 * 
	 * @param username
	 * @return ArrayList<HistoryTransaction>
	 * @throws UserAccountNotFoundException
	 */
	public ArrayList<HistoryTransaction> getLast5Transactions(String username) throws UserAccountNotFoundException;
	
}
