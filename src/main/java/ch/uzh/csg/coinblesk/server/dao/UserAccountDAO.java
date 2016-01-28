/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.uzh.csg.coinblesk.server.dao;

import ch.uzh.csg.coinblesk.server.entity.UserAccount;
import java.util.List;
import javax.persistence.EntityManager;
import javax.persistence.NonUniqueResultException;
import javax.persistence.PersistenceContext;
import javax.persistence.TypedQuery;
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
public class UserAccountDAO {

    @PersistenceContext()
    private EntityManager em;

    public <T> UserAccount getByAttribute(String attribute, T value) {
        final CriteriaBuilder cb = em.getCriteriaBuilder();
        final CriteriaQuery<UserAccount> cq = cb.createQuery(UserAccount.class);
        final Root<UserAccount> from = cq.from(UserAccount.class);
        final Predicate condition = cb.equal(from.get(attribute), value);
        CriteriaQuery<UserAccount> select = cq.select(from).where(condition);
        return getSingleResultOrNull(em.createQuery(select));
    }
    
    public <T> T save(T enity) {
        return em.merge(enity);
    }

    /**
     * Returns a single result if there is a single result, or null if there is
     * no result. If there is more than one result, then an the
     * NonUniqueResultException is thrown.
     *
     * @param <T>
     * @param query
     * @return
     */
    public static <T> T getSingleResultOrNull(TypedQuery<T> query) {
        query.setMaxResults(2);
        List<T> list = query.getResultList();
        if (list == null || list.isEmpty()) {
            return null;
        } else if (list.size() == 1) {
            return list.get(0);
        }
        throw new NonUniqueResultException();
    }
}
