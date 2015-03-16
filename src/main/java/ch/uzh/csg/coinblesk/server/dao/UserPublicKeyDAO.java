package ch.uzh.csg.coinblesk.server.dao;

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

import ch.uzh.csg.coinblesk.customserialization.PKIAlgorithm;
import ch.uzh.csg.coinblesk.server.domain.UserAccount;
import ch.uzh.csg.coinblesk.server.domain.UserPublicKey;
import ch.uzh.csg.coinblesk.server.util.exceptions.UserAccountNotFoundException;

/**
 * DatabaseAccessObject for {@link UserPublicKey}. Handles all DB operations
 * regarding UserPublicKeys.
 * 
 * @author Jeton Memeti
 * 
 */
@Repository
public class UserPublicKeyDAO {
	private static Logger LOGGER = Logger.getLogger(UserPublicKeyDAO.class);
	
	@PersistenceContext
	private EntityManager em;
	
	@Autowired
	private UserAccountDAO userAccountDAO;
	
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
	public byte saveUserPublicKey(long userId, PKIAlgorithm algorithm, String publicKey) throws UserAccountNotFoundException {
		UserAccount userAccountFromDB = userAccountDAO.getById(userId);
		byte newKeyNumber = (byte) (userAccountFromDB.getNofKeys()+1);
		userAccountFromDB.setNofKeys(newKeyNumber);
		UserPublicKey toSave = new UserPublicKey(userId, newKeyNumber, algorithm.getCode(), publicKey);
		em.merge(userAccountFromDB);
		em.persist(toSave);
		LOGGER.info("UserPublicKey saved: user id: " + userId + ", key number: " + newKeyNumber);
		return toSave.getKeyNumber();
		
	}
	
	/**
	 * Returns a list of {@link UserPublicKey} mapped to the given user account.
	 * 
	 * @param userId
	 *            the id of the user account
	 * @return a list with all {@link UserPublicKey} or an empty list, if no key
	 *         has been stored for this user id
	 */
	public List<UserPublicKey> getUserPublicKeys(long userId) {
		CriteriaBuilder cb = em.getCriteriaBuilder();
		CriteriaQuery<UserPublicKey> cq = cb.createQuery(UserPublicKey.class);
		Root<UserPublicKey> root = cq.from(UserPublicKey.class);
		Predicate condition = cb.equal(root.get("userId"), userId);
		cq.where(condition);
		return em.createQuery(cq).getResultList();
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
	public UserPublicKey getUserPublicKey(long userId, byte keyNumber) {
		CriteriaBuilder cb = em.getCriteriaBuilder();
		CriteriaQuery<UserPublicKey> cq = cb.createQuery(UserPublicKey.class);
		Root<UserPublicKey> root = cq.from(UserPublicKey.class);
		Predicate condition1 = cb.equal(root.get("userId"), userId);
		Predicate condition2 = cb.equal(root.get("keyNumber"), keyNumber);
		Predicate condition3 = cb.and(condition1, condition2);
		cq.where(condition3);
		return em.createQuery(cq).getSingleResult();
	}

}
