package ch.uzh.csg.mbps.server.dao;

import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;
import org.hibernate.HibernateException;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.criterion.Restrictions;

import ch.uzh.csg.mbps.customserialization.PKIAlgorithm;
import ch.uzh.csg.mbps.server.domain.UserAccount;
import ch.uzh.csg.mbps.server.domain.UserPublicKey;
import ch.uzh.csg.mbps.server.util.HibernateUtil;
import ch.uzh.csg.mbps.server.util.exceptions.UserAccountNotFoundException;

/**
 * DatabaseAccessObject for {@link UserPublicKey}. Handles all DB operations
 * regarding UserPublicKeys.
 * 
 * @author Jeton Memeti
 * 
 */
public class UserPublicKeyDAO {
	private static Logger LOGGER = Logger.getLogger(UserPublicKeyDAO.class);
	
	private UserPublicKeyDAO() {
	}
	
	private static Session openSession() {
		SessionFactory sessionFactory = HibernateUtil.getSessionFactory();
		Session session = sessionFactory.openSession();
		return session;
	}
	
	/**
	 * Stores a public key on the database and maps this public key to a user
	 * account.
	 * 
	 * @param userId
	 *            the id of the user account
	 * @param algorithm
	 *            the {@link PKIAlgorithm} used to generate the key
	 * @param publicKey
	 *            the base64 encoded public key
	 * @return the key number, indicating the (incremented) position this public
	 *         key has in a list of public keys mapped to this user account
	 * @throws UserAccountNotFoundException
	 */
	public static byte saveUserPublicKey(long userId, PKIAlgorithm algorithm, String publicKey) throws UserAccountNotFoundException {
		Session session = null;
		Transaction transaction = null;
		
		UserAccount userAccountFromDB = UserAccountDAO.getById(userId);
		byte newKeyNumber = (byte) (userAccountFromDB.getNofKeys()+1);
		
		userAccountFromDB.setNofKeys(newKeyNumber);
		
		UserPublicKey toSave = new UserPublicKey(userId, newKeyNumber, algorithm.getCode(), publicKey);
		
		try {
			session = openSession();
			transaction = session.beginTransaction();
			session.update(userAccountFromDB);
			session.save(toSave);
			transaction.commit();
			LOGGER.info("UserPublicKey saved: user id: " + userId + ", key number: " + newKeyNumber);
			return toSave.getKeyNumber();
		} catch (HibernateException e) {
			LOGGER.error("Problem creating UserPublicKey: " + toSave.toString());
			 if (transaction != null)
				 transaction.rollback();
			 throw e;
		} finally {
			session.close();
		}
	}
	
	/**
	 * Returns a list of {@link UserPublicKey} mapped to the given user account.
	 * 
	 * @param userId
	 *            the id of the user account
	 * @return a list with all {@link UserPublicKey} or an empty list, if no key
	 *         has been stored for this user id
	 */
	public static List<UserPublicKey> getUserPublicKeys(long userId) {
		Session session = openSession();
		session.beginTransaction();
		
		@SuppressWarnings("unchecked")
		List<UserPublicKey> list = (List<UserPublicKey>) session
				.createCriteria(UserPublicKey.class)
				.add(Restrictions.eq("userId", userId)).list();
		session.close();
		
		if (list == null || list.isEmpty())
			return new ArrayList<UserPublicKey>();
		else
			return list;
	}
	
	/**
	 * Returns the {@link UserPublicKey} with the given key number mapped to the
	 * account with the user id provided.
	 * 
	 * @param userId
	 *            the id of the user account
	 * @param keyNumber
	 *            the key number of the {@link UserPublicKey} to return
	 * @return the corresponding {@link UserPublicKey} or null
	 */
	public static UserPublicKey getUserPublicKey(long userId, byte keyNumber) {
		Session session = openSession();
		session.beginTransaction();
		
		UserPublicKey result = (UserPublicKey) session
				.createCriteria(UserPublicKey.class)
				.add(Restrictions.eq("userId", userId))
				.add(Restrictions.eq("keyNumber", keyNumber)).uniqueResult();
		session.close();
		
		return result;
	}

}
