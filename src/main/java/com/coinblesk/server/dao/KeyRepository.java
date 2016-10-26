package com.coinblesk.server.dao;

import com.coinblesk.server.entity.Keys;
import org.springframework.data.repository.CrudRepository;

public interface KeyRepository extends CrudRepository<Keys, Long>
{
    Keys findByClientPublicKey(final byte[] clientPublicKey);
}
