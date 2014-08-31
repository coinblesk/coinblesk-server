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

import ch.uzh.csg.mbps.model.HistoryPayInTransaction;
import ch.uzh.csg.mbps.server.domain.PayInTransaction;
import ch.uzh.csg.mbps.server.domain.PayInTransactionUnverified;
import ch.uzh.csg.mbps.server.domain.UserAccount;
import ch.uzh.csg.mbps.server.util.Config;
import ch.uzh.csg.mbps.server.util.exceptions.UserAccountNotFoundException;

@Repository
public class PayInTransactionUnverifiedDAO {
	
	private static Logger LOGGER = Logger.getLogger(PayInTransactionUnverifiedDAO.class);
	
	@PersistenceContext
	private EntityManager em;
	
	@Autowired
	private UserAccountDAO userAccountDAO;

	public void remove(PayInTransaction pit) {
		String hql = "DELETE FROM PayInTransactionUnverified WHERE userID = :userID and transactionID = :transactionID";
		em.createQuery(hql)
			.setParameter("userID", pit.getUserID())
			.setParameter("transactionID", pit.getTransactionID())
			.executeUpdate();
	    LOGGER.debug("removed unverified transaction, as it is now veriefed for user "+pit.getUserID()+" / "+pit.getTransactionID());
    }

	public boolean isNew(PayInTransactionUnverified pit) {
		CriteriaBuilder cb = em.getCriteriaBuilder();
		CriteriaQuery<Long> cq = cb.createQuery(Long.class);
		
		Root<PayInTransactionUnverified> root = cq.from(PayInTransactionUnverified.class);
		cq.select(cb.count(root));
		
		Predicate condition1 = cb.equal(root.get("userID"), pit.getUserID());
		Predicate condition2 = cb.equal(root.get("transactionID"), pit.getTransactionID());
		Predicate condition3 = cb.and(condition1, condition2);
		cq.where(condition3);
		
		Long count = em.createQuery(cq).getSingleResult();
		
		return count == 0;
    }

	public void createPayInTransaction(PayInTransactionUnverified tx) {
		em.persist(tx);
    }

	public List<HistoryPayInTransaction> getHistory(String username, int page) throws UserAccountNotFoundException {
		if (page < 0) {
			return null;
		}
		
		UserAccount userAccount = userAccountDAO.getByUsername(username);
		
		@SuppressWarnings("unchecked")
        List<HistoryPayInTransaction> resultWithAliasedBean = em.createQuery(""
				+ "SELECT NEW ch.uzh.csg.mbps.model.HistoryPayInTransaction(pit.timestamp,  pit.amount) "
				+ "FROM PayInTransactionUnverified pit "
				+ "WHERE pit.userID = :userid "
				+ "ORDER BY pit.timestamp DESC")
				.setFirstResult(page * Config.PAY_INS_MAX_RESULTS)
				.setMaxResults(Config.PAY_INS_MAX_RESULTS)
				.setParameter("userid", userAccount.getId())
				.getResultList();
		
		return resultWithAliasedBean;
    }

	public long getHistoryCount(UserAccount userAccount) {
		CriteriaBuilder cb = em.getCriteriaBuilder();
		CriteriaQuery<Long> cq = cb.createQuery(Long.class);
		Root<PayInTransactionUnverified> root = cq.from(PayInTransactionUnverified.class);
		cq.select(cb.count(root));
		
		Predicate condition = cb.equal(root.get("userID"), userAccount.getId());
		cq.where(condition);
		return em.createQuery(cq).getSingleResult();
    }

	public List<HistoryPayInTransaction> getLast5Transactions(UserAccount userAccount) {
		@SuppressWarnings("unchecked")
        List<HistoryPayInTransaction> resultWithAliasedBean = em.createQuery(""
				+ "SELECT NEW ch.uzh.csg.mbps.model.HistoryPayInTransaction(pit.timestamp,  pit.amount) "
				+ "FROM PayInTransactionUnverified pit "
				+ "WHERE pit.userID = :userid "
				+ "ORDER BY pit.timestamp DESC")
				.setMaxResults(5)
				.setParameter("userid", userAccount.getId())
				.getResultList();
		
		return resultWithAliasedBean;
    }

}
