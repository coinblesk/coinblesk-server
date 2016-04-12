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

import com.coinblesk.server.entity.UserAccount;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.CriteriaUpdate;
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

    public <T> UserAccount getByAttribute(final String attribute, final T value) {
        final CriteriaBuilder cb = em.getCriteriaBuilder();
        final CriteriaQuery<UserAccount> query = cb.createQuery(UserAccount.class);
        final Root<UserAccount> from = query.from(UserAccount.class);
        final Predicate condition = cb.equal(from.get(attribute), value);
        CriteriaQuery<UserAccount> select = query.select(from).where(condition);
        return DAOUtils.getSingleResultOrNull(em.createQuery(select));
    }
    
    public <T> T save(final T enity) {
        return em.merge(enity);
    }
    
    public int remove(final String email) {
        final CriteriaBuilder cb = em.getCriteriaBuilder();
        final CriteriaUpdate<UserAccount> update = cb.createCriteriaUpdate(UserAccount.class);
        final Root from = update.from(UserAccount.class);
        final Predicate condition = cb.equal(from.get("email"), email);
        update.set(from.get("isDeleted"), true).where(condition);
        return em.createQuery(update).executeUpdate();
    }
}
