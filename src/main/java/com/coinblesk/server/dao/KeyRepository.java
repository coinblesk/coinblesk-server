package com.coinblesk.server.dao;

import com.coinblesk.server.entity.Account;
import org.springframework.data.repository.CrudRepository;

public interface KeyRepository extends CrudRepository<Account, Long> {
	Account findByClientPublicKey(final byte[] clientPublicKey);
}
