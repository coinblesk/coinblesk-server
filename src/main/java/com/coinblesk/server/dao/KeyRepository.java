package com.coinblesk.server.dao;

import org.springframework.data.repository.CrudRepository;

import com.coinblesk.server.entity.Keys;

public interface KeyRepository extends CrudRepository<Keys, Long> {
	Keys findByClientPublicKey(final byte[] clientPublicKey);
}
