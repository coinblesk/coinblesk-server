package ch.uzh.csg.mbps.server.util;

import org.apache.log4j.Logger;
import org.hibernate.HibernateException;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.hibernate.service.ServiceRegistry;
import org.hibernate.service.ServiceRegistryBuilder;

/**
 * Class providing Hibernate SessionFactory.
 *
 */
public class HibernateUtil {
	private static Logger LOGGER = Logger.getLogger(HibernateUtil.class);
	
	private static SessionFactory sessionFactory;
	private static ServiceRegistry serviceRegistry;
	
	static {
		try {
			Configuration config = new Configuration();
			config.configure();
			
			serviceRegistry = new ServiceRegistryBuilder().applySettings(config.getProperties()).buildServiceRegistry();
			sessionFactory = config.buildSessionFactory(serviceRegistry);
		} catch (HibernateException e) {
			LOGGER.fatal("Error creating hibernate session: "+e);
			throw new ExceptionInInitializerError(e);
		}
	}
	
	/**
	 * Returns hibernate SessionFactory.
	 * 
	 * @return sessionFactory
	 */
    public static SessionFactory getSessionFactory() {
        return sessionFactory;
    }
}
