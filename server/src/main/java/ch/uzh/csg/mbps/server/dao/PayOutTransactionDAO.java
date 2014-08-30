package ch.uzh.csg.mbps.server.dao;

import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;

import org.apache.log4j.Logger;
import org.hibernate.HibernateException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import ch.uzh.csg.mbps.model.HistoryPayOutTransaction;
import ch.uzh.csg.mbps.server.domain.PayOutTransaction;
import ch.uzh.csg.mbps.server.domain.UserAccount;
import ch.uzh.csg.mbps.server.util.Config;
import ch.uzh.csg.mbps.server.util.exceptions.TransactionException;
import ch.uzh.csg.mbps.server.util.exceptions.UserAccountNotFoundException;

/**
 * DatabaseAccessObject for {@link PayOutTransaction}s. Handles all DB operations
 * regarding {@link PayOutTransaction}s.
 * 
 */
@Repository
public class PayOutTransactionDAO {
	private static Logger LOGGER = Logger.getLogger(PayOutTransactionDAO.class);
	
	@PersistenceContext
	private EntityManager em;
	
	@Autowired
	private UserAccountDAO userAccountDAO;
	
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
	public List<HistoryPayOutTransaction> getHistory(UserAccount userAccount, int page) throws UserAccountNotFoundException {
		if (page < 0) {
			return null;
		}
		
		@SuppressWarnings("unchecked")
        List<HistoryPayOutTransaction> resultWithAliasedBean = em.createQuery(""
				+ "SELECT NEW ch.uzh.csg.mbps.model.HistoryPayOutTransaction(pot.timestamp,  pot.amount, pot.btcAddress) "
				+ "FROM PayOutTransaction pot "
				+ "WHERE pot.userID = :userid "
				+ "ORDER BY pot.timestamp DESC")
				.setFirstResult(page * Config.PAY_INS_MAX_RESULTS)
				.setMaxResults(Config.PAY_INS_MAX_RESULTS)
				.setParameter("userid", userAccount.getId())
				.getResultList();
		
		return resultWithAliasedBean;
	}

	/**
	 * Counts number of {@link PayOutTransaction}-entries for given username and returns
	 * number as long.
	 * 
	 * @param username
	 * @return number of PayOutTransaction
	 * @throws UserAccountNotFoundException if no POTs are assigned to given UserAccount
	 */
	public long getHistoryCount(UserAccount userAccount) throws UserAccountNotFoundException {
		CriteriaBuilder cb = em.getCriteriaBuilder();
		CriteriaQuery<Long> cq = cb.createQuery(Long.class);
		Root<PayOutTransaction> root = cq.from(PayOutTransaction.class);
		cq.select(cb.count(root));
		
		Predicate condition = cb.equal(root.get("userID"), userAccount.getId());
		cq.where(condition);
		return em.createQuery(cq).getSingleResult();
	}

	/**
	 * Saves a new {@link PayOutTransaction} in the database.
	 * 
	 * @param pot PayOutTransaction to save
	 * @throws UserAccountNotFoundException if defined UserAccount is not found
	 * @throws HibernateException
	 */
	public void createPayOutTransaction(PayOutTransaction pot) throws UserAccountNotFoundException {
		em.persist(pot);
		//update UserAccountBalance
		UserAccount userAccount = userAccountDAO.getById(pot.getUserID());
		userAccount.setBalance(userAccount.getBalance().subtract(pot.getAmount()));
		em.merge(userAccount);
		LOGGER.info("PayOutTransaction created for UserAccount with ID: " + userAccount.getId() + " Transaction: " + pot.toString());
	}

	/**
	 * Verifies a {@link PayOutTransaction} in the DB.
	 * 
	 * @param pot PayOutTransation to be verified.
	 * @throws TransactionException if PayOutTransaction with specified TransactionId does not exist in the database.
	 * @throws HibernateException
	 */
	public void verify(PayOutTransaction pot) throws TransactionException {
		
		
		CriteriaBuilder cb = em.getCriteriaBuilder();
		CriteriaQuery<PayOutTransaction> cq = cb.createQuery(PayOutTransaction.class);
		Root<PayOutTransaction> root = cq.from(PayOutTransaction.class);
		
		//TODO: no check for userid?
		Predicate condition = cb.equal(root.get("transactionID"), pot.getTransactionID());
		cq.where(condition);
		
		PayOutTransaction existingPOT = UserAccountDAO.getSingle(cq, em);
		
		if(existingPOT == null){
			throw new TransactionException("Problem verifying PayOutTransaction. This transaction does not exists.");
		}
				
		if(!existingPOT.isVerified()){
			existingPOT.setVerified(true);
			em.merge(existingPOT);
			LOGGER.info("Successfully verified PayOutTransaction with ID: " + existingPOT.getId());
		}
	}
	
	/**
	 * Returns history of 5 newest {@link PayOutTransaction}s assigned to the
	 * given username as an ArrayList.
	 * 
	 * @param username
	 *            for which UserAccount history is requested
	 * @return ArrayList<HistoryPayOutTransaction> (an array list with the last
	 *         5 PayOutTransactions)
	 * @throws UserAccountNotFoundException
	 */
	public List<HistoryPayOutTransaction> getLast5Transactions(UserAccount userAccount) throws UserAccountNotFoundException {
		
		@SuppressWarnings("unchecked")
        List<HistoryPayOutTransaction> resultWithAliasedBean = em.createQuery(""
				+ "SELECT NEW ch.uzh.csg.mbps.model.HistoryPayOutTransaction(pot.timestamp,  pot.amount, pot.btcAddress) "
				+ "FROM PayOutTransaction pot "
				+ "WHERE pot.userID = :userid "
				+ "ORDER BY pot.timestamp DESC")
				.setMaxResults(5)
				.setParameter("userid", userAccount.getId())
				.getResultList();
		
		return resultWithAliasedBean;
	}
	
}
