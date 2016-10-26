package com.coinblesk.server.dao;

import com.coinblesk.server.entity.TimeLockedAddressEntity;
import org.springframework.data.repository.CrudRepository;

import java.util.List;

public interface TimeLockedAddressRepository extends CrudRepository<TimeLockedAddressEntity, Long>
{
    TimeLockedAddressEntity findByAddressHash(byte[] addressHash);
    List<TimeLockedAddressEntity> findByKeys_ClientPublicKey(byte[] clientPublicKey);
}
