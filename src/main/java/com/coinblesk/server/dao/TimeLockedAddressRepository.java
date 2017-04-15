package com.coinblesk.server.dao;

import com.coinblesk.server.entity.TimeLockedAddressEntity;
import org.springframework.data.repository.CrudRepository;

import java.util.Collection;
import java.util.List;

public interface TimeLockedAddressRepository extends CrudRepository<TimeLockedAddressEntity, Long> {

	TimeLockedAddressEntity findByAddressHash(byte[] addressHash);

	List<TimeLockedAddressEntity> findByAddressHashIn(Collection<byte[]> addressHashes);

	// Get oldest time locked address for given public key
	TimeLockedAddressEntity findTopByAccount_clientPublicKeyOrderByLockTimeDesc(byte[] clientPublicKey);
}
