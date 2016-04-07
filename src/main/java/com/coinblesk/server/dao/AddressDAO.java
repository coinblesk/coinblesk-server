package com.coinblesk.server.dao;

import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Join;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;

import org.springframework.stereotype.Repository;

import com.coinblesk.server.entity.Keys;
import com.coinblesk.server.entity.TimeLockedAddressEntity;

@Repository
public class AddressDAO {
	
	@PersistenceContext()
    private EntityManager em;
	
	public <T> T save(final T enity) {
        return em.merge(enity);
    }

	public TimeLockedAddressEntity findTimeLockedAddressByAddressHash(byte[] addressHash) {
		final CriteriaBuilder cb = em.getCriteriaBuilder();
        final CriteriaQuery<TimeLockedAddressEntity> query = cb.createQuery(TimeLockedAddressEntity.class);
        final Root<TimeLockedAddressEntity> from = query.from(TimeLockedAddressEntity.class);
        final Predicate condition = cb.equal(from.get("addressHash"), addressHash);
        CriteriaQuery<TimeLockedAddressEntity> select = query.select(from).where(condition);
        return DAOUtils.getSingleResultOrNull(em.createQuery(select));
	}

	public List<TimeLockedAddressEntity> findTimeLockedAddressesByClientPublicKey(byte[] publicKey) {
		final CriteriaBuilder cb = em.getCriteriaBuilder();
        final CriteriaQuery<TimeLockedAddressEntity> query = cb.createQuery(TimeLockedAddressEntity.class);
        final Root<TimeLockedAddressEntity> from = query.from(TimeLockedAddressEntity.class);
        Join<Keys, TimeLockedAddressEntity> keys = from.join("keys"); 
        query.where(cb.equal(keys.get("clientPublicKey"), publicKey));
        
        List<TimeLockedAddressEntity> result = em.createQuery(query).getResultList();
		return result;
	}

}
