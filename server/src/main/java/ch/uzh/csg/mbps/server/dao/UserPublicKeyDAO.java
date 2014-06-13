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

//TODO jeton: javadoc
public class UserPublicKeyDAO {
	private static Logger LOGGER = Logger.getLogger(UserPublicKeyDAO.class);
	
	private UserPublicKeyDAO() {
	}
	
	private static Session openSession() {
		SessionFactory sessionFactory = HibernateUtil.getSessionFactory();
		Session session = sessionFactory.openSession();
		return session;
	}
	
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
