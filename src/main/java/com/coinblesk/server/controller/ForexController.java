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

import java.math.BigDecimal;
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
import com.coinblesk.server.service.ForexService;
import com.coinblesk.server.utils.ApiVersion;

/**
 * Controller for client http requests regarding Transactions between two
 * UserAccounts.
 */
@RestController
// "/wallet" is for v1 only and should not be used anymore
@RequestMapping({"/wallet", "/forex"})
@ApiVersion({"v1", ""})
public class ForexController {

	private static final Logger LOG = LoggerFactory.getLogger(ForexController.class);

	private final ForexService forexExchangeRateService;

	@Autowired
	public ForexController(ForexService forexExchangeRateService) {
		this.forexExchangeRateService = forexExchangeRateService;
	}

	@RequestMapping(value = "/exchange-rate/{symbol}", method = GET, produces = APPLICATION_JSON_UTF8_VALUE)
	@ResponseBody
	public ForexDTO forexExchangeRate(@PathVariable(value = "symbol") String symbol) throws BusinessException {
		return forexExchangeRate(symbol, "USD");
	}

	/**
	 * Returns up to date exchangerate BTC/CHF
	 *
	 * @return CustomResponseObject with exchangeRate BTC/CHF as a String
	 */
	@RequestMapping(value = "/exchange-rate/{fromSymbol}/{toSymbol}", method = GET, produces = APPLICATION_JSON_UTF8_VALUE)
	@ApiVersion({"v2"})
	@ResponseBody
	public ForexDTO forexExchangeRate(@PathVariable(value = "fromSymbol") String fromSymbol,
			@PathVariable(value = "toSymbol") String toSymbol) throws BusinessException {

		LOG.debug("{exchange-rate} - Received exchange rate request for currency {}/{}", fromSymbol, toSymbol);
		ForexDTO result = new ForexDTO();

		if (!Pattern.matches("[A-Z]{3}", fromSymbol) || !Pattern.matches("[A-Z]{3}", toSymbol)) {
			throw new InvalidCurrencyPatternException();
		}

		BigDecimal exchangeRate = forexExchangeRateService.getExchangeRate(fromSymbol, toSymbol);
		result.setCurrencyA(fromSymbol);
		result.setCurrencyB(toSymbol);
		result.setRate(exchangeRate);

		LOG.debug("{exchange-rate} - {}, {}, rate: {}", result.getCurrencyA(), result.getCurrencyB(), result.getRate());
		return result;
	}
}
