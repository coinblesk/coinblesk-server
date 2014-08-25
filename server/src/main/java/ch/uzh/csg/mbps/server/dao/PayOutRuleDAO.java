package ch.uzh.csg.mbps.server.dao;

import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;

import org.springframework.stereotype.Repository;

import ch.uzh.csg.mbps.server.domain.PayOutRule;
import ch.uzh.csg.mbps.server.domain.UserAccount;
import ch.uzh.csg.mbps.server.util.exceptions.PayOutRuleNotFoundException;

/**
 * DatabaseAccessObject for {@link PayOutRule}s. Handles all DB operations
 * regarding {@link PayOutRule}s.
 * 
 */
@Repository
public class PayOutRuleDAO {
	
	@PersistenceContext
	private EntityManager em;
	
	private static PayOutRule transform(ch.uzh.csg.mbps.model.PayOutRule por) {
		PayOutRule por2 = new PayOutRule();
		por2.setBalanceLimit(por.getBalanceLimitBTC());
		if(por.getDay() != null) {
			por2.setDay(por.getDay());
		}
		if(por.getHour() != null) {
			por2.setHour(por.getHour());
		}
		por2.setPayoutAddress(por.getPayoutAddress());
		por2.setUserId(por.getUserId());
		return por2;
	}
	
	/**
	 * Saves a new {@link PayOutRule} object in the database. 
	 * @param list with {@link PayOutRule}
	 */
	public void createPayOutRules(List<ch.uzh.csg.mbps.model.PayOutRule> list) {
		for(ch.uzh.csg.mbps.model.PayOutRule po:list) {
			em.persist(transform(po));
		}
	}
	
	/**
	 * Returns ArrayList with all {@link PayOutRule}s assigned to given userId. Throws
	 * {@link PayOutRuleNotFoundException} if no {@link PayOutRule}s are assigned to given
	 * {@link UserAccount}.
	 * 
	 * @param userId
	 * @return ArrayList<PayOutRule>
	 * @throws PayOutRuleNotFoundException
	 */
	public List<PayOutRule> getByUserId(long userId) throws PayOutRuleNotFoundException {
		CriteriaBuilder cb = em.getCriteriaBuilder();
		CriteriaQuery<PayOutRule> cq = cb.createQuery(PayOutRule.class);
		Root<PayOutRule> root = cq.from(PayOutRule.class);
		
		Predicate condition = cb.equal(root.get("userId"), userId);
		cq.where(condition);
		
		List<PayOutRule> por = em.createQuery(cq).getResultList();
	
		if(por == null || por.isEmpty()) {
			throw new PayOutRuleNotFoundException();
		}
		
		return por;
	}

	/**
	 * Deletes all {@link PayOutRule}s assigned to {@link UserAccount} defined by parameter
	 * userId.
	 * 
	 * @param userId
	 */
	public void deleteRules(long userId) {
		String hql = "delete from PAYOUT_RULES where userId= :userId";
		em.createQuery(hql).setParameter("userId", userId).executeUpdate();
	}

	/**
	 * Returns a list with all {@link PayOutRule}s which define a {@link PayOutRule} for defined
	 * hour and day. Throws {@link PayOutRuleNotFoundException} if no rule is defined
	 * for defined hour and time.
	 * 
	 * @param hour of day (0-23)
	 * @param day of week (SO 1 -SA 7)
	 * @return List<PayOutRule>
	 * @throws PayOutRuleNotFoundException
	 */
	public List<PayOutRule> get(int hour, int day) throws PayOutRuleNotFoundException {
		
		CriteriaBuilder cb = em.getCriteriaBuilder();
		CriteriaQuery<PayOutRule> cq = cb.createQuery(PayOutRule.class);
		Root<PayOutRule> root = cq.from(PayOutRule.class);
		
		Predicate condition1 = cb.equal(root.get("hour"), hour);
		Predicate condition2 = cb.equal(root.get("day"), day);
		Predicate condition3 = cb.and(condition1, condition2);
		cq.where(condition3);
		
		List<PayOutRule> por = em.createQuery(cq).getResultList();
		
		if (por == null || por.isEmpty()) {
			throw new PayOutRuleNotFoundException();
		}
			
		return por;
	}
}
