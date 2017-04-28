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

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.coinblesk.dto.ForexDTO;
import com.coinblesk.server.exceptions.BusinessException;
import com.coinblesk.server.exceptions.InvalidCurrencyPatternException;
import com.coinblesk.server.utilTest.CoinbleskTest;

public class ForexExchangeRateServiceTest extends CoinbleskTest {

	@Autowired
	private ForexService forexService;

	@Autowired
	private ForexBitcoinService forexBitcoinService;

	@Test
	public void testForex() throws Exception {
		BigDecimal d = forexService.getExchangeRate("USD", "CHF");
		Assert.assertNotNull(d);
		System.out.println("rate is: " + d);
	}

	@Test
	public void testForexMulti1() throws Exception {
		Map<String, BigDecimal> m = forexService.getExchangeRates("CHFUSD", "USDEUR");
		Assert.assertNotNull(m);
		System.out.println("rate is: " + m);
	}

	@Test
	public void testForexMulti2() throws Exception {
		// TODO: check if cached
		Map<String, BigDecimal> m = forexService.getExchangeRates("CHFUSD", "USDEUR");
		Assert.assertNotNull(m);
		System.out.println("rate is: " + m);
	}

	@Test
	public void testBitcoinCurrent() throws BusinessException {
		ForexDTO forexDTO = forexBitcoinService.getCurrentRate("EUR");
		Assert.assertNotNull(forexDTO);
		Assert.assertTrue(forexDTO.getCurrencyFrom().equals("BTC"));
		Assert.assertTrue(forexDTO.getCurrencyTo().equals("EUR"));
	}

	@Test(expected = InvalidCurrencyPatternException.class)
	public void testBitcoinCurrentFailsWithWrongSymbol() throws BusinessException {
		forexBitcoinService.getCurrentRate("ABC");
	}

	@Test
	public void testBitcoinHistory() throws BusinessException {
		List<ForexDTO> list = forexBitcoinService.getHistoricRates("USD");
		Assert.assertNotNull(list);
		Assert.assertTrue(list.size() > 10);
		Assert.assertTrue(list.get(0).getCurrencyFrom().equals("BTC"));
		Assert.assertTrue(list.get(0).getCurrencyTo().equals("USD"));
	}

	@Test(expected = InvalidCurrencyPatternException.class)
	public void testBitcoinHistoryFailsWithWrongSymbol() throws BusinessException {
		forexBitcoinService.getHistoricRates("CDE");
	}

}
