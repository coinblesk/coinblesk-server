package ch.uzh.csg.mbps.server.dao;

import java.util.ArrayList;
import java.util.List;

import org.hibernate.HibernateException;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.criterion.Restrictions;

import ch.uzh.csg.mbps.server.domain.PayOutRule;
import ch.uzh.csg.mbps.server.domain.UserAccount;
import ch.uzh.csg.mbps.server.util.HibernateUtil;
import ch.uzh.csg.mbps.server.util.exceptions.PayOutRuleNotFoundException;

/**
 * DatabaseAccessObject for {@link PayOutRule}s. Handles all DB operations
 * regarding {@link PayOutRule}s.
 * 
 */
public class PayOutRuleDAO {
	
	private static Session openSession() {
		SessionFactory sessionFactory = HibernateUtil.getSessionFactory();
		return sessionFactory.openSession();
	}
	
	private static PayOutRule transform(ch.uzh.csg.mbps.model.PayOutRule por) {
		PayOutRule por2 = new PayOutRule();
		por2.setBalanceLimit(por.getBalanceLimit());
		por2.setDay(por.getDay());
		por2.setHour(por.getHour());
		por2.setPayoutAddress(por.getPayoutAddress());
		por2.setUserId(por.getUserId());
		return por2;
	}
	
	/**
	 * Saves a new {@link PayOutRule} object in the database. 
	 * @param list with {@link PayOutRule}
	 */
	public static void createPayOutRules(ArrayList<ch.uzh.csg.mbps.model.PayOutRule> list) {
		Session session = null;
		org.hibernate.Transaction transaction = null;
		
		try {
			session = openSession();
			transaction = session.beginTransaction();
			for(int i=0; i<list.size(); i++){
				session.save(transform(list.get(i)));				
			}
			transaction.commit();
		} catch (HibernateException e) {
			 if (transaction != null)
				 transaction.rollback();
			 throw e;
		} finally {
			session.close();
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
	@SuppressWarnings("unchecked")
	public static ArrayList<PayOutRule> getByUserId(long userId) throws PayOutRuleNotFoundException {
		Session session = openSession();
		session.beginTransaction();
		ArrayList<PayOutRule> por = (ArrayList<PayOutRule>) session.createCriteria(PayOutRule.class).add(Restrictions.eq("userId", userId)).list();
		
		session.close();
	
		if(por == null || por.isEmpty())
			throw new PayOutRuleNotFoundException();
		
		return por;
	}

	/**
	 * Deletes all {@link PayOutRule}s assigned to {@link UserAccount} defined by parameter
	 * userId.
	 * 
	 * @param userId
	 */
	public static void deleteRules(long userId) {
		Session session = openSession();
		org.hibernate.Transaction tx = session.beginTransaction();
		
		String hql = "delete from PAYOUT_RULES where userId= :userId";
		session.createQuery(hql).setLong("userId", userId).executeUpdate();
		tx.commit();
		session.close();
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
	@SuppressWarnings("unchecked")
	public static List<PayOutRule> get(int hour, int day) throws PayOutRuleNotFoundException {
		Session session = openSession();
		session.beginTransaction();
		List<PayOutRule> por = (List<PayOutRule>) session.createCriteria(PayOutRule.class).add(Restrictions.eq("hour", hour)).add(Restrictions.eq("day", day)).list();
		
		session.close();
		
		if (por == null || por.isEmpty())
			throw new PayOutRuleNotFoundException();
			
		return por;
	}
}
