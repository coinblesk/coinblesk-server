package ch.uzh.csg.mbps.server.dao;

import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;
import org.hibernate.HibernateException;
import org.hibernate.Query;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.criterion.Restrictions;
import org.hibernate.transform.Transformers;
import org.hibernate.type.StandardBasicTypes;

import ch.uzh.csg.mbps.model.HistoryPayOutTransaction;
import ch.uzh.csg.mbps.model.HistoryServerAccountTransaction;
import ch.uzh.csg.mbps.server.domain.ServerAccount;
import ch.uzh.csg.mbps.server.domain.ServerTransaction;
import ch.uzh.csg.mbps.server.util.Config;
import ch.uzh.csg.mbps.server.util.HibernateUtil;
import ch.uzh.csg.mbps.server.util.exceptions.ServerAccountNotFoundException;
import ch.uzh.csg.mbps.server.util.exceptions.TransactionException;

/**
 * DatabaseAccessObject for {@link ServerTransaction}s. Handles all DB operations
 * regarding {@link ServerTransaction}s.
 * 
 */
public class ServerTransactionDAO {
	private static Logger LOGGER = Logger.getLogger(ServerTransactionDAO.class);

	private ServerTransactionDAO(){
	}
	
	private static Session openSession() {
		SessionFactory sessionFactory = HibernateUtil.getSessionFactory();
		return sessionFactory.openSession();
	}
	
	//TODO: mehmet: javadoc
	/**
	 * 
	 * @param serverTransaction
	 * @throws ServerAccountNotFoundException
	 */
	public static void createServerTransaction(ServerTransaction serverTransaction) throws ServerAccountNotFoundException{
		Session session = openSession();
		org.hibernate.Transaction transaction = null;
		ServerAccount serverAccount;
		
		try{
			transaction = session.beginTransaction();
			
			session.save(serverTransaction);
			
			serverAccount = ServerAccountDAO.getByPayinAddress(serverTransaction.getPayinAddress());
			serverAccount.setActiveBalance(serverAccount.getActiveBalance().subtract(serverTransaction.getAmount()));
			session.update(serverAccount);
			
			transaction.commit();
			LOGGER.info("ServerTransaction is created for ServerAccount with ID: " +  serverAccount.getId() + " Transaction: " + serverTransaction);
		} catch (HibernateException e) {
			LOGGER.error("Error creating ServerTransaction" + serverTransaction + "Error: " + e.getMessage());
			if(transaction != null)
				transaction.rollback();
			throw e;
		} catch (ServerAccountNotFoundException e) {
			LOGGER.error("ServerAccount with the PayinAddress " + serverTransaction.getPayinAddress() + " not found");
			throw e;
		} finally {
			session.close();
		}
	}
	
	/**
	 * 
	 * @param serverTransaction
	 * @throws TransactionException
	 */
	public static void verifyTransaction(ServerTransaction serverTransaction) throws TransactionException{
		Session session = openSession();
		session.beginTransaction();
		ServerTransaction existingServerTransaction = (ServerTransaction) session.createCriteria(ServerTransaction.class).add(Restrictions.eq("transactionID", serverTransaction.getTransactionID())).uniqueResult();
		session.close();
		
		if(existingServerTransaction == null)
			throw new TransactionException("Problem verifying ServerTransaction. This transaction does not exists.");
		
		if(!existingServerTransaction.isVerified()){
			existingServerTransaction.setVerified(true);
			Session session2 = openSession();
			org.hibernate.Transaction transaction = null;
			try{
				transaction = session2.beginTransaction();
				session2.update(existingServerTransaction);
				transaction.commit();
				LOGGER.info("Successfully verified ServerTransaction with ID: " + existingServerTransaction.getId());
			} catch(HibernateException e) {
				LOGGER.error("Problem verifying ServerTransaction with TransactionID: " + serverTransaction.getTransactionID());
				 if (transaction != null)
					 transaction.rollback();
				 throw e;
			} finally {
				session.close();
			}
		}
				
	}
	
