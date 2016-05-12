/*
 * Copyright 2016 The Coinblesk team and the CSG Group at University of Zurich
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.coinblesk.server.dao;

import com.coinblesk.server.entity.Tx;
import org.springframework.stereotype.Repository;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import java.util.List;
import javax.persistence.criteria.CriteriaDelete;

/**
 *
 * @author Alessandro De Carli
 * @author Thomas Bocek
 */
@Repository
public class TxDAO {

    @PersistenceContext()
    private EntityManager em;
    
    public List<Tx> findByClientPublicKey(final byte[] clientPublicKey, final boolean approved) {
        final CriteriaBuilder cb = em.getCriteriaBuilder();
        final CriteriaQuery<Tx> query = cb.createQuery(Tx.class);
        final Root<Tx> from = query.from(Tx.class);
        final Predicate condition1 = cb.equal(from.get("clientPublicKey"), clientPublicKey);
        final Predicate condition2 = cb.equal(from.get("approved"), approved);
        final Predicate condition3 = cb.and(condition1, condition2);
        final CriteriaQuery<Tx> select = query.select(from).where(condition3);
        return em.createQuery(select).getResultList();
    }

    public <T> T save(final T enity) {
    	T e = em.merge(enity);
        em.flush();
    	return e;
    }

    public List<Tx> findAll() {
        final CriteriaBuilder cb = em.getCriteriaBuilder();
        final CriteriaQuery<Tx> query = cb.createQuery(Tx.class);
        final Root<Tx> from = query.from(Tx.class);
        final CriteriaQuery<Tx> select = query.select(from);
        return em.createQuery(select).getResultList();
    }
    
    public List<Tx> findAll(final boolean approved) {
        final CriteriaBuilder cb = em.getCriteriaBuilder();
        final CriteriaQuery<Tx> query = cb.createQuery(Tx.class);
        final Root<Tx> from = query.from(Tx.class);
        final Predicate condition = cb.equal(from.get("approved"), approved);
        final CriteriaQuery<Tx> select = query.select(from).where(condition);
        return em.createQuery(select).getResultList();
    }
    
    public int remove(final byte[] txHash) {
        final CriteriaBuilder cb = em.getCriteriaBuilder();
        final CriteriaDelete<Tx> delete = cb.createCriteriaDelete(Tx.class);
        final Root from = delete.from(Tx.class);
        final Predicate condition = cb.equal(from.get("txHash"), txHash);
        delete.where(condition);
        return em.createQuery(delete).executeUpdate();
    }
    
    public int remove(final byte[] txHash, final boolean approved) {
        final CriteriaBuilder cb = em.getCriteriaBuilder();
        final CriteriaDelete<Tx> delete = cb.createCriteriaDelete(Tx.class);
        final Root from = delete.from(Tx.class);
        final Predicate condition1 = cb.equal(from.get("txHash"), txHash);
        final Predicate condition2 = cb.equal(from.get("approved"), approved);
        final Predicate condition3 = cb.and(condition1, condition2);
        delete.where(condition3);
        return em.createQuery(delete).executeUpdate();
    }
}
