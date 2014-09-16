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
import ch.uzh.csg.mbps.server.web.model.HistoryServerAccountTransaction;
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


	/**
	 * Creates a {@link ServerTransaction}
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
	 * Verifies the transaction.
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
	 * Gets the number of {@link ServerTransaction}s.
	 * 
	 * @return long number of transactions
	 */
	public long getHistoryCount(){
		CriteriaBuilder cb = em.getCriteriaBuilder();
		CriteriaQuery<Long> cq = cb.createQuery(Long.class);
		Root<ServerTransaction> root = cq.from(ServerTransaction.class);
		cq.select(cb.count(root));
		
		long nofResults = em.createQuery(cq).getSingleResult().longValue();	
		return nofResults;	
	}
	
	/**
	 * Returns the number {@link ServerTransaction} given by the parameter url
	 * 
	 * @param url
	 * @return long number of transactions
	 */
	public long getServerAccountHistoryCount(String url){
		CriteriaBuilder cb = em.getCriteriaBuilder();
		CriteriaQuery<Long> cq = cb.createQuery(Long.class);
		Root<ServerTransaction> root = cq.from(ServerTransaction.class);
		cq.select(cb.count(root));
		Predicate condition = cb.equal(root.get("serverUrl"), url);
		cq.where(condition);
		long nofResults = em.createQuery(cq).getSingleResult();

		return nofResults;
	}

	/**
	 * Returns all {@link ServerTransaction}s
	 * 
	 * @param page number
	 * @return List of Server Transactions
	 */
	public List<HistoryServerAccountTransaction> getHistory(int page){
		if (page < 0) {
			return null;
		}
		
		CriteriaBuilder cb = em.getCriteriaBuilder();
		CriteriaQuery<HistoryServerAccountTransaction> cq = cb.createQuery(HistoryServerAccountTransaction.class);
		Root<ServerTransaction> root = cq.from(ServerTransaction.class);
		cq.select(cb.construct(HistoryServerAccountTransaction.class, root.get("timestamp"),root.get("amount"), 
				root.get("serverUrl"), root.get("received"), root.get("verified"), root.get("transactionID")));
		
		cq.orderBy(cb.desc(root.get("timestamp")));
		List<HistoryServerAccountTransaction> resultWithAliasedBean = em.createQuery(cq)
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
		Predicate condition3 = cb.equal(root.get("timestamp"), timestamp);
		
		Predicate condition4 = cb.and(condition1, condition2, condition3);
		
		cq.where(condition4);
		Long count = em.createQuery(cq).getSingleResult();
		
		return count > 0;
	}
	
	/**
	 * Returns the last 5 made transactions. All are verified.
	 * 
	 * @return Returns last 5 verified Transaction
	 */
	public List<HistoryServerAccountTransaction> getLast5Transactions(){
		CriteriaBuilder cb = em.getCriteriaBuilder();
		CriteriaQuery<HistoryServerAccountTransaction> cq = cb.createQuery(HistoryServerAccountTransaction.class);
		Root<ServerTransaction> root = cq.from(ServerTransaction.class);
		cq.select(cb.construct(HistoryServerAccountTransaction.class, root.get("timestamp"),
				root.get("amount"), root.get("serverUrl"), root.get("received"), root.get("verified"), root.get("transactionID")));
		
		Predicate condition = cb.equal(root.get("verified"), true);
		cq.where(condition);
		cq.orderBy(cb.desc(root.get("timestamp")));
		List<HistoryServerAccountTransaction> resultWithAliasedBean = em.createQuery(cq)
				.setMaxResults(5)
				.getResultList();

		return resultWithAliasedBean;
	}
	
	/**
	 * Returns the last 5 made transactions of a Server Account.
	 * 
	 * @param url
	 * @return Returns last 5 Server Account Transaction
	 * @throws ServerAccountNotFoundException
	 */
	public List<HistoryServerAccountTransaction> getLast5ServerAccountTransaction(String url) throws ServerAccountNotFoundException{
		CriteriaBuilder cb = em.getCriteriaBuilder();
		CriteriaQuery<HistoryServerAccountTransaction> cq = cb.createQuery(HistoryServerAccountTransaction.class);
		Root<ServerTransaction> root = cq.from(ServerTransaction.class);
		cq.select(cb.construct(HistoryServerAccountTransaction.class, root.get("timestamp"),
				root.get("amount"), root.get("serverUrl"), root.get("received"), root.get("verified"), root.get("transactionID")));
		
		Predicate condition = cb.equal(root.get("serverUrl"), url);
		cq.where(condition);
		cq.orderBy(cb.desc(root.get("timestamp")));
		List<HistoryServerAccountTransaction> resultWithAliasedBean = em.createQuery(cq).setMaxResults(5).getResultList();

		if(resultWithAliasedBean == null)
			throw new ServerAccountNotFoundException(url);
		
		return resultWithAliasedBean;
	}
	
	/**
	 * Returns the {@link ServerTransaction}s as payee.
	 * 
	 * @param page Number of the page
	 * @return List of Transactions
	 */
	public List<HistoryServerAccountTransaction> getPayeeHistory(int page){
		if (page < 0) {
			return null;
		}

		CriteriaBuilder cb = em.getCriteriaBuilder();
		CriteriaQuery<HistoryServerAccountTransaction> cq = cb.createQuery(HistoryServerAccountTransaction.class);
		Root<ServerTransaction> root = cq.from(ServerTransaction.class);
		cq.select(cb.construct(HistoryServerAccountTransaction.class, root.get("timestamp"),
				root.get("amount"), root.get("serverUrl"), root.get("received"), root.get("verified"), root.get("transactionID")));
		
		Predicate condition1 = cb.equal(root.get("received"), true);
		Predicate condition2 = cb.equal(root.get("verified"), true);
		Predicate condition3 = cb.and(condition1, condition2);
		cq.where(condition3);
		cq.orderBy(cb.desc(root.get("timestamp")));
		
		List<HistoryServerAccountTransaction> resultWithAliasedBean = em.createQuery(cq)
				.setFirstResult(page * Config.TRANSACTIONS_MAX_RESULTS)
				.setMaxResults(Config.TRANSACTIONS_MAX_RESULTS)
				.getResultList();
		
		return resultWithAliasedBean;
	}
	
	/**
	 * Returns the {@link ServerTransaction}s as payer.
	 * 
	 * @param page Number of the page
	 * @return List of Transactions
	 */
	public List<HistoryServerAccountTransaction> getPayerHistory(int page){
		if (page < 0) {
			return null;
		}

		CriteriaBuilder cb = em.getCriteriaBuilder();
		CriteriaQuery<HistoryServerAccountTransaction> cq = cb.createQuery(HistoryServerAccountTransaction.class);
		Root<ServerTransaction> root = cq.from(ServerTransaction.class);
		cq.select(cb.construct(HistoryServerAccountTransaction.class, root.get("timestamp"),
				root.get("amount"), root.get("serverUrl"), root.get("received"), root.get("verified"), root.get("transactionID")));
		
		Predicate condition1 = cb.equal(root.get("received"), false);
		Predicate condition2 = cb.equal(root.get("verified"), true);
		Predicate condition3 = cb.and(condition1, condition2);
		cq.where(condition3);
		cq.orderBy(cb.desc(root.get("timestamp")));
		
		List<HistoryServerAccountTransaction> resultWithAliasedBean = em.createQuery(cq)
				.setFirstResult(page * Config.TRANSACTIONS_MAX_RESULTS)
				.setMaxResults(Config.TRANSACTIONS_MAX_RESULTS)
				.getResultList();
		
		return resultWithAliasedBean;
	}
	
	/**
	 * Returns all {@link ServerTransaction} of a given parameter url.
	 * 
	 * @param url
	 * @param page Number of the page
	 * @return List of {@link ServerTransaction}
	 * @throws ServerAccountNotFoundException
	 */
	public List<HistoryServerAccountTransaction> getServerAccountTransactions(String url, int page) throws ServerAccountNotFoundException {
		if (page < 0)
			return null;

		CriteriaBuilder cb = em.getCriteriaBuilder();
		CriteriaQuery<HistoryServerAccountTransaction> cq = cb.createQuery(HistoryServerAccountTransaction.class);
		Root<ServerTransaction> root = cq.from(ServerTransaction.class);
		cq.select(cb.construct(HistoryServerAccountTransaction.class, root.get("timestamp"),
				root.get("amount"), root.get("serverUrl"), root.get("received"), root.get("verified"), root.get("transactionID")));
		
		Predicate condition = cb.equal(root.get("serverUrl"), url);
		cq.where(condition);
		cq.orderBy(cb.desc(root.get("timestamp")));
		List<HistoryServerAccountTransaction> resultWithAliasedBean = em.createQuery(cq).setFirstResult(page*Config.TRANSACTIONS_MAX_RESULTS).setMaxResults(Config.TRANSACTIONS_MAX_RESULTS).getResultList();

		if(resultWithAliasedBean == null)
			throw new ServerAccountNotFoundException(url);
		
		return resultWithAliasedBean;
	}
}
