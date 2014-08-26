package ch.uzh.csg.mbps.server.dao;

import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;

import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import ch.uzh.csg.mbps.server.domain.ServerAccount;
import ch.uzh.csg.mbps.server.domain.ServerTransaction;
import ch.uzh.csg.mbps.server.util.Config;
import ch.uzh.csg.mbps.server.util.exceptions.ServerAccountNotFoundException;
import ch.uzh.csg.mbps.server.util.exceptions.TransactionException;
import ch.uzh.csg.mbps.server.util.web.model.HistoryServerAccountTransaction;
import ch.uzh.csg.mbps.util.Converter;

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
		ServerAccount serverAccount = serverAccountDAO.getByUrl(serverTransaction.getServerUrl());
		if(serverTransaction.isReceived()){			
			serverAccount.setActiveBalance(serverAccount.getActiveBalance().subtract(serverTransaction.getAmount()));
		}else{
			serverAccount.setActiveBalance(serverAccount.getActiveBalance().add(serverTransaction.getAmount()));			
		}
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
		ServerTransaction existingServerTransaction = ServerAccountDAO.getSingle(cq, em);
		
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
				  "WHERE st.server_url = :serverUrl")
				  .setParameter("serverUrl", url)
				  .getSingleResult())
				  .longValue();

		return nofResults;
	}

	/**
	 * 
	 * @param page
	 * @return
	 */
	public List<HistoryServerAccountTransaction> getHistory(int page){
		if (page < 0) {
			return null;
		}
		
		@SuppressWarnings("unchecked")
        List<HistoryServerAccountTransaction> resultWithAliasedBean = em.createQuery(
				  "SELECT NEW ch.uzh.csg.mbps.server.util.web.model.HistoryServerAccountTransaction(st.timestamp, st.amount, st.server_url, st.received) "
				+ "FROM server_transaction st "
				+ "ORDER BY st.timestamp DESC")
				.setFirstResult(page * Config.TRANSACTIONS_MAX_RESULTS)
				.setMaxResults(Config.TRANSACTIONS_MAX_RESULTS)
				.getResultList();
		
		return resultWithAliasedBean;
	}
	
	/**
	 * Checks if a ServerTransaction with the given parameters does already exist.
	 * 
	 * @param url the other server's url
	 * @param amount the amount
	 * @param timestamp the other server's timestamp
	 * @return
	 */
	public boolean exists(String url, long amount, long timestamp) {
		CriteriaBuilder cb = em.getCriteriaBuilder();
		CriteriaQuery<Long> cq = cb.createQuery(Long.class);
		
		Root<ServerTransaction> root = cq.from(ServerTransaction.class);
		cq.select(cb.count(root));
				
		Predicate condition1 = cb.equal(root.get("serverUrl"), url);
		Predicate condition2 = cb.equal(root.get("amount"), Converter.getBigDecimalFromLong(amount));
		Predicate condition3= cb.equal(root.get("timestamp"), timestamp);
		
		Predicate condition4 = cb.and(condition1, condition2, condition3);
		
		cq.where(condition4);
		Long count = em.createQuery(cq).getSingleResult();
		
		return count > 0;
	}
	
	/**
	 * 
	 * @return
	 */
	public List<HistoryServerAccountTransaction> getLast5Transactions(){
		
		@SuppressWarnings("unchecked")
        List<HistoryServerAccountTransaction> resultWithAliasedBean = em.createQuery(
				  "SELECT NEW ch.uzh.csg.mbps.server.util.web.model.HistoryServerAccountTransaction(st.timestamp, st.amount, st.server_url, st.received) "
				+ "FROM server_transaction st "
				+ "ORDER BY st.timestamp DESC")
				.setMaxResults(5)
				.getResultList();
		
		return resultWithAliasedBean;
	}
	
	/**
	 * 
	 * @param url
	 * @return
	 * @throws ServerAccountNotFoundException
	 */
	public List<HistoryServerAccountTransaction> getLast5ServerAccountTransaction(String url) throws ServerAccountNotFoundException{
		
		CriteriaBuilder cb = em.getCriteriaBuilder();
		CriteriaQuery<ServerTransaction> cq = cb.createQuery(ServerTransaction.class);
		Root<ServerTransaction> root = cq.from(ServerTransaction.class);
		Predicate condition = cb.equal(root.get("url"), url);
		cq.where(condition);
		ServerTransaction existingServerTransaction = em.createQuery(cq).getSingleResult();

		if(existingServerTransaction == null)
			throw new ServerAccountNotFoundException(url);
		
		@SuppressWarnings("unchecked")
        List<HistoryServerAccountTransaction> resultWithAliasedBean = em.createQuery(
				  "SELECT NEW ch.uzh.csg.mbps.server.util.web.model.HistoryServerAccountTransaction(st.timestamp, st.amount, st.server_url, st.received) "
				+ "FROM server_transaction st "
				+ "WHERE st.server_url = :serverUrl "
				+ "ORDER BY st.timestamp DESC")
				.setParameter("serverUrl", url)
				.setMaxResults(5)
				.getResultList();
		
		return resultWithAliasedBean;
	}
	
	
	/**
	 * 
	 * @param page
	 * @return
	 */
	public List<HistoryServerAccountTransaction> getPayeeHistory(int page){
		if (page < 0) {
			return null;
		}

		@SuppressWarnings("unchecked")
        List<HistoryServerAccountTransaction> resultWithAliasedBean = em.createQuery(
				  "SELECT NEW ch.uzh.csg.mbps.server.util.web.model.HistoryServerAccountTransaction(st.timestamp, st.amount, st.server_url, st.received) "
				+ "FROM server_transaction st "
				+ "WHERE (st.received = :received and st.verified = :verified) "
				+ "ORDER BY st.timestamp DESC")
				.setParameter("received", true)
				.setParameter("verified", true)
				.setMaxResults(5)
				.getResultList();
		
		return resultWithAliasedBean;
	}
	
	public List<HistoryServerAccountTransaction> getPayerHistory(int page){
		if (page < 0) {
			return null;
		}
		
		@SuppressWarnings("unchecked")
        List<HistoryServerAccountTransaction> resultWithAliasedBean = em.createQuery(
				  "SELECT NEW ch.uzh.csg.mbps.server.util.web.model.HistoryServerAccountTransaction(st.timestamp, st.amount, st.server_url, st.received) "
				+ "FROM server_transaction st "
				+ "WHERE (st.received = :received and st.verified = :verified) "
				+ "ORDER BY st.timestamp DESC")
				.setParameter("received", false)
				.setParameter("verified", true)
				.setMaxResults(5)
				.getResultList();
		
		return resultWithAliasedBean;
	}
	
	public List<HistoryServerAccountTransaction> getServerAccountTransactions(String url, int page) throws ServerAccountNotFoundException {
		if (page < 0)
			return null;
		
		CriteriaBuilder cb = em.getCriteriaBuilder();
		CriteriaQuery<ServerTransaction> cq = cb.createQuery(ServerTransaction.class);
		Root<ServerTransaction> root = cq.from(ServerTransaction.class);
		Predicate condition = cb.equal(root.get("url"), url);
		cq.where(condition);
		ServerTransaction existingServerTransaction = em.createQuery(cq).getSingleResult();

		if(existingServerTransaction == null)
			throw new ServerAccountNotFoundException(url);
		
		@SuppressWarnings("unchecked")
        List<HistoryServerAccountTransaction> resultWithAliasedBean = em.createQuery(
				  "SELECT NEW ch.uzh.csg.mbps.server.util.web.model.HistoryServerAccountTransaction(st.timestamp, st.amount, st.server_url, st.received) "
				+ "FROM server_transaction st "
				+ "WHERE st.server_url = :serverUrl "
				+ "ORDER BY st.timestamp DESC")
				.setParameter("serverUrl", url)
				.setFirstResult(page * Config.TRANSACTIONS_MAX_RESULTS)
				.setMaxResults(Config.TRANSACTIONS_MAX_RESULTS)
				.getResultList();
		
		return resultWithAliasedBean;
	}
}
