package ch.uzh.csg.mbps.server.clientinterface;

import java.security.SignedObject;
import java.util.ArrayList;

import ch.uzh.csg.mbps.model.HistoryTransaction;
import ch.uzh.csg.mbps.model.Transaction;
import ch.uzh.csg.mbps.server.util.exceptions.TransactionException;
import ch.uzh.csg.mbps.server.util.exceptions.UserAccountNotFoundException;
import ch.uzh.csg.mbps.util.Pair;

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
	 * @param toVerify
	 *            contains a Pair of SignedObject. The first item is the
	 *            SignedObject of the buyer, which is a {@link Transaction}
	 *            signed with the buyer's private key. The second item is the
	 *            seller's SignedObject, which is signed with the seller's
	 *            private key. The two signed {@link Transaction} objects must
	 *            be identical in order to be accepted.
	 * @return If the server has accepted and executed this given
	 *         {@link Transaction}, than it signs the object with his private
	 *         key. The callers can then verify the Transaction which has been
	 *         executed.
	 * @throws TransactionException
	 *             If the {@link Transaction} objects received from buyer and
	 *             seller are not identical, if the signatures are not valid, or
	 *             if any other transaction specific problem occurs.
	 * @throws UserAccountNotFoundException
	 *             If the a {@link UserAccount} contained in one or both
	 *             {@link Transaction} objects cannot be found.
	 */
	public SignedObject createTransaction(Pair<SignedObject> toVerify) throws TransactionException, UserAccountNotFoundException;

	/**
	 * Returns the five last {@link Transaction}s of a given {@link UserAccount}.
	 * The returned lists are ordered by the item's time stamp in descending
	 * order.
	 * 
	 * @param username
	 * @return ArrayList<HistoryTransaction>
	 * @throws UserAccountNotFoundException
	 */
	public ArrayList<HistoryTransaction> getLast5Transactions(String username) throws UserAccountNotFoundException;
	
}
