package ch.uzh.csg.mbps.server.dao;

import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;

import org.springframework.stereotype.Repository;

import ch.uzh.csg.mbps.server.domain.ServerAccount;
import ch.uzh.csg.mbps.server.domain.ServerPayOutRule;
import ch.uzh.csg.mbps.server.util.exceptions.PayOutRuleNotFoundException;
import ch.uzh.csg.mbps.server.util.exceptions.ServerPayOutRuleNotFoundException;

/**
 * DatabaseAccessObject for {@link ServerPayOutRule}s. Handles all DB operations
 * regarding {@link ServerPayOutRule}s.
 * 
 */
@Repository
public class ServerPayOutRuleDAO {
	
	@PersistenceContext
	private EntityManager eManager;

	/**
	 * Saves a new {@link ServerPayOutRule} object in the database.
	 * 
	 * @param list
	 *            with {@link ServerPayOutRule}
	 */
	public void createPayOutRules(List<ServerPayOutRule> list) {
		for(ServerPayOutRule spr:list) {
			eManager.persist(spr);
		}
	}

	/**
	 * Returns List with all {@link ServerPayOutRule}s assigned to given
	 * serverAccountId. Throws {@link PayOutRuleNotFoundException} if no
	 * {@link ServerPayOutRule}s are assigned to given {@link ServerAccount}.
	 * 
	 * @param serverAccountId
	 * @return List<ServerPayOutRule>
	 * @throws PayOutRuleNotFoundException
	 */
	public List<ServerPayOutRule> getByServerAccountId(long serverAccountId) throws ServerPayOutRuleNotFoundException {
		CriteriaBuilder cb = eManager.getCriteriaBuilder();
		CriteriaQuery<ServerPayOutRule> cq = cb.createQuery(ServerPayOutRule.class);
		Root<ServerPayOutRule> root = cq.from(ServerPayOutRule.class);
		
		Predicate condition = cb.equal(root.get("serverAccountId"), serverAccountId);
		cq.where(condition);
		
		List<ServerPayOutRule> spor = eManager.createQuery(cq).getResultList();
	
		if(spor == null || spor.isEmpty()) {
			throw new ServerPayOutRuleNotFoundException();
		}
		
		return spor;
	}

	/**
	 * Deletes all {@link ServerPayOutRule}s assigned to {@link ServerAccount}
	 * defined by parameter serverAccountId.
	 * 
	 * @param serverAccountId
	 */
	public void deleteRules(long serverAccountId) {
		String hql = "delete from SERVER_PAYOUT_RULES where SERVER_ACCOUNT_ID= :serverAccountId";
		eManager.createQuery(hql).setParameter("serverAccountId", serverAccountId).executeUpdate();
	}

	/**
	 * Returns a list with all {@link ServerPayOutRule}s which define a
	 * {@link ServerPayOutRule} for defined hour and day. Throws
	 * {@link PayOutRuleNotFoundException} if no rule is defined for defined
	 * hour and time.
	 * 
	 * @param hour
	 *            of day (0-23)
	 * @param day
	 *            of week (SO 1 -SA 7)
	 * @return List<ServerPayOutRule>
	 * @throws PayOutRuleNotFoundException
	 */
	public List<ServerPayOutRule> get(int hour, int day) throws PayOutRuleNotFoundException {
		CriteriaBuilder cb = eManager.getCriteriaBuilder();
		CriteriaQuery<ServerPayOutRule> cq = cb.createQuery(ServerPayOutRule.class);
		Root<ServerPayOutRule> root = cq.from(ServerPayOutRule.class);
		
		Predicate condition1 = cb.equal(root.get("hour"), hour);
		Predicate condition2 = cb.equal(root.get("day"), day);
		Predicate condition3 = cb.and(condition1, condition2);
		cq.where(condition3);
		
		List<ServerPayOutRule> spor = eManager.createQuery(cq).getResultList();
		
		if (spor == null || spor.isEmpty()) {
			throw new PayOutRuleNotFoundException();
		}
			
		return spor;
	}
}