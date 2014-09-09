package ch.uzh.csg.mbps.server.dao;

import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Root;

import org.apache.log4j.Logger;
import org.springframework.stereotype.Repository;

import ch.uzh.csg.mbps.server.domain.Activities;
import ch.uzh.csg.mbps.server.util.Config;

/**
 * DatabaseAccessObject for {@link Activities}s. Handles all DB operations
 * regarding {@link Activities}.
 * 
 */
@Repository
public class ActivitiesDAO {

private static Logger LOGGER = Logger.getLogger(ActivitiesDAO.class);

	@PersistenceContext
	private EntityManager em;
	
	/**
	 * Creates the log into the database
	 * @param activity
	 */
	public void createActivityLog(Activities activity) {
		em.merge(activity);
		LOGGER.info("Activity created by user " + activity.getUsername()+ " title: " + activity.getTitle());
	}
	
	/**
	 * Returns a list of predefined amount of activities
	 * @param page: Accessed site page
	 * @return
	 */
	public List<Activities> getLogs(int page){
		if (page < 0) {
			return null;
		}

		CriteriaBuilder cb = em.getCriteriaBuilder();
		CriteriaQuery<Activities> cq = cb.createQuery(Activities.class);
		Root<Activities> root = cq.from(Activities.class);
		
		cq.orderBy(cb.desc(root.get("creationDate")));
		
		return em.createQuery(cq)
		         .setFirstResult(page * Config.ACTIVITIES_MAX_RESULTS)
		         .setMaxResults(Config.ACTIVITIES_MAX_RESULTS)
		         .getResultList();
	}
}
