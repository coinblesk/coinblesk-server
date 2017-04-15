package com.coinblesk.server.dao;

import com.coinblesk.server.entity.Account;
import com.coinblesk.server.entity.TimeLockedAddressEntity;
import org.bitcoinj.core.ECKey;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.time.Instant;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNotNull;

@RunWith(SpringRunner.class)
@DataJpaTest
public class TimeLockedAddressDaoTest {

	@Autowired
	TimeLockedAddressRepository timeLockedAddressRepository;

	@Autowired
	AccountRepository accountRepository;

	@Test
	public void testGetNewestTimeLockedAddress() {
		final ECKey clientKey = new ECKey();
		final ECKey serverKey = new ECKey();

		Account account = accountRepository.save(new Account()
			.clientPublicKey(clientKey.getPubKey())
			.serverPrivateKey(serverKey.getPrivKeyBytes())
			.serverPublicKey(serverKey.getPubKey())
			.timeCreated(Instant.now().getEpochSecond()));

		timeLockedAddressRepository.save(new TimeLockedAddressEntity()
			.setAccount(account)
			.setLockTime(0)
			.setAddressHash("old".getBytes())
			.setRedeemScript("fake".getBytes())
			.setTimeCreated(100)
			);
		timeLockedAddressRepository.save(new TimeLockedAddressEntity()
			.setAccount(account)
			.setLockTime(0)
			.setAddressHash("new".getBytes())
			.setRedeemScript("fake".getBytes())
			.setTimeCreated(200)
		);

		// Make sure we get the newest address
		TimeLockedAddressEntity tlaEntity =
			timeLockedAddressRepository.findTopByAccount_clientPublicKeyOrderByTimeCreatedDesc(clientKey.getPubKey());
		assertNotNull(tlaEntity);
		assertArrayEquals("new".getBytes(), tlaEntity.getAddressHash());
	}

}
