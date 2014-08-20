package ch.uzh.csg.mbps.server.dao;

import java.util.ArrayList;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;

import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import ch.uzh.csg.mbps.model.HistoryServerAccountTransaction;
import ch.uzh.csg.mbps.server.domain.ServerAccount;
import ch.uzh.csg.mbps.server.domain.ServerTransaction;
import ch.uzh.csg.mbps.server.util.exceptions.ServerAccountNotFoundException;
import ch.uzh.csg.mbps.server.util.exceptions.TransactionException;

/**
 * DatabaseAccessObject for {@link ServerTransaction}s. Handles all DB operations
 * regarding {@link ServerTransaction}s.
 * 
 */
@Repository
public class ServerTransactionDAO {
	
	
	private static Logger LOGGER = Logger.getLogger(ServerTransactionDAO.class);
	
	@Autowired
	private ServerAccountDAO serverAccountDAO;
	
	@PersistenceContext
	private EntityManager em;

	//TODO: mehmet: javadoc
	/**
	 * 
	 * @param serverTransaction
	 * @throws ServerAccountNotFoundException
	 */
	public void createServerTransaction(ServerTransaction serverTransaction) throws ServerAccountNotFoundException{
		em.persist(serverTransaction);
		ServerAccount serverAccount = serverAccountDAO.getByPayinAddress(serverTransaction.getPayinAddress());
		serverAccount.setActiveBalance(serverAccount.getActiveBalance().subtract(serverTransaction.getAmount()));
		em.merge(serverAccount);
		LOGGER.info("ServerTransaction is created for ServerAccount with ID: " +  serverAccount.getId() + " Transaction: " + serverTransaction);
	}
	
	/**
	 * 
	 * @param serverTransaction
	 * @throws TransactionException
	 */
	public void verifyTransaction(ServerTransaction serverTransaction) throws TransactionException{
		
		CriteriaBuilder cb = em.getCriteriaBuilder();
		CriteriaQuery<ServerTransaction> cq = cb.createQuery(ServerTransaction.class);
		Root<ServerTransaction> root = cq.from(ServerTransaction.class);
		Predicate condition = cb.equal(root.get("transactionID"), serverTransaction.getTransactionID());
		cq.where(condition);
		ServerTransaction existingServerTransaction = UserAccountDAO.getSingle(cq, em);
		
		if(existingServerTransaction == null) {
			throw new TransactionException("Problem verifying ServerTransaction. This transaction does not exists.");
		}
		
		if(!existingServerTransaction.isVerified()){
			existingServerTransaction.setVerified(true);
			em.merge(existingServerTransaction);
			LOGGER.info("Successfully verified ServerTransaction with ID: " + existingServerTransaction.getId());
			
		}
				
	}
	
	/**
	 * 
	 * @return
	 */
	public long getHistoryCount(){
		
		long nofResults = ((Number) em.createQuery(
				"SELECT COUNT(*) " +
				"FROM server_transaction st")
				.getSingleResult())
				.longValue();
		
		return nofResults;	
	}
	
	/**
	 * 
	 * @param url
	 * @return
	 */
	public long getServerAccountHistoryCount(String url){
		
		CriteriaBuilder cb = em.getCriteriaBuilder();
		CriteriaQuery<ServerTransaction> cq = cb.createQuery(ServerTransaction.class);
		Root<ServerTransaction> root = cq.from(ServerTransaction.class);
		Predicate condition = cb.equal(root.get("url"), url);
		cq.where(condition);
		ServerTransaction existingServerTransaction = em.createQuery(cq).getSingleResult();
		
		if(existingServerTransaction == null) {
			return 0;
		}
		
		long nofResults = ((Number) em.createQuery(
				  "SELECT COUNT(*) " +
				  "FROM server_transaction st " +
				  "WHERE st.user_id = :userid")
				  .setParameter("userid", existingServerTransaction.getId())
				  .getSingleResult())
				  .longValue();

		return nofResults;
	}
	
	/**
	 * 
	 * @return
	 */
	public ArrayList<HistoryServerAccountTransaction> getLast3Transactions(){
		
		
		/*Query query = session.createSQLQuery(
				  "SELECT st.timestamp, st.amount, st.payin_address as payinAddress " +
				  "FROM server_transaction st " +
				  "ORDER BY st.timestamp DESC")
				  .addScalar("timestamp")
				  .addScalar("amount")
				  .addScalar("payinAddress")
				  .setMaxResults(3)
				  .setFetchSize(3)
				  .setResultTransformer(Transformers.aliasToBean(HistoryServerAccountTransaction.class));
		
		@SuppressWarnings("unchecked")
		List<HistoryServerAccountTransaction> resultWithAliasedBean = query.list();
		
		List<HistoryServerAccountTransaction> results = resultWithAliasedBean;*/
		
		//TODO: rewrite with JPA, not Hibernate!
		
		
		return new ArrayList<HistoryServerAccountTransaction>();
	}
	
