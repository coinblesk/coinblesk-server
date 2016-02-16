/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.uzh.csg.coinblesk.server.dao;

import ch.uzh.csg.coinblesk.server.entity.Keys;
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

    public Keys getByHash(final byte[] clientHash) {
        final CriteriaBuilder cb = em.getCriteriaBuilder();
        final CriteriaQuery<Keys> query = cb.createQuery(Keys.class);
        final Root<Keys> from = query.from(Keys.class);
        final Predicate condition = cb.equal(from.get("clientHash"), clientHash);
        CriteriaQuery<Keys> select = query.select(from).where(condition);
        return DAOUtils.getSingleResultOrNull(em.createQuery(select));
    }
    
    public <T> T save(final T enity) {
        return em.merge(enity);
    }
}
