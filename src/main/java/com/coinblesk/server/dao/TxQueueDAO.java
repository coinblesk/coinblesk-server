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

import com.coinblesk.server.entity.Keys;
import com.coinblesk.server.entity.TxQueue;
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
 * @author Thomas Bocek
 */
@Repository
public class TxQueueDAO {

    @PersistenceContext()
    private EntityManager em;

    public TxQueue save(final TxQueue entity) {
        TxQueue e = findByTxHash(entity.txHash());
        if(e == null) {
            e = em.merge(entity);
            em.flush();
        }
    	return e;
    }
    
    public TxQueue findByTxHash(final byte[] txHash) {
        final CriteriaBuilder cb = em.getCriteriaBuilder();
        final CriteriaQuery<TxQueue> query = cb.createQuery(TxQueue.class);
        final Root<TxQueue> from = query.from(TxQueue.class);
        final Predicate condition = cb.equal(from.get("txHash"), txHash);
        CriteriaQuery<TxQueue> select = query.select(from).where(condition);
        return DAOUtils.getSingleResultOrNull(em.createQuery(select));
    }

    public List<TxQueue> findAll() {
        final CriteriaBuilder cb = em.getCriteriaBuilder();
        final CriteriaQuery<TxQueue> query = cb.createQuery(TxQueue.class);
        final Root<TxQueue> from = query.from(TxQueue.class);
        final CriteriaQuery<TxQueue> select = query.select(from);
        return em.createQuery(select).getResultList();
    }
    
    public int remove(final byte[] txHash) {
        final CriteriaBuilder cb = em.getCriteriaBuilder();
        final CriteriaDelete<TxQueue> delete = cb.createCriteriaDelete(TxQueue.class);
        final Root from = delete.from(TxQueue.class);
        final Predicate condition = cb.equal(from.get("txHash"), txHash);
        delete.where(condition);
        return em.createQuery(delete).executeUpdate();
    }
}
