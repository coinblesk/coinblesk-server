/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.uzh.csg.coinblesk.server.dao;

import ch.uzh.csg.coinblesk.server.entity.Keys;
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
public class KeyDAO {

    @PersistenceContext()
    private EntityManager em;

    public Keys findByClientPublicKey(final byte[] clientPublicKey) {
        final CriteriaBuilder cb = em.getCriteriaBuilder();
        final CriteriaQuery<Keys> query = cb.createQuery(Keys.class);
        final Root<Keys> from = query.from(Keys.class);
        final Predicate condition = cb.equal(from.get("clientPublicKey"), clientPublicKey);
        CriteriaQuery<Keys> select = query.select(from).where(condition);
        return DAOUtils.getSingleResultOrNull(em.createQuery(select));
    }
    
    public <T> T save(final T enity) {
        return em.merge(enity);
    }

    public List<Keys> findAll() {
        final CriteriaBuilder cb = em.getCriteriaBuilder();
        final CriteriaQuery<Keys> query = cb.createQuery(Keys.class);
        final Root<Keys> from = query.from(Keys.class);
        CriteriaQuery<Keys> select = query.select(from);
        return em.createQuery(select).getResultList();
    }

    public boolean containsP2SH(byte[] hash160) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Long> query = cb.createQuery(Long.class);
        final Root<Keys> from = query.from(Keys.class);
        query.select(cb.count(from));
        final Predicate condition = cb.equal(from.get("p2shHash"), hash160);
        query.where(condition);
        return em.createQuery(query).getSingleResult() > 0;
    }
}
