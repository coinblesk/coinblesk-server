package ch.uzh.csg.coinblesk.server.dao;

import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import ch.uzh.csg.coinblesk.server.entity.ClientWatchingKey;


/**
 * DatabaseAccessObject for storing bitcoin transaction inputs of time-locked
 * transactions that have been signed by the server.
 * 
 */
@Repository
public class ClientWatchingKeyDAO {

    @PersistenceContext()
    private EntityManager em;

    /**
     * Checks in the database if the client watching key already exists.
     * 
     * @param clientWatchingKey
     *            the base64 encoded watching key of the client
     * @return true if the watching key already exists
     */
    public boolean exists(String clientWatchingKey) {

        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<ClientWatchingKey> qb = cb.createQuery(ClientWatchingKey.class);
        Root<ClientWatchingKey> root = qb.from(ClientWatchingKey.class);

        Predicate condition = cb.equal(root.get("clientWatchingKey"), clientWatchingKey);

        qb.where(condition);

        return getSingle(qb, em) != null;
    }

    private <K> K getSingle(CriteriaQuery<K> cq, EntityManager em) {
        List<K> list = em.createQuery(cq).getResultList();
        if (list.size() == 0) {
            return null;
        }
        return list.get(0);
    }

    @Transactional
    public void addClientWatchingKey(String base58EncodedWatchingKey) {

        ClientWatchingKey watchingKey = new ClientWatchingKey(base58EncodedWatchingKey);
        em.persist(watchingKey);
        em.flush();

    }
}
