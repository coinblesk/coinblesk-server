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

import com.coinblesk.server.entity.ApprovedTx;
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
public class ApprovedTxDAO {

    @PersistenceContext()
    private EntityManager em;

    public List<ApprovedTx> findByAddress(final byte[] address) {
        final CriteriaBuilder cb = em.getCriteriaBuilder();
        final CriteriaQuery<ApprovedTx> query = cb.createQuery(ApprovedTx.class);
        final Root<ApprovedTx> from = query.from(ApprovedTx.class);
        final Predicate condition1 = cb.equal(from.get("addressTo"), address);
        final Predicate condition2 = cb.equal(from.get("addressFrom"), address);
        final Predicate conditionOr = cb.or(condition1, condition2);
        CriteriaQuery<ApprovedTx> select = query.select(from).where(conditionOr);
        return em.createQuery(select).getResultList();
    }

    public List<ApprovedTx> findAll() {
        final CriteriaBuilder cb = em.getCriteriaBuilder();
        final CriteriaQuery<ApprovedTx> query = cb.createQuery(ApprovedTx.class);
        final Root<ApprovedTx> from = query.from(ApprovedTx.class);
        CriteriaQuery<ApprovedTx> select = query.select(from);
        return em.createQuery(select).getResultList();
    }

    public <T> T save(final T enity) {
        return em.merge(enity);
    }

    public int remove(byte[] txHash) {
        final CriteriaBuilder cb = em.getCriteriaBuilder();
        final CriteriaDelete<ApprovedTx> delete = cb.createCriteriaDelete(ApprovedTx.class);
        final Root from = delete.from(ApprovedTx.class);
        final Predicate condition = cb.equal(from.get("txHash"), txHash);
        delete.where(condition);
        return em.createQuery(delete).executeUpdate();
    }
}
