package ch.uzh.csg.mbps.server.dao;

import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;
import org.hibernate.HibernateException;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.criterion.Restrictions;
import org.hibernate.transform.Transformers;

import ch.uzh.csg.mbps.model.HistoryPayInTransaction;
import ch.uzh.csg.mbps.server.domain.PayInTransaction;
import ch.uzh.csg.mbps.server.domain.UserAccount;
import ch.uzh.csg.mbps.server.service.UserAccountService;
import ch.uzh.csg.mbps.server.util.Config;
import ch.uzh.csg.mbps.server.util.HibernateUtil;
import ch.uzh.csg.mbps.server.util.exceptions.UserAccountNotFoundException;

/**
 * DatabaseAccessObject for {@link PayInTransaction}. Handles all DB operations
 * regarding PayInTransactions.
 * 
 */
public class PayInTransactionDAO {
	private static Logger LOGGER = Logger.getLogger(PayInTransactionDAO.class);
	
	private static Session openSession() {
		SessionFactory sessionFactory = HibernateUtil.getSessionFactory();
		return sessionFactory.openSession();
	}

	/**
	 * Saves a new {@link PayInTransaction} in the database.
	 * 
	 * @param tx (PayInTransaction)
	 * @throws HibernateException
	 * @throws UserAccountNotFoundException
	 */
	public static void createPayInTransaction(PayInTransaction tx) throws HibernateException, UserAccountNotFoundException {
		Session session = openSession();
		org.hibernate.Transaction transaction = null;
		
		try {
			transaction = session.beginTransaction();
			
			session.save(tx);
			
			//update UserAccountBalance
			UserAccount userAccount = UserAccountDAO.getById(tx.getUserID());
			userAccount.setBalance(userAccount.getBalance().add(tx.getAmount()));
			session.update(userAccount);
			
			transaction.commit();
			LOGGER.info("PayInTransaction created and balance for user with ID " + tx.getUserID() + " updated: " + tx.toString());
		} catch (HibernateException e) {
			LOGGER.error("Problem creating PayInTransaction: " + tx.toString() + "ErrorMessage: " + e.getMessage());
			if (transaction != null)
				transaction.rollback();
			throw e;
		} finally {
			session.close();
		}
	}

	/**
	 * Checks if {@link PayInTransaction} is already saved in the database and returns
	 * true (false) if pit doesn't exist yet (already exists).
	 * 
	 * @param pit
	 * @return boolean if PayInTransaction is new (not in DB yet).
	 */
	public static boolean isNew(PayInTransaction pit) {
		Session session = openSession();
		session.beginTransaction();
		PayInTransaction existingPIT = (PayInTransaction) session.createCriteria(PayInTransaction.class).add(Restrictions.eq("userID", pit.getUserID())).add(Restrictions.eq("transactionID", pit.getTransactionID())).uniqueResult();
		session.close();
	
		if (existingPIT == null)
			return true;
		else
			return false;
	}

	/**
	 * Returns defined amount of {@link PayInTransaction}s assigned to the given
	 * username as an ArrayList. Number of PITs and selection is defined in the
	 * Config-File and by the parameter "page".
	 * 
	 * @param username
	 * @param page which defines which page of PayInTransaction shall be returned (NrX to NrY)
	 * @return ArrayList<HistoryPayInTransaction>
	 * @throws UserAccountNotFoundException
	 */
	public static ArrayList<HistoryPayInTransaction> getHistory(String username, int page) throws UserAccountNotFoundException {
		if (page < 0)
			return null;
		
		UserAccount userAccount = UserAccountService.getInstance().getByUsername(username);
		Session session = openSession();
		session.beginTransaction();
				
		@SuppressWarnings("unchecked")
		List<HistoryPayInTransaction> resultWithAliasedBean = session.createSQLQuery(
				  "SELECT pit.timestamp, pit.amount " +
				  "FROM pay_in_transaction pit " +
				  "WHERE pit.user_id = :userid " +
				  "ORDER BY pit.timestamp DESC")
				  .addScalar("timestamp")
				  .addScalar("amount")
				  .setLong("userid",  userAccount.getId())
				  .setFirstResult(page * Config.PAY_INS_MAX_RESULTS)
				  .setMaxResults(Config.PAY_INS_MAX_RESULTS)
				  .setFetchSize(Config.PAY_INS_MAX_RESULTS)
				  .setResultTransformer(Transformers.aliasToBean(HistoryPayInTransaction.class))
				  .list();
		
		List<HistoryPayInTransaction> results = resultWithAliasedBean;
		session.close();
		
		return new ArrayList<HistoryPayInTransaction>(results);
	}
	
	/**
	 * Counts number of {@link PayInTransaction}-entries for given username and returns
	 * number as long.
	 * 
	 * @param username
	 * @return long number of PayInTransactions
	 * @throws UserAccountNotFoundException  if no PITs are assigned to given UserAccount
	 */
	public static long getHistoryCount(String username) throws UserAccountNotFoundException {
		UserAccount userAccount = UserAccountService.getInstance().getByUsername(username);
		Session session = openSession();
		session.beginTransaction();
				
		long nofResults = ((Number) session.createSQLQuery(
				  "SELECT COUNT(*) " +
				  "FROM pay_in_transaction pit " +
				  "WHERE pit.user_id = :userid")
				  .setLong("userid", userAccount.getId())
				  .uniqueResult())
				  .longValue();
		session.close();
		
		return nofResults;
	}
}