	/**
	 * 
	 * @param url
	 * @return
	 * @throws ServerAccountNotFoundException
	 */
	public ArrayList<HistoryServerAccountTransaction> getLast3ServerAccountTransaction(String url) throws ServerAccountNotFoundException{
		
		
		CriteriaBuilder cb = em.getCriteriaBuilder();
		CriteriaQuery<ServerTransaction> cq = cb.createQuery(ServerTransaction.class);
		Root<ServerTransaction> root = cq.from(ServerTransaction.class);
		Predicate condition = cb.equal(root.get("url"), url);
		cq.where(condition);
		ServerTransaction existingServerTransaction = em.createQuery(cq).getSingleResult();

		if(existingServerTransaction == null)
			throw new ServerAccountNotFoundException(url);
		
		
		/*Query query = session.createSQLQuery(
				  "SELECT st.timestamp, st.amount, st.payin_address as payinAddress " +
				  "FROM server_transaction st " +
				  "WHERE st.user_id = :userid " +
				  "ORDER BY st.timestamp DESC")
				  .addScalar("timestamp")
				  .addScalar("amount")
				  .addScalar("payinAddress")
				  .setLong("userid",  serverAccount.getId())
				  .setMaxResults(3)
				  .setFetchSize(3)
				  .setResultTransformer(Transformers.aliasToBean(HistoryServerAccountTransaction.class));
		
		@SuppressWarnings("unchecked")
		List<HistoryServerAccountTransaction> resultWithAliasedBean = query.list();
		
		List<HistoryServerAccountTransaction> results = resultWithAliasedBean;*/
		//TODO: rewrite with JPA, not Hibernate!
		
		
		return new ArrayList<HistoryServerAccountTransaction>(0);
	}
	
	/**
	 * 
	 * @param page
	 * @return
	 */
	public ArrayList<HistoryServerAccountTransaction> getHistory(int page){
		if (page < 0) {
			return null;
		}

		
				
		/*Query query = session.createSQLQuery(
				  "SELECT st.timestamp, st.amount, st.payin_address as payinAddress " +
				  "FROM server_transaction st " +
				  "ORDER BY st.timestamp DESC")
				  .addScalar("timestamp")
				  .addScalar("amount")
				  .addScalar("payinAddress")
				  .setFirstResult(page * Config.SEREVER_TRANSACTION_MAX_RESULTS)
				  .setMaxResults(Config.SEREVER_TRANSACTION_MAX_RESULTS)
				  .setFetchSize(Config.SEREVER_TRANSACTION_MAX_RESULTS)
				  .setResultTransformer(Transformers.aliasToBean(HistoryServerAccountTransaction.class));
		
		@SuppressWarnings("unchecked")
		List<HistoryServerAccountTransaction> resultWithAliasedBean = query.list();
		
		List<HistoryServerAccountTransaction> results = resultWithAliasedBean;*/
		//TODO: rewrite with JPA, not Hibernate!
		
		return new ArrayList<HistoryServerAccountTransaction>();
	}
	
	/**
	 * 
	 * @param page
	 * @return
	 */
	public ArrayList<HistoryServerAccountTransaction> getPayeeHistory(int page){
		if (page < 0) {
			return null;
		}

		/*Query query= session.createSQLQuery(
				  "SELECT st.timestamp, st.amount, st.payin_address as payinAddress " +
				  "FROM server_transaction st " +
				  "WHERE st.received = 'TRUE' "+
				  "AND st.verified = 'TRUE' "+ 
				  "ORDER BY st.timestamp DESC")
				  .addScalar("timestamp")
				  .addScalar("amount")
				  .addScalar("payinAddress", StandardBasicTypes.STRING)
				  .setFirstResult(page * Config.SEREVER_TRANSACTION_MAX_RESULTS)
				  .setMaxResults(Config.SEREVER_TRANSACTION_MAX_RESULTS)
				  .setFetchSize(Config.SEREVER_TRANSACTION_MAX_RESULTS)
				  .setResultTransformer(Transformers.aliasToBean(HistoryServerAccountTransaction.class));
		
		@SuppressWarnings("unchecked")
		List<HistoryServerAccountTransaction> resultWithAliasedBean = query.list();
		
		List<HistoryServerAccountTransaction> results = resultWithAliasedBean;*/
		//TODO: rewrite with JPA, not Hibernate!
		
		return new ArrayList<HistoryServerAccountTransaction>();
	}
	
	public ArrayList<HistoryServerAccountTransaction> getPayerHistory(int page){
		if (page < 0) {
			return null;
		}

		/*Query query= session.createSQLQuery(
				  "SELECT st.timestamp, st.amount, st.payin_address as payinAddress " +
				  "FROM server_transaction st " +
				  "WHERE st.received = 'FALSE' "+
				  "AND st.verified = 'TRUE' "+ 
				  "ORDER BY st.timestamp DESC")
				  .addScalar("timestamp")
				  .addScalar("amount")
				  .addScalar("payinAddress", StandardBasicTypes.STRING)
				  .setFirstResult(page * Config.SEREVER_TRANSACTION_MAX_RESULTS)
				  .setMaxResults(Config.SEREVER_TRANSACTION_MAX_RESULTS)
				  .setFetchSize(Config.SEREVER_TRANSACTION_MAX_RESULTS)
				  .setResultTransformer(Transformers.aliasToBean(HistoryServerAccountTransaction.class));
		
		@SuppressWarnings("unchecked")
		List<HistoryServerAccountTransaction> resultWithAliasedBean = query.list();
		
		List<HistoryServerAccountTransaction> results = resultWithAliasedBean;*/
		//TODO: rewrite with JPA, not Hibernate!
		
		return new ArrayList<HistoryServerAccountTransaction>();
	}
}
