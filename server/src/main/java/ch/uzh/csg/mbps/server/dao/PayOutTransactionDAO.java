package ch.uzh.csg.mbps.server.dao;

import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;
import org.hibernate.HibernateException;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.criterion.Restrictions;
import org.hibernate.transform.Transformers;

import ch.uzh.csg.mbps.model.HistoryPayOutTransaction;
import ch.uzh.csg.mbps.server.domain.PayOutTransaction;
import ch.uzh.csg.mbps.server.domain.UserAccount;
import ch.uzh.csg.mbps.server.service.UserAccountService;
import ch.uzh.csg.mbps.server.util.Config;
import ch.uzh.csg.mbps.server.util.HibernateUtil;
import ch.uzh.csg.mbps.server.util.exceptions.TransactionException;
import ch.uzh.csg.mbps.server.util.exceptions.UserAccountNotFoundException;

/**
 * DatabaseAccessObject for {@link PayOutTransaction}s. Handles all DB operations
 * regarding {@link PayOutTransaction}s.
 * 
 */
public class PayOutTransactionDAO {
	private static Logger LOGGER = Logger.getLogger(PayOutTransactionDAO.class);
	
	private static Session openSession() {
		SessionFactory sessionFactory = HibernateUtil.getSessionFactory();
		return sessionFactory.openSession();
	}
	
	/**
	 * Returns defined amount of {@link PayOutTransaction}s assigned to the given
	 * username as an ArrayList. Number of POTs and selection is defined in the
	 * Config-File and by the parameter "page".
	 * 
	 * @param username for which UserAccount history is requested
	 * @param page which defines which page of PayOutTransaction shall be returned (NrX to NrY)
	 * @return ArrayList<HistoryPayOutTransaction> (an array list with the requested amount of PayOutTransactions)
	 * @throws UserAccountNotFoundException
	 */
	public static ArrayList<HistoryPayOutTransaction> getHistory(String username, int page) throws UserAccountNotFoundException {
		if (page < 0)
			return null;
		
		UserAccount userAccount = UserAccountService.getInstance().getByUsername(username);
		Session session = openSession();
		session.beginTransaction();
				
		@SuppressWarnings("unchecked")
		List<HistoryPayOutTransaction> resultWithAliasedBean = session.createSQLQuery(
				  "SELECT pot.timestamp, pot.amount, pot.btc_address as btcAddress " +
				  "FROM pay_out_transaction pot " +
				  "WHERE pot.user_id = :userid " +
				  "ORDER BY pot.timestamp DESC")
				  .addScalar("timestamp")
				  .addScalar("amount")
				  .addScalar("btcAddress")
				  .setLong("userid",  userAccount.getId())
				  .setFirstResult(page * Config.PAY_OUTS_MAX_RESULTS)
				  .setMaxResults(Config.PAY_OUTS_MAX_RESULTS)
				  .setFetchSize(Config.PAY_OUTS_MAX_RESULTS)
				  .setResultTransformer(Transformers.aliasToBean(HistoryPayOutTransaction.class))
				  .list();
		
		List<HistoryPayOutTransaction> results = resultWithAliasedBean;
		session.close();
		
		return new ArrayList<HistoryPayOutTransaction>(results);
	}

	/**
	 * Counts number of {@link PayOutTransaction}-entries for given username and returns
	 * number as long.
	 * 
	 * @param username
	 * @return number of PayOutTransaction
	 * @throws UserAccountNotFoundException if no POTs are assigned to given UserAccount
	 */
	public static long getHistoryCount(String username) throws UserAccountNotFoundException {
		UserAccount userAccount = UserAccountService.getInstance().getByUsername(username);
		Session session = openSession();
		session.beginTransaction();
		
		long nofResults = ((Number) session.createSQLQuery(
				  "SELECT COUNT(*) " +
				  "FROM pay_out_transaction pot " +
				  "WHERE pot.user_id = :userid")
				  .setLong("userid", userAccount.getId())
				  .uniqueResult())
				  .longValue();

		session.close();
		
		return nofResults;
	}

