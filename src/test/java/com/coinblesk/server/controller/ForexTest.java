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

import com.coinblesk.json.v1.ExchangeRateTO;
import com.coinblesk.server.utilTest.CoinbleskTest;
import com.coinblesk.util.SerializeUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @author Thomas Bocek
 */

public class ForexTest extends CoinbleskTest {

	private static MockMvc mockMvc;
	@Autowired
	private WebApplicationContext webAppContext;

	@Before
	public void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(webAppContext).build();
	}

	@Test
	public void testV1() throws Exception {
		MvcResult res = mockMvc.perform(get("/forex/exchangeRate/CHF").secure(true)).andExpect(status().isOk())
			.andReturn();
		ExchangeRateTO rate = SerializeUtils.GSON.fromJson(res.getResponse().getContentAsString(), ExchangeRateTO
			.class);
		System.out.println("rate is: " + rate.rate() + "/" + rate.name());
		Assert.assertNotNull(rate);
	}

	@Test
	public void testV2() throws Exception {
		MvcResult res = mockMvc.perform(get("/v2/forex/rate/CHF-EUR").secure(true)).andExpect(status().isOk())
			.andReturn();
		ExchangeRateTO rate = SerializeUtils.GSON.fromJson(res.getResponse().getContentAsString(), ExchangeRateTO
			.class);
		System.out.println("rate is: " + rate.rate() + "/" + rate.name());
		Assert.assertNotNull(rate);
	}
}
