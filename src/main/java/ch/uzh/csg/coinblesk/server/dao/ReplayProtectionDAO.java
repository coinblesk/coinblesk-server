/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.uzh.csg.coinblesk.server.dao;

import ch.uzh.csg.coinblesk.server.entity.Refund;
import ch.uzh.csg.coinblesk.server.entity.ReplayProtection;
import java.util.Date;
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
public class ReplayProtectionDAO {

    @PersistenceContext()
    private EntityManager em;

    public ReplayProtection findByClientPublicKeyDate(final byte[] clientPublicKey, String endpoint, Date seenDate) {
        final CriteriaBuilder cb = em.getCriteriaBuilder();
        final CriteriaQuery<ReplayProtection> query = cb.createQuery(ReplayProtection.class);
        final Root<ReplayProtection> from = query.from(ReplayProtection.class);
        final Predicate condition1 = cb.equal(from.get("clientPublicKey"), clientPublicKey);
        final Predicate condition2 = cb.equal(from.get("seenDate"), seenDate);
        final Predicate condition3 = cb.equal(from.get("endpoint"), endpoint);
        final Predicate conditionAnd = cb.and(condition1, condition2, condition3);
        CriteriaQuery<ReplayProtection> select = query.select(from).where(conditionAnd);
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
