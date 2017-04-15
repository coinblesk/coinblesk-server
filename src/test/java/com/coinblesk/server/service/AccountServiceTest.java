/*
 * Copyright 2016 The Coinblesk team and the CSG Group at University of Zurich
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.coinblesk.server.service;

import com.coinblesk.bitcoin.TimeLockedAddress;
import com.coinblesk.server.config.AppConfig;
import com.coinblesk.server.dao.AccountRepository;
import com.coinblesk.server.entity.Account;
import com.coinblesk.server.entity.TimeLockedAddressEntity;
import com.coinblesk.server.exceptions.InvalidLockTimeException;
import com.coinblesk.server.exceptions.UserNotFoundException;
import com.coinblesk.server.utilTest.CoinbleskTest;
import org.assertj.core.api.Assertions;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.Utils;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import javax.persistence.EntityManager;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.Assert.*;

/**
 * @author Sebastian Stephan
 */
public class AccountServiceTest extends CoinbleskTest {

	@Autowired
	private AccountService accountService;

	@Autowired
	private AccountRepository accountRepository;

	@Autowired
	private EntityManager em;

	@Autowired
	private AppConfig appConfig;

	@Test
	public void createAccountReturnsServerPublicKey() {
		final ECKey serverPublicKey = accountService.createAcount(new ECKey());
		Assert.assertNotNull(serverPublicKey);
		Assert.assertNotNull(serverPublicKey.getPubKey());
	}

	@Test
	public void createAccountSavesNewAccount() {
		ECKey clientKey = new ECKey();
		accountService.createAcount(clientKey);
		assertNotNull(accountRepository.findByClientPublicKey(clientKey.getPubKey()));
	}

	@Test
	public void createdAccountContainsReturnedServerPublicKey() {
		ECKey clientKey = new ECKey();
		ECKey returnedServerPublicKey = accountService.createAcount(clientKey);
		Account account = accountRepository.findByClientPublicKey(clientKey.getPubKey());
		assertArrayEquals("Returned serverKey must be the one saved",
			returnedServerPublicKey.getPubKey(), account.serverPublicKey());
	}

	@Test
	public void createdAccountHasBalanceZero() {
		ECKey clientKey = new ECKey();
		accountService.createAcount(clientKey);
		Account account = accountRepository.findByClientPublicKey(clientKey.getPubKey());
		assertEquals("Initial virtual balance must be zero", account.virtualBalance(), 0);
	}

	@Test
	public void createdAccountHasZeroNonce() {
		ECKey clientKey = new ECKey();
		accountService.createAcount(clientKey);
		Account account = accountRepository.findByClientPublicKey(clientKey.getPubKey());
		assertEquals("Initial nonce must be zero", account.nonce(), 0);
	}

	@Test
	public void createdAccountContainsTimeCreated() {
		ECKey clientKey = new ECKey();
		accountService.createAcount(clientKey);
		Account account = accountRepository.findByClientPublicKey(clientKey.getPubKey());
		assertTrue("timeCreated must not be in the future", account.timeCreated() <= Instant.now().getEpochSecond());
		assertTrue("timeCreated must be somthing in the last 10 seconds",
			account.timeCreated() >= Instant.now().minus(Duration.ofSeconds(10)).getEpochSecond());
	}

	@Test
	public void creatingAccountTwiceOnlyInsertsOnce() {
		ECKey clientKey = new ECKey();
		accountService.createAcount(clientKey);
		accountService.createAcount(clientKey);
		List resultList = em.createQuery("SELECT a FROM ACCOUNT a WHERE CLIENT_PUBLIC_KEY = :pubKey")
			.setParameter("pubKey", clientKey.getPubKey())
			.getResultList();
		assertEquals(resultList.size(), 1);
	}

	@Test
	public void creatingAccountTwiceReturnsSameServerKey() {
		ECKey clientKey = new ECKey();
		ECKey serverKey1 = accountService.createAcount(clientKey);
		ECKey serverKey2 = accountService.createAcount(clientKey);
		assertEquals(serverKey1, serverKey2);
	}

	private long validLocktime() {
		final long minLockTime = Instant.now().getEpochSecond() + appConfig.getMinimumLockTimeSeconds();
		final long maxLockTime = Instant.now().plus(Duration.ofDays(appConfig.getMaximumLockTimeDays())).getEpochSecond();
		return (minLockTime + maxLockTime) / 2;
	}

	@Test
	public void createTimeLockedAddressFailsWithNullParameters() {
		Assertions.assertThatThrownBy(() -> {
			accountService.createTimeLockedAddress(null, validLocktime());
		}).isInstanceOf(RuntimeException.class);
	}

