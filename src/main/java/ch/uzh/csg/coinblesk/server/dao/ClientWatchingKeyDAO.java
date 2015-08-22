package ch.uzh.csg.coinblesk.server.dao;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import ch.uzh.csg.coinblesk.server.entity.ClientWatchingKey;


/**
 * DatabaseAccessObject for storing bitcoin transaction inputs of time-locked
 * transactions that have been signed by the server.
 * 
 */
@Repository
public class ClientWatchingKeyDAO {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(ClientWatchingKeyDAO.class);

    @PersistenceContext()
    private EntityManager em;

    /**
     * Checks in the database if the client watching key already exists.
     * 
     * @param clientWatchingKey
     *            the base64 encoded watching key of the client
     * @return true if the watching key already exists
     */
    public boolean exists(final String clientWatchingKey) {
    	final CriteriaBuilder cb = em.getCriteriaBuilder();
    	final CriteriaQuery<Long> cq = cb.createQuery(Long.class);
    	final CriteriaQuery<Long> qb = cq.select(cb.count(cq.from(ClientWatchingKey.class)));
    	final Root<ClientWatchingKey> root = qb.from(ClientWatchingKey.class);
    	
    	final Predicate condition = cb.equal(root.get("clientWatchingKey"), clientWatchingKey);

        qb.where(condition);

        long result = em.createQuery(cq).getSingleResult();
        LOGGER.debug("found {} entries for watching key {}", result, clientWatchingKey);
        return result > 0;
    }

    public void addClientWatchingKey(final String base58EncodedWatchingKey) {

    	final ClientWatchingKey watchingKey = new ClientWatchingKey(base58EncodedWatchingKey);
        em.persist(watchingKey);
        em.flush();
        LOGGER.debug("added encoded watching key {}", base58EncodedWatchingKey);
    }
}