	/**
	 * Saves a new {@link PayOutTransaction} in the database.
	 * 
	 * @param pot PayOutTransaction to save
	 * @throws UserAccountNotFoundException if defined UserAccount is not found
	 * @throws HibernateException
	 */
	public static void createPayOutTransaction(PayOutTransaction pot) throws UserAccountNotFoundException, HibernateException {
		Session session = openSession();
		org.hibernate.Transaction transaction = null;
		UserAccount userAccount;
		try {
			transaction = session.beginTransaction();
			
			session.save(pot);
			
			//update UserAccountBalance
			userAccount = UserAccountDAO.getById(pot.getUserID());
			userAccount.setBalance(userAccount.getBalance().subtract(pot.getAmount()));
			session.update(userAccount);
			
			transaction.commit();
			LOGGER.info("PayOutTransaction created for UserAccount with ID: " + userAccount.getId() + " Transaction: " + pot.toString());
		} catch (HibernateException e) {
			LOGGER.error("Error creating PayOutTransaction: " + pot.toString() + "ErrorMessage: " + e.getMessage());
			if (transaction != null)
				transaction.rollback();
			throw e;
		} catch (UserAccountNotFoundException e) {
			throw e;
		} finally {
			session.close();
		}
	}

	/**
	 * Verifies a {@link PayOutTransaction} in the DB.
	 * 
	 * @param pot PayOutTransation to be verified.
	 * @throws TransactionException if PayOutTransaction with specified TransactionId does not exist in the database.
	 * @throws HibernateException
	 */
	public static void verify(PayOutTransaction pot) throws TransactionException, HibernateException {
		Session session = openSession();
		session.beginTransaction();
		PayOutTransaction existingPOT = (PayOutTransaction) session.createCriteria(PayOutTransaction.class).add(Restrictions.eq("transactionID", pot.getTransactionID())).uniqueResult();
		session.close();

		if(existingPOT == null){
			throw new TransactionException("Problem verifying PayOutTransaction. This transaction does not exists.");
		}
				
		if(!existingPOT.isVerified()){
			existingPOT.setVerified(true);
			Session session2 = openSession();
			org.hibernate.Transaction transaction = null;			
		
			try {
				transaction = session2.beginTransaction();
				session2.update(existingPOT);
				transaction.commit();
				LOGGER.info("Successfully verified PayOutTransaction with ID: " + existingPOT.getId());
			} catch (HibernateException e) {
				LOGGER.error("Problem verifying PayOutTransaction with TransactionID: " + pot.getTransactionID());
				 if (transaction != null)
					 transaction.rollback();
				 throw e;
			} finally {
				session2.close();
			}
		}
	}
	
	//TODO simon: create Javadoc
	public static ArrayList<HistoryPayOutTransaction> getLast5Transactions(
			String username) throws UserAccountNotFoundException {
		UserAccount userAccount = UserAccountService.getInstance().getByUsername(username);
		Session session = openSession();
		session.beginTransaction();
				//TODO simon: test!
		@SuppressWarnings("unchecked")
		List<HistoryPayOutTransaction> resultWithAliasedBean = session.createSQLQuery(
				  "SELECT pot.timestamp, pot.amount, pot.btc_address as btcAddress " +
				  "FROM pay_out_transaction pot " +
				  "WHERE pot.user_id = :userid " +
				  "ORDER BY pot.timestamp DESC")
				  .addScalar("timestamp")
				  .addScalar("amount")
				  .addScalar("btcAddress")
				  .setLong("userid",  userAccount.getId())
//				  .setFirstResult(page * Config.PAY_OUTS_MAX_RESULTS)
				  .setMaxResults(5)
				  .setFetchSize(5)
				  .setResultTransformer(Transformers.aliasToBean(HistoryPayOutTransaction.class))
				  .list();
		
		List<HistoryPayOutTransaction> results = resultWithAliasedBean;
		session.close();
		
		return new ArrayList<HistoryPayOutTransaction>(results);
	}
	
}
