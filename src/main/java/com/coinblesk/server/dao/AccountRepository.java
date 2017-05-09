package com.coinblesk.server.dao;

import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

import com.coinblesk.server.entity.Account;

public interface AccountRepository extends CrudRepository<Account, Long> {
	Account findByClientPublicKey(final byte[] clientPublicKey);
	List<Account> findByBroadcastBeforeLessThanAndChannelTransactionNotNull(long timestamp);
	List<Account> findByLockedIsTrue();

	@Query("SELECT SUM(a.virtualBalance) FROM ACCOUNT a")
	public Long getSumOfAllVirtualBalances();

}