	/**
	 * 
	 * @return
	 */
	public static long getHistoryCount(){
		Session session = openSession();
		session.beginTransaction();
		
		long nofResults = ((Number) session.createSQLQuery(
				"SELECT COUNT(*) " +
				"FROM server_transaction st")
				.uniqueResult())
				.longValue();

		session.close();
		return nofResults;	
	}
	
	/**
	 * 
	 * @param url
	 * @return
	 */
	public static long getServerAccountHistoryCount(String url){
		Session session = openSession();
		session.beginTransaction();
		ServerAccount serverAccount = null;
		
		try{
			serverAccount = (ServerAccount) session.createCriteria(ServerAccount.class).add(Restrictions.eq("url", url)).uniqueResult();
		} catch (HibernateException e) {
			LOGGER.error("Problem counting Transaction of url: " + url + " ErrorMessage: "+ e.getMessage());
			throw e;
		} finally {
			session.close();
		}
		
		long nofResults = ((Number) session.createSQLQuery(
				  "SELECT COUNT(*) " +
				  "FROM server_transaction st " +
				  "WHERE st.user_id = :userid")
				  .setLong("userid", serverAccount.getId())
				  .uniqueResult())
				  .longValue();

		return nofResults;
	}
	
	/**
	 * 
	 * @return
	 */
	public static ArrayList<HistoryServerAccountTransaction> getLast3Transactions(){
		Session session = openSession();
		session.beginTransaction();
		
		@SuppressWarnings("unchecked")
		List<HistoryServerAccountTransaction> resultWithAliasedBean = session.createSQLQuery(
				  "SELECT st.timestamp, st.amount, st.payin_address as btcAddress " +
				  "FROM server_transaction st " +
				  "ORDER BY st.timestamp DESC")
				  .addScalar("timestamp")
				  .addScalar("amount")
				  .addScalar("btcAddress")
				  .setMaxResults(3)
				  .setFetchSize(3)
				  .setResultTransformer(Transformers.aliasToBean(HistoryPayOutTransaction.class))
				  .list();
		
		List<HistoryServerAccountTransaction> results = resultWithAliasedBean;
		session.close();
		
		return new ArrayList<HistoryServerAccountTransaction>(results);
	}
	
	/**
	 * 
	 * @param url
	 * @return
	 * @throws ServerAccountNotFoundException
	 */
	public static ArrayList<HistoryServerAccountTransaction> getLast3ServerAccountTransaction(String url) throws ServerAccountNotFoundException{
		Session session = openSession();
		session.beginTransaction();
		ServerAccount serverAccount = (ServerAccount) session.createCriteria(ServerAccount.class).add(Restrictions.eq("url", url)).uniqueResult();
		session.close();
		if(serverAccount == null)
			throw new ServerAccountNotFoundException(url);
		
		Session session2 = openSession();
		session2.beginTransaction();
		@SuppressWarnings("unchecked")
		List<HistoryServerAccountTransaction> resultWithAliasedBean = session.createSQLQuery(
				  "SELECT st.timestamp, st.amount, st.payin_address as btcAddress " +
				  "FROM server_transaction st " +
				  "WHERE st.user_id = :userid " +
				  "ORDER BY st.timestamp DESC")
				  .addScalar("timestamp")
				  .addScalar("amount")
				  .addScalar("btcAddress")
				  .setLong("userid",  serverAccount.getId())
				  .setMaxResults(3)
				  .setFetchSize(3)
				  .setResultTransformer(Transformers.aliasToBean(HistoryPayOutTransaction.class))
				  .list();
		
		List<HistoryServerAccountTransaction> results = resultWithAliasedBean;
		session2.close();
		
		return new ArrayList<HistoryServerAccountTransaction>(results);
	}
	
