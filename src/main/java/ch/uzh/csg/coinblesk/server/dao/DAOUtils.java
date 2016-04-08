package ch.uzh.csg.coinblesk.server.dao;

import java.util.List;

import javax.persistence.NonUniqueResultException;
import javax.persistence.TypedQuery;

/**
 *
 * @author Thomas Bocek
 */
public class DAOUtils {
    /**
     * Returns a single result if there is a single result, or null if there is
     * no result. If there is more than one result, then an the
     * NonUniqueResultException is thrown.
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
