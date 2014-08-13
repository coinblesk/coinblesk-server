package ch.uzh.csg.mbps.server.dao;

import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;
import org.hibernate.HibernateException;
import org.hibernate.Session;
import org.hibernate.SessionFactory;

import ch.uzh.csg.mbps.server.domain.Activities;
import ch.uzh.csg.mbps.server.util.Config;
import ch.uzh.csg.mbps.server.util.HibernateUtil;

public class ActivitiesDAO {

private static Logger LOGGER = Logger.getLogger(ActivitiesDAO.class);
	
	private static Session openSession() {
		SessionFactory sessionFactory = HibernateUtil.getSessionFactory();
		return sessionFactory.openSession();
	}
	
	/**
	 * Creates the log into the database
	 * @param activity
	 */
	public static void createActivityLog(Activities activity){
		Session session = openSession();
		org.hibernate.Transaction transaction = null;
		try{
			transaction = session.beginTransaction();
			session.update(activity);
			transaction.commit();
			LOGGER.info("Activity created by user " + activity.getUsername()+ " title: " + activity.getTitle());
		} catch (HibernateException e){
			LOGGER.error("Problem creating activity log: " + activity.getTitle() + ". ErrorMessage: " + e.getMessage());
			if (transaction != null)
				transaction.rollback();
			throw e;
		} finally {
			session.close();
		}
	}
	
	/**
	 * Returns a list of predefined amount of activities
	 * @param page: Accessed site page
	 * @return
	 */
	public static ArrayList<Activities> getLogs(int page){
		if (page < 0)
			return null;

		Session session = openSession();
		session.beginTransaction();
		
		@SuppressWarnings("unchecked")
		List<Activities> result = (List<Activities>) session.createCriteria(Activities.class)
				.setFirstResult(page * Config.ACTIVITIES_MAX_RESULTS)
				.setMaxResults(Config.ACTIVITIES_MAX_RESULTS)
				.setFetchSize(Config.ACTIVITIES_MAX_RESULTS)
				.list();
		session.close();
		
		return (ArrayList<Activities>) result;
	}
}