	@Test
	public void createTimeLockedAddressFailsWithUnknownUser() {
		Assertions.assertThatThrownBy(() -> accountService.createTimeLockedAddress(new ECKey(), validLocktime())).isInstanceOf(UserNotFoundException.class);
	}

	@Test
	public void createTimeLockedAddressFailsWithInvalidLocktime() {
		// Zero locktime
		Assertions.assertThatThrownBy(() -> accountService.createTimeLockedAddress(new ECKey(), 0L)).isInstanceOf(InvalidLockTimeException.class);

		// Locktime that could be interpreted as block height
		Assertions.assertThatThrownBy(() -> accountService.createTimeLockedAddress(new ECKey(), Transaction.LOCKTIME_THRESHOLD - 1)).isInstanceOf(InvalidLockTimeException.class);

		// In the past
		Assertions.assertThatThrownBy(() -> accountService.createTimeLockedAddress(new ECKey(), Instant.now().minus(Duration.ofDays(1)).getEpochSecond())).isInstanceOf(InvalidLockTimeException.class);

		// Now
		Assertions.assertThatThrownBy(() -> accountService.createTimeLockedAddress(new ECKey(), Instant.now().getEpochSecond())).isInstanceOf(InvalidLockTimeException.class);

		// Not enough into the future
		Assertions.assertThatThrownBy(() -> accountService.createTimeLockedAddress(new ECKey(),
			Instant.now().getEpochSecond() + appConfig.getMinimumLockTimeSeconds() - 1)).isInstanceOf(InvalidLockTimeException.class);

		// Too far into the future
		Assertions.assertThatThrownBy(() -> accountService.createTimeLockedAddress(new ECKey(),
			Instant.now().plus(Duration.ofDays(appConfig.getMaximumLockTimeDays() + 1)).getEpochSecond())).isInstanceOf(InvalidLockTimeException.class);
	}

	@Test
	public void createTimeLockedAddressSucceedsWithKnownUser() throws InvalidLockTimeException, UserNotFoundException {
		final ECKey clientKey = new ECKey();
		accountService.createAcount(clientKey);
		TimeLockedAddress address = accountService.createTimeLockedAddress(clientKey, validLocktime())
			.getTimeLockedAddress();
		assertNotNull(address);
	}

	@Test
	public void createTimeLockedAddressIsSaved() throws InvalidLockTimeException, UserNotFoundException {
		final ECKey clientKey = new ECKey();
		accountService.createAcount(clientKey);
		TimeLockedAddress intoDB = accountService.createTimeLockedAddress(clientKey, validLocktime())
			.getTimeLockedAddress();
		TimeLockedAddress fromDB = accountService.getTimeLockedAddressByAddressHash(intoDB.getAddressHash());

		assertEquals(intoDB, fromDB);

	}

	@Test
	public void createTimeLockedAddressCreatesCorrentHash() throws InvalidLockTimeException, UserNotFoundException {
		final ECKey clientKey = new ECKey();
		accountService.createAcount(clientKey);
		TimeLockedAddress intoDB = accountService.createTimeLockedAddress(clientKey, validLocktime())
			.getTimeLockedAddress();

		List result = em.createQuery("SELECT a FROM TIME_LOCKED_ADDRESS a WHERE ADDRESS_HASH = :addressHash")
			.setParameter("addressHash", intoDB.getAddressHash()).getResultList();
		assertEquals(result.size(), 1);
		TimeLockedAddressEntity saved = (TimeLockedAddressEntity) result.get(0);

		// Address hash is 20 byte hash of redeem script
		Assert.assertArrayEquals(saved.getAddressHash(), Utils.sha256hash160(saved.getRedeemScript()));
	}

	@Test
	public void createTimeLockedAddressCreatesCorrentRedeemScript() throws InvalidLockTimeException, UserNotFoundException {
		final ECKey clientKey = new ECKey();
		accountService.createAcount(clientKey);
		TimeLockedAddress intoDB = accountService.createTimeLockedAddress(clientKey, validLocktime())
			.getTimeLockedAddress();

		List result = em.createQuery("SELECT a FROM TIME_LOCKED_ADDRESS a WHERE ADDRESS_HASH = :addressHash")
			.setParameter("addressHash", intoDB.getAddressHash()).getResultList();
		assertEquals(result.size(), 1);
		TimeLockedAddressEntity saved = (TimeLockedAddressEntity) result.get(0);

		TimeLockedAddress fromRedeemScript = TimeLockedAddress.fromRedeemScript(saved.getRedeemScript());
		assertEquals(intoDB, fromRedeemScript);
	}

}
