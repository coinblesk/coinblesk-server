/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.uzh.csg.coinblesk.server.dao;

import ch.uzh.csg.coinblesk.server.entity.Refund;
import java.util.List;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import org.springframework.stereotype.Repository;

/**
 *
 * @author Thomas Bocek
 */
@Repository
public class RefundDAO {

    @PersistenceContext()
    private EntityManager em;

    public Refund findByClientPublicKey(final byte[] clientPublicKey) {
        final CriteriaBuilder cb = em.getCriteriaBuilder();
        final CriteriaQuery<Refund> query = cb.createQuery(Refund.class);
        final Root<Refund> from = query.from(Refund.class);
        final Predicate condition = cb.equal(from.get("clientPublicKey"), clientPublicKey);
        CriteriaQuery<Refund> select = query.select(from).where(condition);
        return DAOUtils.getSingleResultOrNull(em.createQuery(select));
    }
    
    public <T> T save(final T enity) {
        return em.merge(enity);
    }

    public List<Refund> findAll() {
        final CriteriaBuilder cb = em.getCriteriaBuilder();
        final CriteriaQuery<Refund> query = cb.createQuery(Refund.class);
        final Root<Refund> from = query.from(Refund.class);
        CriteriaQuery<Refund> select = query.select(from);
        return em.createQuery(select).getResultList();
    }
}
