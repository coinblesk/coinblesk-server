package ch.uzh.csg.mbps.server.dao;

import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;
import org.hibernate.HibernateException;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.criterion.Restrictions;
import org.hibernate.transform.Transformers;

import ch.uzh.csg.mbps.customserialization.Currency;
import ch.uzh.csg.mbps.model.HistoryTransaction;
import ch.uzh.csg.mbps.server.domain.DbTransaction;
import ch.uzh.csg.mbps.server.domain.UserAccount;
import ch.uzh.csg.mbps.server.service.UserAccountService;
import ch.uzh.csg.mbps.server.util.Config;
import ch.uzh.csg.mbps.server.util.HibernateUtil;
import ch.uzh.csg.mbps.server.util.exceptions.UserAccountNotFoundException;
import ch.uzh.csg.mbps.util.Converter;

/**
 * DatabaseAccessObject for {@link DbTransaction}. Handles all DB operations regarding
 * {@link DbTransaction}s between two {@link UserAccount}s.
 * 
 */
public class TransactionDAO {
	private static Logger LOGGER = Logger.getLogger(TransactionDAO.class);

	private static Session openSession() {
		SessionFactory sessionFactory = HibernateUtil.getSessionFactory();
		return sessionFactory.openSession();
	}

	/**
	 * Returns defined amount of {@link DbTransaction}s assigned to the given
	 * username as an ArrayList. Number of Transactions and selection is defined
	 * in the Config-File and by the parameter "page".
	 * 
	 * @param username
	 *            for which history is requested
	 * @param page
	 *            which defines which page of Transactions shall be returned
	 *            (NrX to NrY)
	 * @return ArrayList with requested amount of HistoryTransactions
	 * @throws UserAccountNotFoundException
	 */
	@SuppressWarnings("unchecked")
	public static ArrayList<HistoryTransaction> getHistory(String username, int page) throws UserAccountNotFoundException, HibernateException {
		if (page < 0)
			return null;
		
		UserAccount userAccount = UserAccountService.getInstance().getByUsername(username);
		Session session = openSession();
		session.beginTransaction();	
		List<HistoryTransaction> resultWithAliasedBean = session
				.createSQLQuery(
						"SELECT transaction.timestamp, u2.username as buyer, u1.username as seller, transaction.amount, transaction.input_currency as inputCurrency, transaction.input_currency_amount as inputCurrencyAmount "
								+ "FROM DB_TRANSACTION transaction "
								+ "INNER JOIN user_account u1 on transaction.username_payee = u1.username "
								+ "INNER JOIN user_account u2 on transaction.username_payer = u2.username "
								+ "WHERE transaction.username_payer = :username OR transaction.username_payee = :username "
								+ "ORDER BY transaction.timestamp DESC")
				.addScalar("timestamp")
				.addScalar("buyer")
				.addScalar("seller")
				.addScalar("amount")
				.addScalar("inputCurrency")
				.addScalar("inputCurrencyAmount")
				.setString("username", userAccount.getUsername())
				.setFirstResult(page * Config.TRANSACTIONS_MAX_RESULTS)
				.setMaxResults(Config.TRANSACTIONS_MAX_RESULTS)
				.setFetchSize(Config.TRANSACTIONS_MAX_RESULTS)
				.setResultTransformer(Transformers.aliasToBean(HistoryTransaction.class))
				.list();

		List<HistoryTransaction> results = resultWithAliasedBean;
		session.close();
		
		return new ArrayList<HistoryTransaction>(results);
	}

	/**
	 * Counts number of {@link DbTransaction}-entries for given username and
	 * returns number as long.
	 * 
	 * @param username
	 *            for which Transactions shall be counted.
	 * @return number of Transactions assigned to username.
	 * @throws UserAccountNotFoundException
	 */
	public static long getHistoryCount(String username) throws UserAccountNotFoundException {
		UserAccount userAccount = UserAccountService.getInstance().getByUsername(username);
		Session session = openSession();
		session.beginTransaction();
		long nofResults = ((Number) session.createSQLQuery(
				  "SELECT COUNT(*) " +
				  "FROM DB_TRANSACTION transaction " +
				  "INNER JOIN user_account u1 on transaction.username_payee = u1.username " +
				  "INNER JOIN user_account u2 on transaction.username_payer = u2.username " +
				  "WHERE transaction.username_payer = :username OR transaction.username_payee = :username")
				  .setString("username",userAccount.getUsername())
				  .uniqueResult())
				  .longValue();

		session.close();
		
		return nofResults;
	}

