package com.coinblesk.server.dao;

import com.coinblesk.server.entity.Account;
import org.springframework.data.repository.CrudRepository;

import java.util.List;

public interface AccountRepository extends CrudRepository<Account, Long> {
	Account findByClientPublicKey(final byte[] clientPublicKey);
	List<Account> findByBroadcastBeforeLessThanAndChannelTransactionNotNull(long timestamp);
	List<Account> findByLockedIsTrue();
}
