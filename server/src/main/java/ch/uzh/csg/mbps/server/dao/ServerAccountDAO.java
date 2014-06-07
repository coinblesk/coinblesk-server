package ch.uzh.csg.mbps.server.dao;

import java.util.List;

import org.apache.log4j.Logger;
import org.hibernate.HibernateException;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.criterion.Restrictions;

import ch.uzh.csg.mbps.server.domain.ServerAccount;
import ch.uzh.csg.mbps.server.util.HibernateUtil;
import ch.uzh.csg.mbps.server.util.exceptions.ServerAccountNotFoundException;

/**
 * DatabaseAccessObject for {@link ServerAccount}s. Handles all DB operations
 * regarding {@link ServerAccount}s.
 * 
 */
public class ServerAccountDAO {
	private static Logger LOGGER = Logger.getLogger(ServerAccountDAO.class);

	private ServerAccountDAO(){
	}
	
	/**
	 * 
	 * @return
	 */
	private static Session openSession() {
		SessionFactory sessionFactory = HibernateUtil.getSessionFactory();
		Session session = sessionFactory.openSession();
		return session;
	}
	
	/**
	 * 
	 * @param serverAccount
	 * @param token
	 */
	public static void createAccount(ServerAccount serverAccount, String token){
		Session session = null;
		
		org.hibernate.Transaction transaction = null;
		ServerAccount fromDb;
		try{
			session = openSession();
			transaction = session.beginTransaction();
			session.save(serverAccount);
			fromDb = (ServerAccount) session.createCriteria(ServerAccount.class).add(Restrictions.eq("url", serverAccount.getUrl())).uniqueResult();
			transaction.commit();
			LOGGER.info("ServerAccount created: " + fromDb.toString());
		} catch(HibernateException e) {
			LOGGER.error("Problem creating ServerAccount: " + serverAccount.toString());
			if(transaction != null)
				transaction.rollback();
			throw e;
		} finally {
			session.close();
		}
	}

	public static void delete(String url) throws ServerAccountNotFoundException{
		ServerAccount serverAccount = getByUrl(url);
		
		Session session = openSession();
		org.hibernate.Transaction transaction = null;
		
		//TODO: mehmet: check: TrustLevel -> 
		// Full: activeBalance has to be zero
		// Hyprid: escrow account
		// No: User server account Balance has to be zero
	}
	
	/**
	 * 
	 * @param serverAccount
	 */
	public static void updatedAccount(ServerAccount serverAccount){
		Session session = openSession();
		org.hibernate.Transaction transaction = null;
		
		try{
			transaction = session.beginTransaction();
			session.update(serverAccount);
			transaction.commit();
			LOGGER.info("Updated ServerAccount: " + serverAccount.toString());
		} catch(HibernateException e) {
			LOGGER.error("Problem updating ServerAccount: " + serverAccount.toString() + " ErrorMessage: "+ e.getMessage());
			if(transaction != null)
				transaction.rollback();
			throw e;
		} finally {
			session.close();
		}
		
	}
	
	/**
	 * 
	 * @param url
	 * @return
	 * @throws ServerAccountNotFoundException
	 */
	public static ServerAccount getByUrl(String url) throws ServerAccountNotFoundException{
		Session session = openSession();
		session.beginTransaction();
		
		ServerAccount serverAccount = (ServerAccount) session.createCriteria(ServerAccount.class).add(Restrictions.eq("url", url)).uniqueResult();
		session.close();
		if(serverAccount == null || serverAccount.isDeleted())
			throw new ServerAccountNotFoundException(url);
		
		return serverAccount;
	}

	/**
	 * 
	 * @param url
	 * @return
	 * @throws ServerAccountNotFoundException
	 */
	public static ServerAccount getByUrlIgnoreCaseAndDeletedFlag(String url) throws ServerAccountNotFoundException{
		Session session = openSession();
		session.beginTransaction();
		
		ServerAccount serverAccount = (ServerAccount) session.createCriteria(ServerAccount.class).add(Restrictions.eq("url", url)).uniqueResult();
		session.close();
		if(serverAccount == null)
			throw new ServerAccountNotFoundException(url);
		
		return serverAccount;
	}
	
	/**
	 * 
	 * @param id
	 * @return
	 * @throws ServerAccountNotFoundException
	 */
	public static ServerAccount getById(Long id) throws ServerAccountNotFoundException{
		Session session = openSession();
		session.beginTransaction();
		
		ServerAccount serverAccount = (ServerAccount) session.createCriteria(ServerAccount.class).add(Restrictions.eq("id", id)).uniqueResult();
		session.close();
		if(serverAccount == null || serverAccount.isDeleted())
			throw new ServerAccountNotFoundException("id: " + id);
		
		return serverAccount;
	}
	
	/**
	 * 
	 * @param email
	 * @return
	 * @throws ServerAccountNotFoundException
	 */
	public static ServerAccount getByEmail(String email) throws ServerAccountNotFoundException{
		Session session = openSession();
		session.beginTransaction();
		ServerAccount serverAccount = (ServerAccount) session.createCriteria(ServerAccount.class).add(Restrictions.eq("email", email)).uniqueResult();
		session.close();
		if (serverAccount == null || serverAccount.isDeleted())
			throw new ServerAccountNotFoundException("email: "+ email);
		
		return serverAccount;
	}
	
	/**
	 * 
	 * @param email
	 * @return
	 * @throws ServerAccountNotFoundException
	 */
	public static ServerAccount getByEmailIgnoreCaseAndDeletedFlag(String email) throws ServerAccountNotFoundException{
		Session session = openSession();
		session.beginTransaction();
		ServerAccount serverAccount = (ServerAccount) session.createCriteria(ServerAccount.class).add(Restrictions.eq("email", email).ignoreCase()).uniqueResult();
		session.close();
		if (serverAccount == null)
			throw new ServerAccountNotFoundException(email);
		
		return serverAccount;
	}

	/**
	 * 
	 * @param address
	 * @return
	 * @throws ServerAccountNotFoundException
	 */
	public static ServerAccount getByPayinAddress(String address) throws ServerAccountNotFoundException{
		Session session = openSession();
		session.beginTransaction();
		ServerAccount serverAccount = (ServerAccount) session.createCriteria(ServerAccount.class).add(Restrictions.eq("payinAddress", address)).uniqueResult();
		
		session.close();
		if (serverAccount == null || serverAccount.isDeleted())
			throw new ServerAccountNotFoundException("Payin Address: "+address);
		
		return serverAccount;
	}
	
	/**
	 * 
	 * @param trustlevel
	 * @return
	 */
	public static List<ServerAccount> getListOfTrustLevel(int trustlevel){
		Session session = openSession();
		session.beginTransaction();
		@SuppressWarnings("unchecked")
		List<ServerAccount> list = (List<ServerAccount>) session.createCriteria(ServerAccount.class).add(Restrictions.eq("trustLevel", trustlevel)).list();
		session.close();
		return list;
	}
	
	/**
	 * 
	 * @return
	 */
	public static List<ServerAccount> getAllServerAccounts(){
		Session session = openSession();
		session.beginTransaction();
		@SuppressWarnings("unchecked")
		List<ServerAccount> list = (List<ServerAccount>) session.createCriteria(ServerAccount.class).list();
		session.close();
		return list;
	}
	
}
