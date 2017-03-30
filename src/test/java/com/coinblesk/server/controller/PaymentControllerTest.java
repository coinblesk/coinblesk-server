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
package com.coinblesk.server.controller;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Random;

import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.Utils;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.coinblesk.bitcoin.TimeLockedAddress;
import com.coinblesk.json.v1.BaseTO;
import com.coinblesk.json.v1.KeyTO;
import com.coinblesk.json.v1.TimeLockedAddressTO;
import com.coinblesk.json.v1.Type;
import com.coinblesk.server.service.AccountService;
import com.coinblesk.server.utilTest.CoinbleskTest;
import com.coinblesk.server.utilTest.KeyTestUtil;
import com.coinblesk.server.utilTest.RESTUtils;
import com.coinblesk.util.BitcoinUtils;
import com.coinblesk.util.SerializeUtils;
import com.github.springtestdbunit.annotation.DatabaseSetup;
import com.github.springtestdbunit.annotation.DatabaseTearDown;

/**
 *
 * @author Andreas Albrecht
 */
public class PaymentControllerTest extends CoinbleskTest {

	public static final String URL_KEY_EXCHANGE = "/v1/payment/key-exchange";
	public static final String URL_CREATE_TIME_LOCKED_ADDRESS = "/v1/payment/createTimeLockedAddress";
	public static final String URL_SIGN_VERIFY = "/v1/payment/signverify";

	@Autowired
	private WebApplicationContext webAppContext;

	@Autowired
	private AccountService accountService;

	private static MockMvc mockMvc;

