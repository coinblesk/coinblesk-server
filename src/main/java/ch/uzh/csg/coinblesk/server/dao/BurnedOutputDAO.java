/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.uzh.csg.coinblesk.server.dao;

import ch.uzh.csg.coinblesk.server.entity.BurnedOutput;
import java.util.List;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaDelete;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import org.springframework.stereotype.Repository;

/**
 *
 * @author Thomas Bocek
 */
@Repository
public class BurnedOutputDAO {

    @PersistenceContext()
    private EntityManager em;

    public BurnedOutput findByTxOutpoint(final byte[] txOutpoint) {
        final CriteriaBuilder cb = em.getCriteriaBuilder();
        final CriteriaQuery<BurnedOutput> query = cb.createQuery(BurnedOutput.class);
        final Root<BurnedOutput> from = query.from(BurnedOutput.class);
        final Predicate condition = cb.equal(from.get("txOutpoint"), txOutpoint);
        CriteriaQuery<BurnedOutput> select = query.select(from).where(condition);
        return DAOUtils.getSingleResultOrNull(em.createQuery(select));
    }
    
    public List<BurnedOutput> findByClientKey(final byte[] clientPublicKey) {
        final CriteriaBuilder cb = em.getCriteriaBuilder();
        final CriteriaQuery<BurnedOutput> query = cb.createQuery(BurnedOutput.class);
        final Root<BurnedOutput> from = query.from(BurnedOutput.class);
        final Predicate condition = cb.equal(from.get("clientPublicKey"), clientPublicKey);
        CriteriaQuery<BurnedOutput> select = query.select(from).where(condition);
        return em.createQuery(select).getResultList();
    }
    
    public <T> T save(final T enity) {
        return em.merge(enity);
    }

    public int remove(byte[] outpoints) {
        final CriteriaBuilder cb = em.getCriteriaBuilder();
        final CriteriaDelete<BurnedOutput> delete = cb.createCriteriaDelete(BurnedOutput.class);
        final Root from = delete.from(BurnedOutput.class);
        final Predicate condition = cb.equal(from.get("txOutpoint"), outpoints);
        delete.where(condition);
        return em.createQuery(delete).executeUpdate();
    }

    public int removeAll() {
        final CriteriaBuilder cb = em.getCriteriaBuilder();
        final CriteriaDelete<BurnedOutput> delete = cb.createCriteriaDelete(BurnedOutput.class);
        delete.from(BurnedOutput.class);
        return em.createQuery(delete).executeUpdate();
    }
}
