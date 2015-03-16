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
import ch.uzh.csg.coinblesk.server.domain.ServerAccount;
import ch.uzh.csg.coinblesk.server.domain.ServerPublicKey;
import ch.uzh.csg.coinblesk.server.util.exceptions.ServerAccountNotFoundException;

@Repository
public class ServerPublicKeyDAO {
	private static Logger LOGGER = Logger.getLogger(UserPublicKeyDAO.class);
	
	@PersistenceContext
	private EntityManager em;
	
	@Autowired
	private ServerAccountDAO serverAccountDAO;
	
	/**
	 * Stores a public key on the database and maps this public key to a server
	 * account.
	 * 
	 * @param userId
	 *            the id of the server account
	 * @param algorithm
	 *            the {@link PKIAlgorithm} used to generate the key
	 * @param publicKey
	 *            the base64 encoded public key
	 * @return the key number, indicating the (incremented) position this public
	 *         key has in a list of public keys mapped to this server account
	 * @throws ServerAccountNotFoundException
	 */
	public byte saveUserPublicKey(long serverId, PKIAlgorithm algorithm, String publicKey) throws ServerAccountNotFoundException {
		ServerAccount accountFromDB = serverAccountDAO.getById(serverId);
		byte newKeyNumber = (byte) (accountFromDB.getNOfKeys()+1);
		accountFromDB.setNOfKeys(newKeyNumber);
		ServerPublicKey toSave = new ServerPublicKey(serverId, newKeyNumber, algorithm.getCode(), publicKey);
		em.merge(accountFromDB);
		em.persist(toSave);
		LOGGER.info("UserPublicKey saved: server id: " + serverId + ", key number: " + "[newKeyNumber]");
		return toSave.getKeyNumber();
		
	}
	
	/**
	 * Returns a list of {@link ServerPublicKey} mapped to the given server account.
	 * 
	 * @param serverId
	 *            the id of the server account
	 * @return a list with all {@link ServerPublicKey} or an empty list, if no key
	 *         has been stored for this server id
	 */
	public List<ServerPublicKey> getServerPublicKeys(long serverId) {
		CriteriaBuilder cb = em.getCriteriaBuilder();
		CriteriaQuery<ServerPublicKey> cq = cb.createQuery(ServerPublicKey.class);
		Root<ServerPublicKey> root = cq.from(ServerPublicKey.class);
		Predicate condition = cb.equal(root.get("serverId"), serverId);
		cq.where(condition);
		return em.createQuery(cq).getResultList();
	}
	
	/**
	 * Returns the {@link ServerPublicKey} with the given key number mapped to the
	 * account with the server id provided.
	 * 
	 * @param serverId
	 *            the id of the server account
	 * @param keyNumber
	 *            the key number of the {@link ServerPublicKey} to return
	 * @return the corresponding {@link ServerPublicKey} or null
	 */
	public ServerPublicKey getServerPublicKey(long serverId, byte keyNumber) {
		CriteriaBuilder cb = em.getCriteriaBuilder();
		CriteriaQuery<ServerPublicKey> cq = cb.createQuery(ServerPublicKey.class);
		Root<ServerPublicKey> root = cq.from(ServerPublicKey.class);
		Predicate condition1 = cb.equal(root.get("serverId"), serverId);
		Predicate condition2 = cb.equal(root.get("keyNumber"), keyNumber);
		Predicate condition3 = cb.and(condition1, condition2);
		cq.where(condition3);
		return em.createQuery(cq).getSingleResult();
	}
}
