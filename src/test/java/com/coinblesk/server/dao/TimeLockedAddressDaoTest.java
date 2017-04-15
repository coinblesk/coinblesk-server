package com.coinblesk.server.dao;

import com.coinblesk.server.entity.Account;
import com.coinblesk.server.entity.TimeLockedAddressEntity;
import org.bitcoinj.core.ECKey;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.time.Duration;
import java.time.Instant;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNotNull;

@RunWith(SpringRunner.class)
@DataJpaTest
public class TimeLockedAddressDaoTest {

	@Autowired
	private TimeLockedAddressRepository timeLockedAddressRepository;

	@Autowired
	private AccountRepository accountRepository;

	@Test
	public void testGetNewestTimeLockedAddress() {
		final ECKey clientKey = new ECKey();
		final ECKey serverKey = new ECKey();

		Account account = accountRepository.save(new Account().clientPublicKey(clientKey.getPubKey()).serverPrivateKey
			(serverKey.getPrivKeyBytes()).serverPublicKey(serverKey.getPubKey()).timeCreated(Instant.now()
			.getEpochSecond()));

		timeLockedAddressRepository.save(new TimeLockedAddressEntity().setAccount(account).setLockTime(Instant.now()
			.plus(Duration.ofMinutes(2)).getEpochSecond()).setAddressHash("firstToExpire".getBytes()).setRedeemScript
			("fake".getBytes()).setTimeCreated(Instant.now().getEpochSecond()));
		timeLockedAddressRepository.save(new TimeLockedAddressEntity().setAccount(account).setLockTime(Instant.now()
			.plus(Duration.ofMinutes(10)).getEpochSecond()).setAddressHash("lastToExpire".getBytes()).setRedeemScript
			("fake".getBytes()).setTimeCreated(Instant.now().getEpochSecond()));
		timeLockedAddressRepository.save(new TimeLockedAddressEntity().setAccount(account).setLockTime(Instant.now()
			.plus(Duration.ofMinutes(8)).getEpochSecond()).setAddressHash("middleToExpire".getBytes()).setRedeemScript
			("fake".getBytes()).setTimeCreated(Instant.now().getEpochSecond()));

		// Make sure we get the address with the longest lock time
		TimeLockedAddressEntity tlaEntity = timeLockedAddressRepository
			.findTopByAccount_clientPublicKeyOrderByLockTimeDesc(clientKey.getPubKey());
		assertNotNull(tlaEntity);
		assertArrayEquals("lastToExpire".getBytes(), tlaEntity.getAddressHash());
	}

}