	@Before
	public void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(webAppContext).build();
	}

	@Test
	@DatabaseSetup("/EmptyDatabase.xml")
	@DatabaseTearDown("/EmptyDatabase.xml")
	public void testKeyExchange_NoContent() throws Exception {
		mockMvc
			.perform(post(URL_KEY_EXCHANGE).secure(true))
			.andExpect(status().is4xxClientError());
	}

	@Test
	@DatabaseSetup("/EmptyDatabase.xml")
	@DatabaseTearDown("/EmptyDatabase.xml")
	public void testKeyExchange_EmptyRequest() throws Exception {
		KeyTO requestTO = new KeyTO();
		KeyTO response = requestKeyExchange(requestTO);
		assertFalse(response.isSuccess());
		assertEquals(response.type(), Type.INPUT_MISMATCH);
		assertNull(response.publicKey());
	}

	@Test
	@DatabaseSetup("/EmptyDatabase.xml")
	@DatabaseTearDown("/EmptyDatabase.xml")
	public void testKeyExchange_NoPubKey() throws Exception {
		KeyTO requestTO = new KeyTO();
		requestTO.currentDate(System.currentTimeMillis());

		KeyTO response = requestKeyExchange(requestTO);
		assertFalse(response.isSuccess());
		assertEquals(response.type(), Type.INPUT_MISMATCH);
		assertNull(response.publicKey());
	}

	@Test
	@DatabaseSetup("/EmptyDatabase.xml")
	@DatabaseTearDown("/EmptyDatabase.xml")
	public void testKeyExchange_InvalidPubKey() throws Exception {
		KeyTO requestTO = new KeyTO()
			.currentDate(System.currentTimeMillis())
			.publicKey("invalid key".getBytes());

		KeyTO response = requestKeyExchange(requestTO);
		assertFalse(response.isSuccess());
		assertEquals(response.type(), Type.INPUT_MISMATCH);
		assertNull(response.publicKey());
	}

	@Test
	@DatabaseSetup("/EmptyDatabase.xml")
	@DatabaseTearDown("/EmptyDatabase.xml")
	public void testKeyExchange() throws Exception {
		ECKey key = new ECKey();
		KeyTO requestTO = new KeyTO()
			.currentDate(System.currentTimeMillis())
			.publicKey(key.getPubKey());

		KeyTO response = requestKeyExchange(requestTO);
		assertTrue(response.isSuccess());
		assertEquals(response.type(), Type.SUCCESS);
		assertNotNull(response.publicKey());
		assertTrue(ECKey.isPubKeyCanonical(response.publicKey()));

		// throws if invalid
		ECKey serverPubKey = ECKey.fromPublicOnly(response.publicKey());
		assertTrue(SerializeUtils.verifyJSONSignature(response, serverPubKey));
	}

	@Test
	@DatabaseSetup("/EmptyDatabase.xml")
	@DatabaseTearDown("/EmptyDatabase.xml")
	public void testKeyExchange_ExistingPubKey() throws Exception {
		ECKey key = new ECKey();
		KeyTO requestTO = new KeyTO()
			.currentDate(System.currentTimeMillis())
			.publicKey(key.getPubKey());

		KeyTO response = requestKeyExchange(requestTO);
		assertTrue(response.isSuccess());
		assertEquals(response.type(), Type.SUCCESS);
		assertNotNull(response.publicKey());

		// throws if invalid
		ECKey serverPubKey = ECKey.fromPublicOnly(response.publicKey());
		assertTrue(SerializeUtils.verifyJSONSignature(response, serverPubKey));

		// execute 2nd request - server should respond with same key.
		KeyTO request2TO = new KeyTO()
			.currentDate(System.currentTimeMillis())
			.publicKey(key.getPubKey());
		KeyTO response_2 = requestKeyExchange(request2TO);
		assertTrue(response_2.isSuccess());
		assertEquals(response_2.type(), Type.SUCCESS_BUT_KEY_ALREADY_EXISTS);
		assertNotNull(response_2.publicKey());
		assertArrayEquals(response_2.publicKey(), response.publicKey());
		assertTrue(SerializeUtils.verifyJSONSignature(response_2, serverPubKey));
	}

	private KeyTO requestKeyExchange(KeyTO requestTO) throws Exception {
		String jsonTO = SerializeUtils.GSON.toJson(requestTO);
		return RESTUtils.postRequest(mockMvc, URL_KEY_EXCHANGE, jsonTO, KeyTO.class);
	}

	@Test
	@DatabaseSetup("/EmptyDatabase.xml")
	@DatabaseTearDown("/EmptyDatabase.xml")
	public void testCreateTimeLockedAddress_NoContent() throws Exception {
		mockMvc
			.perform(post(URL_CREATE_TIME_LOCKED_ADDRESS).secure(true))
			.andExpect(status().is4xxClientError());
	}

	@Test
	@DatabaseSetup("/EmptyDatabase.xml")
	@DatabaseTearDown("/EmptyDatabase.xml")
	public void testCreateTimeLockedAddress_EmptyRequest() throws Exception {
		TimeLockedAddressTO requestTO = new TimeLockedAddressTO();
		TimeLockedAddressTO response = requestCreateTimeLockedAddress(requestTO);
		assertFalse(response.isSuccess());
		assertEquals(response.type(), Type.INPUT_MISMATCH);
		assertNull(response.timeLockedAddress());
	}

	@Test
	@DatabaseSetup("/EmptyDatabase.xml")
	@DatabaseTearDown("/EmptyDatabase.xml")
	public void testCreateTimeLockedAddress_NoPublicKey() throws Exception {
		ECKey clientKey = new ECKey();
		TimeLockedAddressTO requestTO = new TimeLockedAddressTO()
				.currentDate(System.currentTimeMillis());
		SerializeUtils.signJSON(requestTO, clientKey);

		TimeLockedAddressTO response = requestCreateTimeLockedAddress(requestTO);
		assertFalse(response.isSuccess());
		assertEquals(response.type(), Type.SERVER_ERROR);
		assertNull(response.timeLockedAddress());
	}

	@Test
	@DatabaseSetup("/EmptyDatabase.xml")
	@DatabaseTearDown("/EmptyDatabase.xml")
	public void testCreateTimeLockedAddress_NoSignature() throws Exception {
		ECKey clientKey = new ECKey();
		TimeLockedAddressTO requestTO = createSignedTimeLockedAddressTO(clientKey);
		requestTO.messageSig(null); // Do not sign

		TimeLockedAddressTO responseTO = requestCreateTimeLockedAddress(requestTO);
		assertFalse(responseTO.isSuccess());
		assertEquals(responseTO.type(), Type.INPUT_MISMATCH);
	}

	@Test
	@DatabaseSetup("/EmptyDatabase.xml")
	@DatabaseTearDown("/EmptyDatabase.xml")
	public void testCreateTimeLockedAddress_WrongSignature() throws Exception {
		ECKey clientKey = new ECKey();
		ECKey wrongKey = new ECKey();
		TimeLockedAddressTO requestTO = new TimeLockedAddressTO()
				.currentDate(Utils.currentTimeMillis())
				.publicKey(clientKey.getPubKey());
		SerializeUtils.signJSON(requestTO, wrongKey);

		TimeLockedAddressTO responseTO = requestCreateTimeLockedAddress(requestTO);
		assertFalse(responseTO.isSuccess());
		assertEquals(responseTO.type(), Type.JSON_SIGNATURE_ERROR);
	}

	@Test
	@DatabaseSetup("/EmptyDatabase.xml")
	@DatabaseTearDown("/EmptyDatabase.xml")
	public void testCreateTimeLockedAddress_WrongECKey() throws Exception {
		TimeLockedAddressTO requestTO = new TimeLockedAddressTO()
				.publicKey("helloworld".getBytes())
				.currentDate(System.currentTimeMillis());
		SerializeUtils.signJSON(requestTO, new ECKey());

		TimeLockedAddressTO response = requestCreateTimeLockedAddress(requestTO);
		assertFalse(response.isSuccess());
		assertEquals(response.type(), Type.SERVER_ERROR);
		assertNull(response.timeLockedAddress());
	}

	@Test
	@DatabaseSetup("/EmptyDatabase.xml")
	@DatabaseTearDown("/EmptyDatabase.xml")
	public void testCreateTimeLockedAddress_NewAddress_ClientUnknown() throws Exception {
		ECKey clientKey = new ECKey();
		assertNull(accountService.getByClientPublicKey(clientKey.getPubKey()) ); // not known yet
		TimeLockedAddressTO requestTO = createSignedTimeLockedAddressTO(clientKey);
		TimeLockedAddressTO responseTO = requestCreateTimeLockedAddress(requestTO);
		assertFalse(responseTO.isSuccess());
		assertNull(responseTO.timeLockedAddress());
		assertEquals(responseTO.type(), Type.KEYS_NOT_FOUND);
	}

	@Test
	@DatabaseSetup("/EmptyDatabase.xml")
	@DatabaseSetup("/keys.xml")
	@DatabaseTearDown("/EmptyDatabase.xml")
	public void testCreateTimeLockedAddress_NoLockTime() throws Exception {
		ECKey clientKey = KeyTestUtil.ALICE_CLIENT;
		TimeLockedAddressTO requestTO = new TimeLockedAddressTO()
					.currentDate(Utils.currentTimeMillis())
					.publicKey(clientKey.getPubKey());
		SerializeUtils.signJSON(requestTO, clientKey);

		TimeLockedAddressTO responseTO = requestCreateTimeLockedAddress(requestTO);
		assertFalse(responseTO.isSuccess());
		assertNull(responseTO.timeLockedAddress());
		assertEquals(responseTO.type(), Type.LOCKTIME_ERROR);
	}

	@Test
	@DatabaseSetup("/EmptyDatabase.xml")
	@DatabaseSetup("/keys.xml")
	@DatabaseTearDown("/EmptyDatabase.xml")
	public void testCreateTimeLockedAddress_LockTimeByBlock() throws Exception {
		ECKey clientKey = KeyTestUtil.ALICE_CLIENT;
		long lockTime = Transaction.LOCKTIME_THRESHOLD - 1000;
		TimeLockedAddressTO requestTO = new TimeLockedAddressTO()
					.currentDate(Utils.currentTimeMillis())
					.publicKey(clientKey.getPubKey())
					.lockTime(lockTime);
		SerializeUtils.signJSON(requestTO, clientKey);

		TimeLockedAddressTO responseTO = requestCreateTimeLockedAddress(requestTO);
		assertFalse(responseTO.isSuccess());
		assertNull(responseTO.timeLockedAddress());
		assertEquals(responseTO.type(), Type.LOCKTIME_ERROR);
	}

	@Test
	@DatabaseSetup("/EmptyDatabase.xml")
	@DatabaseSetup("/keys.xml")
	@DatabaseTearDown("/EmptyDatabase.xml")
	public void testCreateTimeLockedAddress_LockTimeInPast() throws Exception {
		ECKey clientKey = KeyTestUtil.ALICE_CLIENT;
		long lockTime = Utils.currentTimeSeconds() - 1;
		TimeLockedAddressTO requestTO = new TimeLockedAddressTO()
					.currentDate(Utils.currentTimeMillis())
					.publicKey(clientKey.getPubKey())
					.lockTime(lockTime);
		SerializeUtils.signJSON(requestTO, clientKey);

		TimeLockedAddressTO responseTO = requestCreateTimeLockedAddress(requestTO);
		assertFalse(responseTO.isSuccess());
		assertNull(responseTO.timeLockedAddress());
		assertEquals(responseTO.type(), Type.LOCKTIME_ERROR);
	}

	@Test
	@DatabaseSetup("/EmptyDatabase.xml")
	@DatabaseSetup("/keys.xml")
	@DatabaseTearDown("/EmptyDatabase.xml")
	public void testCreateTimeLockedAddress_RequestTwice() throws Exception {
		ECKey clientKey = KeyTestUtil.ALICE_CLIENT;
		long lockTime = Utils.currentTimeSeconds() + 100;
		TimeLockedAddressTO requestTO = new TimeLockedAddressTO()
					.currentDate(Utils.currentTimeMillis())
					.publicKey(clientKey.getPubKey())
					.lockTime(lockTime);
		SerializeUtils.signJSON(requestTO, clientKey);

		TimeLockedAddress address1, address2;
		// 1st request
		TimeLockedAddressTO responseTO = requestCreateTimeLockedAddress(requestTO);
		assertTrue(responseTO.isSuccess());
		assertNotNull(responseTO.timeLockedAddress());
		assertEquals(responseTO.type(), Type.SUCCESS);
		address1 = responseTO.timeLockedAddress();
		assertVerifyJSONSig(responseTO, ECKey.fromPublicOnly(address1.getServerPubKey()));

		// 2nd request
		responseTO = requestCreateTimeLockedAddress(requestTO);
		assertTrue(responseTO.isSuccess());
		assertNotNull(responseTO.timeLockedAddress());
		assertEquals(responseTO.type(), Type.SUCCESS_BUT_ADDRESS_ALREADY_EXISTS);
		address2 = responseTO.timeLockedAddress();
		assertVerifyJSONSig(responseTO, ECKey.fromPublicOnly(address2.getServerPubKey()));

		// must receive same address
		assertEquals(address1, address2);
	}

	@Test
	@DatabaseSetup("/EmptyDatabase.xml")
	@DatabaseSetup("/keys.xml")
	@DatabaseTearDown("/EmptyDatabase.xml")
	public void testCreateTimeLockedAddress_NewAddress_ClientKnown() throws Exception {
		// keys are already stored in DB
		ECKey clientKey = KeyTestUtil.ALICE_CLIENT;
		ECKey serverKey = KeyTestUtil.ALICE_SERVER;

		TimeLockedAddressTO requestTO = createSignedTimeLockedAddressTO(clientKey);
		TimeLockedAddress expectedAddress = new TimeLockedAddress(
				clientKey.getPubKey(), serverKey.getPubKey(), requestTO.lockTime());
		assertFalse(accountService.addressExists(expectedAddress.getAddressHash()));

		TimeLockedAddressTO responseTO = requestCreateTimeLockedAddress(requestTO);
		assertTrue(responseTO.isSuccess());
		assertVerifyJSONSig(responseTO, serverKey);

		TimeLockedAddress address = responseTO.timeLockedAddress();
		assertVerifyJSONSig(responseTO, ECKey.fromPublicOnly(address.getServerPubKey()));
		assertNotNull(address);
		assertNotNull(address.getAddressHash());
		assertNotNull(address.getClientPubKey());
		assertNotNull(address.getServerPubKey());
		assertTrue(BitcoinUtils.isLockTimeByTime(address.getLockTime()));

		assertArrayEquals(address.getClientPubKey(), clientKey.getPubKey());
		assertArrayEquals(address.getServerPubKey(), serverKey.getPubKey());
		assertEquals(address.getLockTime(), requestTO.lockTime());

		assertEquals(expectedAddress, address);
		assertTrue(accountService.addressExists(address.getAddressHash()));
	}

	private TimeLockedAddressTO requestCreateTimeLockedAddress(TimeLockedAddressTO requestTO) throws Exception {
		String jsonTO = SerializeUtils.GSON.toJson(requestTO);
		return RESTUtils.postRequest(mockMvc, URL_CREATE_TIME_LOCKED_ADDRESS, jsonTO, TimeLockedAddressTO.class);
	}

	private TimeLockedAddressTO createSignedTimeLockedAddressTO(ECKey clientKey) {
		// lock time between now and (now+1year)
		long lockTime = Utils.currentTimeSeconds() + new Random().nextInt(365 * 24 * 60 * 60);
		TimeLockedAddressTO requestTO = new TimeLockedAddressTO()
				.currentDate(Utils.currentTimeMillis())
																	.publicKey(clientKey.getPubKey())
																	.lockTime(lockTime);
		SerializeUtils.signJSON(requestTO, clientKey);
		return requestTO;
	}

	private static <K extends BaseTO<?>> boolean assertVerifyJSONSig(K k, ECKey key) {
		boolean result = SerializeUtils.verifyJSONSignature(k, key);
		assertTrue(result);
		return result;
	}
}