	/**
	 * Saves a new {@link DbTransaction} in the database.
	 * 
	 * @param tx
	 *            to save in the DB
	 * @param buyerAccount
	 *            UserAccount from which transaction-amount is subtracted.
	 * @param sellerAccount
	 *            UserAccount to which transaction-amount is added.
	 * @throws HibernateException
	 */
	public static void createTransaction(DbTransaction tx, UserAccount buyerAccount, UserAccount sellerAccount) throws HibernateException {
		Session session = openSession();
		org.hibernate.Transaction transaction = null;
		
		try {
			transaction = session.beginTransaction();
			session.save(tx);
			buyerAccount.setBalance(buyerAccount.getBalance().subtract(tx.getAmount()));
			session.update(buyerAccount);
			
			sellerAccount.setBalance(sellerAccount.getBalance().add(tx.getAmount()));
			session.update(sellerAccount);
			
			transaction.commit();			
			
			LOGGER.info("Transaction created: " + tx.toString());
		} catch (HibernateException e) {
			LOGGER.error("Problem creating Transaction: " + tx.toString() + " " + e.getMessage());
			if (transaction != null)
				transaction.rollback();
			
			throw e;
		} finally {
			session.close();
		}
	}
	
	/**
	 * Checks if a Transaction with the given parameters does already exist.
	 * 
	 * @param usernamePayer
	 *            the payer's username
	 * @param usernamePayee
	 *            the payee's username
	 * @param currency
	 *            the currency
	 * @param amount
	 *            the amount
	 * @param timestamp
	 *            the payer's timestamp
	 * @return
	 */
	public static boolean exists(String usernamePayer, String usernamePayee, Currency currency, long amount, long timestampPayer) {
		Session session = openSession();
		session.beginTransaction();
		
		DbTransaction tx = (DbTransaction) session.createCriteria(DbTransaction.class)
				.add(Restrictions.eq("usernamePayer", usernamePayer))
				.add(Restrictions.eq("usernamePayee", usernamePayee))
				.add(Restrictions.eq("currency", currency.getCurrencyCode()))
				.add(Restrictions.eq("amount", Converter.getBigDecimalFromLong(amount)))
				.add(Restrictions.eq("timestampPayer", timestampPayer))
				.uniqueResult();
		
		session.close();
		
		if (tx != null) {
			return true;
		} else {
			return false;
		}
	}

	/**
	 * Returns 5 newest Transactions as {@link HistoryTransaction}s in
	 * descending order.
	 * 
	 * @param username
	 * @return ArrayList<HistoryTransaction>
	 * @throws UserAccountNotFoundException
	 */
	@SuppressWarnings("unchecked")
	public static ArrayList<HistoryTransaction> getLast5Transactions(String username) throws UserAccountNotFoundException {
		UserAccount userAccount = UserAccountService.getInstance().getByUsername(username);
		Session session = openSession();
		session.beginTransaction();
		
		List<HistoryTransaction> resultWithAliasedBean = session.createSQLQuery(
				  "SELECT transaction.timestamp, u2.username as buyer, u1.username as seller, transaction.amount, transaction.input_currency as inputCurrency, transaction.input_currency_amount as inputCurrencyAmount " +
				  "FROM DB_TRANSACTION transaction " +
				  "INNER JOIN user_account u1 on transaction.username_payee = u1.username " +
				  "INNER JOIN user_account u2 on transaction.username_payer = u2.username " +
				  "WHERE transaction.username_payer = :username OR transaction.username_payee = :username " +
				  "ORDER BY transaction.timestamp DESC")
				  .addScalar("timestamp")
				  .addScalar("buyer")
				  .addScalar("seller")
				  .addScalar("amount")
				  .addScalar("inputCurrency")
				  .addScalar("inputCurrencyAmount")
				  .setString("username", userAccount.getUsername())
				  .setMaxResults(5)
				  .setFetchSize(5)
				  .setResultTransformer(Transformers.aliasToBean(HistoryTransaction.class))
				  .list();

		List<HistoryTransaction> results = resultWithAliasedBean;
		session.close();
		
		return new ArrayList<HistoryTransaction>(results);
	}
	
}
