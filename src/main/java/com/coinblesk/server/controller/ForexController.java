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

import static org.springframework.http.MediaType.APPLICATION_JSON_UTF8_VALUE;
import static org.springframework.web.bind.annotation.RequestMethod.GET;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import com.coinblesk.dto.ForexDTO;
import com.coinblesk.server.exceptions.BusinessException;
import com.coinblesk.server.exceptions.InvalidCurrencyPatternException;
import com.coinblesk.server.service.ForexBitcoinService;
import com.coinblesk.server.service.ForexService;

/**
 * Controller for client http requests regarding Transactions between two
 * UserAccounts.
 */
@RestController
// "/wallet" is for v1 only and should not be used anymore
@RequestMapping({"/wallet", "/forex"})
public class ForexController {

	private static final Logger LOG = LoggerFactory.getLogger(ForexController.class);

	private final ForexService forexService;
	private final ForexBitcoinService forexBitcoinService;

	@Autowired
	public ForexController(ForexService forexExchangeRateService, ForexBitcoinService forexBitcoinService) {
		this.forexService = forexExchangeRateService;
		this.forexBitcoinService = forexBitcoinService;
	}

	@RequestMapping(value = "/exchange-rate/{symbol}", method = GET, produces = APPLICATION_JSON_UTF8_VALUE)
	@ResponseBody
	public ForexDTO forexExchangeRate(@PathVariable(value = "symbol") String symbol) throws BusinessException {
		validateSymbol(symbol);
		return forexExchangeRate(symbol, "USD");
	}

	/**
	 * Returns up to date exchangerate BTC/CHF
	 *
	 * @return CustomResponseObject with exchangeRate BTC/CHF as a String
	 */
	@RequestMapping(value = "/exchange-rate/{fromSymbol}/{toSymbol}", method = GET, produces = APPLICATION_JSON_UTF8_VALUE)
	@ResponseBody
	public ForexDTO forexExchangeRate(@PathVariable(value = "fromSymbol") String fromSymbol,
			@PathVariable(value = "toSymbol") String toSymbol) throws BusinessException {

		LOG.debug("{exchange-rate} - Received exchange rate request for currency {}/{}", fromSymbol, toSymbol);
		ForexDTO result = new ForexDTO();

		validateSymbol(fromSymbol);
		validateSymbol(toSymbol);

		BigDecimal exchangeRate = forexService.getExchangeRate(fromSymbol, toSymbol);
		result.setCurrencyFrom(fromSymbol);
		result.setCurrencyTo(toSymbol);
		result.setRate(exchangeRate);

		LOG.debug("{exchange-rate} - {}, {}, rate: {}", result.getCurrencyFrom(), result.getCurrencyTo(), result.getRate());
		return result;
	}

	@RequestMapping(value = "/exchange-rate/bitcoin/bitstamp/current/{symbol}", method = GET, produces = APPLICATION_JSON_UTF8_VALUE)
	@ResponseBody
	public ForexDTO bitcoinBitstampCurrentRate(@PathVariable("symbol") String symbol) throws BusinessException, IOException {
		validateSymbol(symbol);
		ForexDTO forexBTCUSD = forexBitcoinService.getBitstampBTCUSDRate();
		BigDecimal forexUSDCHF = forexService.getExchangeRate("USD", "CHF");

		forexBTCUSD.setCurrencyTo(symbol);
		forexBTCUSD.setRate(forexBTCUSD.getRate().multiply(forexUSDCHF));
		forexBTCUSD.setUpdatedAt(new Date());

		return forexBTCUSD;
	}

	@RequestMapping(value = "/exchange-rate/bitcoin/current/{symbol}", method = GET, produces = APPLICATION_JSON_UTF8_VALUE)
	@ResponseBody
	public ForexDTO bitcoinCurrentRate(@PathVariable("symbol") String symbol) throws BusinessException {
		validateSymbol(symbol);
		return forexBitcoinService.getCurrentRate(symbol);
	}

	@RequestMapping(value = "/exchange-rate/bitcoin/history/{symbol}", method = GET, produces = APPLICATION_JSON_UTF8_VALUE)
	@ResponseBody
	public List<ForexDTO> bitcoinHistoricRates(@PathVariable("symbol") String symbol) throws BusinessException {
		validateSymbol(symbol);
		return forexBitcoinService.getHistoricRates(symbol);
	}

	private void validateSymbol(String symbol) throws BusinessException {
		if (!Pattern.matches("[A-Z]{3}", symbol)) {
			throw new InvalidCurrencyPatternException();
		}
	}
}
