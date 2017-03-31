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
import com.coinblesk.server.entity.Account;
import com.coinblesk.server.entity.TimeLockedAddressEntity;
import com.coinblesk.server.exceptions.InvalidLockTimeException;
import com.coinblesk.server.exceptions.UserNotFoundException;
import com.coinblesk.server.utilTest.CoinbleskTest;
import com.coinblesk.server.utilTest.KeyTestUtil;
import com.github.springtestdbunit.DbUnitTestExecutionListener;
import com.github.springtestdbunit.annotation.DatabaseSetup;
import com.github.springtestdbunit.annotation.DatabaseTearDown;
import org.bitcoinj.core.ECKey;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestExecutionListeners;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

/**
 *
 * @author Thomas Bocek
 * @author Andreas Albrecht
 */
@TestExecutionListeners(listeners = DbUnitTestExecutionListener.class, mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
public class AccountServiceTest extends CoinbleskTest {

	@Autowired
	private AccountService accountService;

	@Test
	@DatabaseSetup("/EmptyDatabase.xml")
	@DatabaseTearDown("/EmptyDatabase.xml")
	public void testAddKey() throws Exception {
		ECKey ecKeyClient = new ECKey();
		ECKey ecKeyServer = new ECKey();

		boolean retVal = accountService.storeKeysAndAddress(ecKeyClient.getPubKey(), ecKeyServer.getPubKey(),
				ecKeyServer.getPrivKeyBytes()).element0();
		Assert.assertTrue(retVal);
		// adding again should fail
		retVal = accountService.storeKeysAndAddress(ecKeyClient.getPubKey(), ecKeyServer.getPubKey(),
				ecKeyServer.getPrivKeyBytes()).element0();
		Assert.assertFalse(retVal);
	}

	@Test
	@DatabaseSetup("/EmptyDatabase.xml")
	@DatabaseTearDown("/EmptyDatabase.xml")
	public void testAddKey2() throws Exception {
		ECKey ecKeyClient = new ECKey();
		ECKey ecKeyServer = new ECKey();

		boolean retVal = accountService.storeKeysAndAddress(ecKeyClient.getPubKey(), ecKeyServer.getPubKey(),
				ecKeyServer.getPrivKeyBytes()).element0();
		Assert.assertTrue(retVal);
		retVal = accountService.storeKeysAndAddress(ecKeyClient.getPubKey(), ecKeyServer.getPubKey(),
				ecKeyServer.getPrivKeyBytes()).element0();
		Assert.assertFalse(retVal);

		Account account = accountService.getByClientPublicKey(ecKeyClient.getPubKey());
		Assert.assertNotNull(account);

		account = accountService.getByClientPublicKey(ecKeyClient.getPubKey());
		Assert.assertNotNull(account);
		//
		List<ECKey> list = accountService.getPublicECKeysByClientPublicKey(ecKeyClient.getPubKey());
		Assert.assertEquals(2, list.size());
		Assert.assertArrayEquals(list.get(0).getPubKey(), ecKeyClient.getPubKey());
		Assert.assertArrayEquals(list.get(1).getPubKey(), ecKeyServer.getPubKey());
	}

	@Test
	@DatabaseSetup("/EmptyDatabase.xml")
	@DatabaseSetup("/keys.xml")
	@DatabaseTearDown("/EmptyDatabase.xml")
	public void testGetTimeLockedAddress_EmptyResult() {
		long lockTime = 123456;
		ECKey clientKey = KeyTestUtil.ALICE_CLIENT;

		Account account = accountService.getByClientPublicKey(clientKey.getPubKey());

		TimeLockedAddress address = new TimeLockedAddress(clientKey.getPubKey(), account.serverPublicKey(), lockTime);
		// do not store -> empty result

		TimeLockedAddress fromDB = accountService.getTimeLockedAddressByAddressHash(address.getAddressHash());
		assertNull(fromDB);
	}

	@Test
	@DatabaseSetup("/EmptyDatabase.xml")
	@DatabaseSetup("/keys.xml")
	@DatabaseTearDown("/EmptyDatabase.xml")
	public void testStoreAndGetTimeLockedAddress() throws InvalidLockTimeException, UserNotFoundException {
		long lockTime = Instant.now().plus(Duration.ofDays(90)).getEpochSecond();
		ECKey clientKey = KeyTestUtil.ALICE_CLIENT;

		TimeLockedAddress intoDB = accountService.createTimeLockedAddress(clientKey, lockTime);
		assertNotNull(intoDB);

		TimeLockedAddress fromDB = accountService.getTimeLockedAddressByAddressHash(intoDB.getAddressHash());
		assertNotNull(fromDB);

		assertEquals(intoDB, fromDB);

		Account account = accountService.getByClientPublicKey(clientKey.getPubKey());
		assertTrue(account.timeLockedAddresses().stream()
				.anyMatch( entity -> entity.toTimeLockedAddress().equals(fromDB)));
	}

	@Test
	@DatabaseSetup("/EmptyDatabase.xml")
	@DatabaseSetup("/keys.xml")
	@DatabaseTearDown("/EmptyDatabase.xml")
	public void testStoreAndGetTimeLockedAddresses() throws InvalidLockTimeException, UserNotFoundException {
		ECKey clientKey = KeyTestUtil.ALICE_CLIENT;

		Account account = accountService.getByClientPublicKey(clientKey.getPubKey());

		Long locktime1 = Instant.now().plus(Duration.ofDays(30)).getEpochSecond();
		TimeLockedAddress addressEntity_1 = accountService.createTimeLockedAddress(clientKey, locktime1);
		assertNotNull(addressEntity_1);

		Long locktime2 = Instant.now().plus(Duration.ofDays(60)).getEpochSecond();
		TimeLockedAddress addressEntity_2 = accountService.createTimeLockedAddress(clientKey, locktime2);
		assertNotNull(addressEntity_2);

		List<TimeLockedAddressEntity> fromDB = accountService.getTimeLockedAddressesByClientPublicKey(
				clientKey.getPubKey());

		assertNotNull(fromDB);
		assertTrue(fromDB.size() == 2);
		assertTrue(fromDB.stream().map(e -> e.toTimeLockedAddress()).anyMatch(e -> e.equals(addressEntity_1)));
		assertTrue(fromDB.stream().map(e -> e.toTimeLockedAddress()).anyMatch(e -> e.equals(addressEntity_2)));

		account = accountService.getByClientPublicKey(clientKey.getPubKey());
		assertTrue(account.timeLockedAddresses().containsAll(fromDB));
	}
}
