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

import com.coinblesk.server.dto.KeyExchangeRequestDTO;
import com.coinblesk.server.utilTest.CoinbleskTest;
import com.coinblesk.util.SerializeUtils;
import org.bitcoinj.core.ECKey;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @author Thomas Bocek
 * @author Sebastian Stephan
 */
public class KeyExchangeTest extends CoinbleskTest {

	private static MockMvc mockMvc;
	@Autowired
	private WebApplicationContext webAppContext;

	@Before
	public void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(webAppContext).build();
	}

	@Test
	public void noPayloadFails() throws Exception {
		mockMvc.perform(post("/payment/key-exchange").contentType(MediaType.APPLICATION_JSON)).andExpect(status()
			.isBadRequest());

		mockMvc.perform(post("/payment/key-exchange").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect
			(status().isBadRequest());
	}

	@Test
	public void emptyPublicKeyFails() throws Exception {
		mockMvc.
			perform(post("/payment/key-exchange").contentType(MediaType.APPLICATION_JSON).content("{\"publicKey\": " +
				"\"\"}")).andExpect(status().isBadRequest());
	}


	@Test
	public void invalidPublicKeyFails() throws Exception {
		String bogusKey = "02a485c51c0cef798620ea81054100000BOGUSKEY000001a046d50ca3ca0ad148f";
		mockMvc.perform(post("/payment/key-exchange").contentType(MediaType.APPLICATION_JSON).content(SerializeUtils
			.GSON.toJson(new KeyExchangeRequestDTO(bogusKey)))).andExpect(status().isBadRequest());
	}

	@Test
	public void resultIs200OK() throws Exception {
		ECKey goodKey = new ECKey();
		mockMvc.perform(post("/payment/key-exchange").contentType(MediaType.APPLICATION_JSON).content(SerializeUtils
			.GSON.toJson(new KeyExchangeRequestDTO(goodKey.getPublicKeyAsHex())))).andExpect(status().isOk())
			.andReturn();
	}
}
