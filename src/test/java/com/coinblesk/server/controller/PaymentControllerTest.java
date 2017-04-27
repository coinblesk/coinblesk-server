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

import com.coinblesk.bitcoin.TimeLockedAddress;
import com.coinblesk.server.config.AppConfig;
import com.coinblesk.server.dto.*;
import com.coinblesk.server.service.AccountService;
import com.coinblesk.server.utilTest.CoinbleskTest;
import com.coinblesk.server.utils.DTOUtils;
import com.coinblesk.util.BitcoinUtils;
import com.coinblesk.util.SerializeUtils;
import org.bitcoinj.core.ECKey;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.Duration;
import java.time.Instant;

import static org.junit.Assert.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @author Andreas Albrecht
 * @author Sebastian Stephan
 */
public class PaymentControllerTest extends CoinbleskTest {

	public static final String URL_KEY_EXCHANGE = "/payment/key-exchange";
	public static final String URL_CREATE_TIME_LOCKED_ADDRESS = "/payment/createTimeLockedAddress";
	private static MockMvc mockMvc;
	@Autowired
	private WebApplicationContext webAppContext;
	@Autowired
	private AccountService accountService;
	@Autowired
	private AppConfig appConfig;

	@Before
	public void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(webAppContext).build();
	}

	@Test
	public void testKeyExchange_NoContent() throws Exception {
		mockMvc.perform(post(URL_KEY_EXCHANGE)).andExpect(status().is4xxClientError());
	}

	@Test
	public void testKeyExchange_EmptyRequest() throws Exception {
		mockMvc.perform(post(URL_KEY_EXCHANGE).contentType(APPLICATION_JSON).content("{}")).andExpect(status()
			.is4xxClientError());
	}

	@Test
	public void testKeyExchange_NoPubKey() throws Exception {
		KeyExchangeRequestDTO requestDTO = new KeyExchangeRequestDTO("");
		mockMvc.perform(post(URL_KEY_EXCHANGE).contentType(APPLICATION_JSON).content(SerializeUtils.GSON.toJson
			(requestDTO))).andExpect(status().is4xxClientError());
	}

	@Test
	public void testKeyExchange_InvalidPubKey() throws Exception {
		String invalidPubKey = "f66b37dc2de5276a080bce77f9a6b0753f963e300c9a1f4557815ed49dc80fffb1";
		KeyExchangeRequestDTO requestDTO = new KeyExchangeRequestDTO(invalidPubKey);
		mockMvc.perform(post(URL_KEY_EXCHANGE).contentType(APPLICATION_JSON).content(SerializeUtils.GSON.toJson
			(requestDTO))).andExpect(status().is4xxClientError());
	}

	@Test
	public void testKeyExchange() throws Exception {
		ECKey clientKey = new ECKey();
		KeyExchangeRequestDTO requestDTO = new KeyExchangeRequestDTO(clientKey.getPublicKeyAsHex());
		String response = mockMvc.perform(post(URL_KEY_EXCHANGE).contentType(APPLICATION_JSON).content(SerializeUtils
			.GSON.toJson(requestDTO))).andExpect(status().is2xxSuccessful()).andReturn().getResponse()
			.getContentAsString();
		KeyExchangeResponseDTO responseDTO = SerializeUtils.GSON.fromJson(response, KeyExchangeResponseDTO.class);
		String serverPublicKey = responseDTO.getServerPublicKey();
		assertNotNull(serverPublicKey);
		ECKey serverKey = DTOUtils.getECKeyFromHexPublicKey(serverPublicKey);
		assertNotEquals(clientKey.getPublicKeyAsHex(), serverKey.getPublicKeyAsHex());
	}

	@Test
	public void testKeyExchange_ExistingPubKey() throws Exception {
		ECKey clientKey = new ECKey();

		// Test idempotence, requesting twice, succeeds with same result.
		KeyExchangeRequestDTO requestDTO1 = new KeyExchangeRequestDTO(clientKey.getPublicKeyAsHex());
		String response1 = mockMvc.perform(post(URL_KEY_EXCHANGE).contentType(APPLICATION_JSON).content(SerializeUtils
			.GSON.toJson(requestDTO1))).andExpect(status().is2xxSuccessful()).andReturn().getResponse()
			.getContentAsString();
		KeyExchangeResponseDTO responseDTO1 = SerializeUtils.GSON.fromJson(response1, KeyExchangeResponseDTO.class);
		String serverPublicKey1 = responseDTO1.getServerPublicKey();
		assertNotNull(serverPublicKey1);
		ECKey serverKey1 = DTOUtils.getECKeyFromHexPublicKey(serverPublicKey1);

		KeyExchangeRequestDTO requestDTO2 = new KeyExchangeRequestDTO(clientKey.getPublicKeyAsHex());
		String response2 = mockMvc.perform(post(URL_KEY_EXCHANGE).contentType(APPLICATION_JSON).content(SerializeUtils
			.GSON.toJson(requestDTO2))).andExpect(status().is2xxSuccessful()).andReturn().getResponse()
			.getContentAsString();
		KeyExchangeResponseDTO responseDTO2 = SerializeUtils.GSON.fromJson(response2, KeyExchangeResponseDTO.class);
		String serverPublicKey2 = responseDTO2.getServerPublicKey();
		assertNotNull(serverPublicKey2);
		ECKey serverKey2 = DTOUtils.getECKeyFromHexPublicKey(serverPublicKey2);

		assertEquals(serverKey1.getPublicKeyAsHex(), serverKey2.getPublicKeyAsHex());
	}

	@Test
	public void testCreateTimeLockedAddress_NoContent() throws Exception {
		mockMvc.perform(post(URL_CREATE_TIME_LOCKED_ADDRESS)).andExpect(status().is4xxClientError());
	}

	@Test
	public void testCreateTimeLockedAddress_EmptyRequest() throws Exception {
		mockMvc.perform(post(URL_CREATE_TIME_LOCKED_ADDRESS).contentType(APPLICATION_JSON).content("{}")).andExpect
			(status().is4xxClientError());
	}

	@Test
	public void testCreateTimeLockedAddress_NoPublicKey() throws Exception {
		ECKey clientKey = new ECKey();
		accountService.createAcount(clientKey);

		CreateAddressRequestDTO innerDTO = new CreateAddressRequestDTO("", validLocktime());
		SignedDTO requestDTO = DTOUtils.serializeAndSign(innerDTO, clientKey);

		mockMvc.perform(post(URL_CREATE_TIME_LOCKED_ADDRESS).contentType(APPLICATION_JSON).content(DTOUtils.toJSON
			(requestDTO))).andExpect(status().is4xxClientError());
	}

	@Test
	public void testCreateTimeLockedAddress_NoSignature() throws Exception {
		ECKey clientKey = new ECKey();
		accountService.createAcount(clientKey);

		CreateAddressRequestDTO innerDTO = new CreateAddressRequestDTO(clientKey.getPublicKeyAsHex(), validLocktime());
		String payload = DTOUtils.toBase64(innerDTO);
		SignedDTO requestDTO = new SignedDTO(payload, null);

		mockMvc.perform(post(URL_CREATE_TIME_LOCKED_ADDRESS).contentType(APPLICATION_JSON).content(DTOUtils.toJSON
			(requestDTO))).andExpect(status().is4xxClientError());
	}

	@Test
	public void testCreateTimeLockedAddress_WrongSignature() throws Exception {
		ECKey clientKey = new ECKey();
		ECKey wrongKey = new ECKey();
		accountService.createAcount(clientKey);

		CreateAddressRequestDTO innerDTO = new CreateAddressRequestDTO(clientKey.getPublicKeyAsHex(), validLocktime());
		SignedDTO requestDTO = DTOUtils.serializeAndSign(innerDTO, wrongKey);

		mockMvc.perform(post(URL_CREATE_TIME_LOCKED_ADDRESS).contentType(APPLICATION_JSON).content(DTOUtils.toJSON
			(requestDTO))).andExpect(status().is4xxClientError());
	}

	@Test
	public void testCreateTimeLockedAddress_NewAddress_ClientUnknown() throws Exception {
		ECKey clientKey = new ECKey();

		CreateAddressRequestDTO innerDTO = new CreateAddressRequestDTO(clientKey.getPublicKeyAsHex(), validLocktime());
		SignedDTO requestDTO = DTOUtils.serializeAndSign(innerDTO, clientKey);

		mockMvc.perform(post(URL_CREATE_TIME_LOCKED_ADDRESS).contentType(APPLICATION_JSON).content(DTOUtils.toJSON
			(requestDTO))).andExpect(status().is4xxClientError());
	}

	@Test
	public void testCreateTimeLockedAddress_NoLockTime() throws Exception {
		ECKey clientKey = new ECKey();
		accountService.createAcount(clientKey);

		CreateAddressRequestDTO innerDTO = new CreateAddressRequestDTO(clientKey.getPublicKeyAsHex(), 0L);
		SignedDTO requestDTO = DTOUtils.serializeAndSign(innerDTO, clientKey);

		mockMvc.perform(post(URL_CREATE_TIME_LOCKED_ADDRESS).contentType(APPLICATION_JSON).content(DTOUtils.toJSON
			(requestDTO))).andExpect(status().is4xxClientError());
	}


	@Test
	public void testCreateTimeLockedAddress_ValidSignature() throws Exception {
		ECKey clientKey = new ECKey();
		ECKey serverKey = accountService.createAcount(clientKey);

		long lockTime = validLocktime();
		CreateAddressRequestDTO innerDTO = new CreateAddressRequestDTO(clientKey.getPublicKeyAsHex(), lockTime);
		SignedDTO requestDTO = DTOUtils.serializeAndSign(innerDTO, clientKey);

		String responseString = mockMvc.perform(post(URL_CREATE_TIME_LOCKED_ADDRESS).contentType(APPLICATION_JSON)
			.content(DTOUtils.toJSON(requestDTO))).andExpect(status().isOk()).andReturn().getResponse()
			.getContentAsString();

		SignedDTO responseDTO = DTOUtils.fromJSON(responseString, SignedDTO.class);
		DTOUtils.validateSignature(responseDTO.getPayload(), responseDTO.getSignature(), serverKey);

	}

	@Test
	public void testCreateTimeLockedAddress_NewAddress_ClientKnown() throws Exception {
		ECKey clientKey = new ECKey();
		ECKey serverKey = accountService.createAcount(clientKey);
		long requestedLockTime = validLocktime();

		TimeLockedAddress expectedAddress = new TimeLockedAddress(clientKey.getPubKey(), serverKey.getPubKey(),
			requestedLockTime);

		CreateAddressRequestDTO innerDTO = new CreateAddressRequestDTO(clientKey.getPublicKeyAsHex(),
			requestedLockTime);
		SignedDTO requestDTO = DTOUtils.serializeAndSign(innerDTO, clientKey);

		String responseString = mockMvc.perform(post(URL_CREATE_TIME_LOCKED_ADDRESS).contentType(APPLICATION_JSON)
			.content(DTOUtils.toJSON(requestDTO))).andExpect(status().isOk()).andReturn().getResponse()
			.getContentAsString();

		SignedDTO responseDTO = DTOUtils.fromJSON(responseString, SignedDTO.class);
		DTOUtils.validateSignature(responseDTO.getPayload(), responseDTO.getSignature(), serverKey);
		CreateAddressResponseDTO createAddressResponse = DTOUtils.parseAndValidate(responseDTO,
			CreateAddressResponseDTO.class);

		// Construct TLA from response
		byte[] clientPublicKey = DTOUtils.getECKeyFromHexPublicKey(createAddressResponse.getClientPublicKey())
			.getPubKey();
		byte[] serverPublicKey = DTOUtils.getECKeyFromHexPublicKey(createAddressResponse.getServerPublicKey())
			.getPubKey();
		long receivedLockTime = createAddressResponse.getLockTime();
		TimeLockedAddress address = new TimeLockedAddress(clientPublicKey, serverPublicKey, receivedLockTime);

		assertNotNull(address);
		assertNotNull(address.getAddressHash());
		assertNotNull(address.getClientPubKey());
		assertNotNull(address.getServerPubKey());
		assertTrue(BitcoinUtils.isLockTimeByTime(address.getLockTime()));

		assertArrayEquals(address.getClientPubKey(), clientKey.getPubKey());
		assertArrayEquals(address.getServerPubKey(), serverKey.getPubKey());
		assertEquals(address.getLockTime(), requestedLockTime);

		assertEquals(expectedAddress, address);
		assertNotNull(accountService.getTimeLockedAddressByAddressHash(address.getAddressHash()));
	}

	private long validLocktime() {
		final long minLockTime = Instant.now().getEpochSecond() + appConfig.getMinimumLockTimeSeconds();
		final long maxLockTime = Instant.now().plus(Duration.ofDays(appConfig.getMaximumLockTimeDays()))
			.getEpochSecond();
		return (minLockTime + maxLockTime) / 2;
	}
}
