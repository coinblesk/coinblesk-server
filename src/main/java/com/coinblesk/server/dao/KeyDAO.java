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

    @PersistenceContext
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
    	T e = em.merge(enity);
    	em.flush();
        return e;
    }

    public List<Keys> findAll() {
        final CriteriaBuilder cb = em.getCriteriaBuilder();
        final CriteriaQuery<Keys> query = cb.createQuery(Keys.class);
        final Root<Keys> from = query.from(Keys.class);
        CriteriaQuery<Keys> select = query.select(from);
        return em.createQuery(select).getResultList();
    }
}
