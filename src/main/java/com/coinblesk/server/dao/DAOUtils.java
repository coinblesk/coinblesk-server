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

import java.util.List;
import javax.persistence.NonUniqueResultException;
import javax.persistence.TypedQuery;

/**
 *
 * @author Thomas Bocek
 */
public class DAOUtils {

    /**
     * Returns a single result if there is a single result, or null if there is no result. If there is more
     * than one result, then an the NonUniqueResultException is thrown.
     *
     * @param <T>
     * @param query
     * @return
     */
    public static <T> T getSingleResultOrNull(final TypedQuery<T> query) {
        query.setMaxResults(2);
        final List<T> list = query.getResultList();
        if (list == null || list.isEmpty()) {
            return null;
        } else if (list.size() == 1) {
            return list.get(0);
        }
        throw new NonUniqueResultException();
    }
}
