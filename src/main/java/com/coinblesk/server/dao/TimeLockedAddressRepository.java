package com.coinblesk.server.dao;

import java.util.Collection;
import java.util.List;

import org.springframework.data.repository.CrudRepository;

import com.coinblesk.server.entity.TimeLockedAddressEntity;

public interface TimeLockedAddressRepository extends CrudRepository<TimeLockedAddressEntity, Long> {

	TimeLockedAddressEntity findByAddressHash(byte[] addressHash);

	List<TimeLockedAddressEntity> findByAddressHashIn(Collection<byte[]> addressHashes);

}