	/**
	 * 
	 * @param page
	 * @return
	 */
	public static ArrayList<HistoryServerAccountTransaction> getHistory(int page){
		if (page < 0)
			return null;

		Session session = openSession();
		session.beginTransaction();
				
		@SuppressWarnings("unchecked")
		List<HistoryServerAccountTransaction> resultWithAliasedBean = session.createSQLQuery(
				  "SELECT st.timestamp, st.amount, st.payin_address as btcAddress " +
				  "FROM server_transaction st " +
				  "ORDER BY pot.timestamp DESC")
				  .addScalar("timestamp")
				  .addScalar("amount")
				  .addScalar("btcAddress")
				  .setFirstResult(page * Config.SEREVER_TRANSACTION_MAX_RESULTS)
				  .setMaxResults(Config.SEREVER_TRANSACTION_MAX_RESULTS)
				  .setFetchSize(Config.SEREVER_TRANSACTION_MAX_RESULTS)
				  .setResultTransformer(Transformers.aliasToBean(HistoryServerAccountTransaction.class))
				  .list();
		
		List<HistoryServerAccountTransaction> results = resultWithAliasedBean;
		session.close();
		
		return new ArrayList<HistoryServerAccountTransaction>(results);
	}
	
	/**
	 * 
	 * @param page
	 * @return
	 */
	public static ArrayList<HistoryServerAccountTransaction> getPayeeHistory(int page){
		if (page < 0)
			return null;

		Session session = openSession();
		session.beginTransaction();
				
		Query query= session.createSQLQuery(
				  "SELECT st.timestamp, st.amount, st.payin_address as btcAddress " +
				  "FROM server_transaction st " +
				  "WHERE st.received = 'TRUE' "+
				  "AND st.verified = 'TRUE' "+ 
				  "ORDER BY st.timestamp DESC")
				  .addScalar("timestamp")
				  .addScalar("amount")
				  .addScalar("btcAddress", StandardBasicTypes.STRING)
				  .setFirstResult(page * Config.SEREVER_TRANSACTION_MAX_RESULTS)
				  .setMaxResults(Config.SEREVER_TRANSACTION_MAX_RESULTS)
				  .setFetchSize(Config.SEREVER_TRANSACTION_MAX_RESULTS)
				  .setResultTransformer(Transformers.aliasToBean(HistoryServerAccountTransaction.class));
		
		@SuppressWarnings("unchecked")
		List<HistoryServerAccountTransaction> resultWithAliasedBean = query.list();
		
		List<HistoryServerAccountTransaction> results = resultWithAliasedBean;
		session.close();
		
		return new ArrayList<HistoryServerAccountTransaction>(results);
	}
	
	public static ArrayList<HistoryServerAccountTransaction> getPayerHistory(int page){
		if (page < 0)
			return null;

		Session session = openSession();
		session.beginTransaction();
				
		Query query= session.createSQLQuery(
				  "SELECT st.timestamp, st.amount, st.payin_address as btcAddress " +
				  "FROM server_transaction st " +
				  "WHERE st.received = 'FALSE' "+
				  "AND st.verified = 'TRUE' "+ 
				  "ORDER BY st.timestamp DESC")
				  .addScalar("timestamp")
				  .addScalar("amount")
				  .addScalar("btcAddress", StandardBasicTypes.STRING)
				  .setFirstResult(page * Config.SEREVER_TRANSACTION_MAX_RESULTS)
				  .setMaxResults(Config.SEREVER_TRANSACTION_MAX_RESULTS)
				  .setFetchSize(Config.SEREVER_TRANSACTION_MAX_RESULTS)
				  .setResultTransformer(Transformers.aliasToBean(HistoryServerAccountTransaction.class));
		
		@SuppressWarnings("unchecked")
		List<HistoryServerAccountTransaction> resultWithAliasedBean = query.list();
		
		List<HistoryServerAccountTransaction> results = resultWithAliasedBean;
		session.close();
		
		return new ArrayList<HistoryServerAccountTransaction>(results);
	}
}
