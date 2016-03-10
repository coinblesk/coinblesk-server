package ch.uzh.csg.coinblesk.server.dao;

import ch.uzh.csg.coinblesk.server.entity.Tx;
import org.springframework.stereotype.Repository;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import java.util.List;

/**
 * Created by ale on 10/03/16.
 */


@Repository
public class TxDAO {
    @PersistenceContext()
    private EntityManager em;

    public List<Tx> findByClientPublicKey(final byte[] clientPublicKey) {
        final CriteriaBuilder cb = em.getCriteriaBuilder();
        final CriteriaQuery<Tx> query = cb.createQuery(Tx.class);
        final Root<Tx> from = query.from(Tx.class);
        final Predicate condition = cb.equal(from.get("clientPublicKey"), clientPublicKey);
        CriteriaQuery<Tx> select = query.select(from).where(condition);
        return em.createQuery(select).getResultList();
    }

    public <T> T save(final T enity) {
        return em.merge(enity);
    }

    public List<Tx> findAll() {
        final CriteriaBuilder cb = em.getCriteriaBuilder();
        final CriteriaQuery<Tx> query = cb.createQuery(Tx.class);
        final Root<Tx> from = query.from(Tx.class);
        CriteriaQuery<Tx> select = query.select(from);
        return em.createQuery(select).getResultList();
    }
}
