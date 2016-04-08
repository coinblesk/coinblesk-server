package ch.uzh.csg.coinblesk.server.dao;

import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Join;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;

import org.springframework.stereotype.Repository;

import ch.uzh.csg.coinblesk.server.entity.AddressEntity;
import ch.uzh.csg.coinblesk.server.entity.Keys;
import ch.uzh.csg.coinblesk.server.entity.TimeLockedAddressEntity;

@Repository
public class AddressDAO {
	
	@PersistenceContext()
    private EntityManager em;
	
	public <T> T save(final T enity) {
        return em.merge(enity);
    }

	// TODO: make generic --> AddressEntity instead of timelockedaddressentity.
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
	
	public AddressEntity findAddressByAddressHash(byte[] addressHash160) {
		final CriteriaBuilder cb = em.getCriteriaBuilder();
        final CriteriaQuery<AddressEntity> query = cb.createQuery(AddressEntity.class);
        final Root<AddressEntity> from = query.from(AddressEntity.class);
        final Predicate condition = cb.equal(from.get("addressHash"), addressHash160);
        final CriteriaQuery<AddressEntity> select = query.select(from).where(condition);
        final AddressEntity result = DAOUtils.getSingleResultOrNull(em.createQuery(select));
        return result;
	}

}
