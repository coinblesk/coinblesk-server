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

import static com.coinblesk.enumerator.ForexBitcoinVendor.BITSTAMP;
import static com.coinblesk.enumerator.ForexBitcoinVendor.COINDESK;
import static com.coinblesk.enumerator.ForexCurrency.USD;
import static org.springframework.http.MediaType.APPLICATION_JSON_UTF8_VALUE;
import static org.springframework.web.bind.annotation.RequestMethod.GET;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import com.coinblesk.dto.ForexDTO;
import com.coinblesk.enumerator.ForexBitcoinVendor;
import com.coinblesk.enumerator.ForexCurrency;
import com.coinblesk.server.exceptions.BusinessException;
import com.coinblesk.server.exceptions.InvalidForexVendorException;
import com.coinblesk.server.service.ForexBitcoinService;
import com.coinblesk.server.service.ForexFiatService;

/**
 * Controller for client http requests regarding Transactions between two
 * UserAccounts.
 */
@RestController
// "/wallet" is for v1 only and should not be used anymore
@RequestMapping({"/wallet", "/forex"})
public class ForexController {

	private static final Logger LOG = LoggerFactory.getLogger(ForexController.class);

	private final ForexFiatService forexService;
	private final ForexBitcoinService forexBitcoinService;

	@Autowired
	public ForexController(ForexFiatService forexExchangeRateService, ForexBitcoinService forexBitcoinService) {
		this.forexService = forexExchangeRateService;
		this.forexBitcoinService = forexBitcoinService;
	}

	@RequestMapping(value = "/exchange-rate/fiat", method = GET, produces = APPLICATION_JSON_UTF8_VALUE)
	@ResponseBody
	public ForexDTO getCurrentFiatExchangeRate(@RequestParam(value = "fromCurrency", required = true) ForexCurrency fromCurrency,
			@RequestParam(value = "toCurrency", required = false) ForexCurrency toCurrency) throws BusinessException {

		// fallback, if only fromCurrency is specified
		if(toCurrency == null) {
			toCurrency = USD;
		}

		LOG.debug("{exchange-rate} - Received exchange rate request for currency {}/{}", fromCurrency, toCurrency);
		ForexDTO result = forexService.getExchangeRateDTO(fromCurrency, toCurrency);
		LOG.debug("{exchange-rate} - {}, {}, rate: {}", result.getCurrencyFrom(), result.getCurrencyTo(), result.getRate());

		return result;
	}

	@RequestMapping(value = "/exchange-rate/bitcoin", method = GET, produces = APPLICATION_JSON_UTF8_VALUE)
	@ResponseBody
	public ForexDTO getCurrentBitcoinExchangeRate(@RequestParam(value = "vendor", required = true) ForexBitcoinVendor vendor,
			@RequestParam(value = "currency", required = true) ForexCurrency currency) throws BusinessException {

		if(BITSTAMP.equals(vendor)) {
			return forexBitcoinService.getBitstampCurrentRate(currency);

		} else if(COINDESK.equals(vendor)) {
			return forexBitcoinService.getCoindeskCurrentRate(currency);

		} else {
			throw new InvalidForexVendorException();
		}
	}

	@RequestMapping(value = "/exchange-rate/bitcoin/history", method = GET, produces = APPLICATION_JSON_UTF8_VALUE)
	@ResponseBody
	public List<ForexDTO> getHistoricBitcoinExchangeRate(@RequestParam(value = "vendor", required = true) ForexBitcoinVendor vendor,
			@RequestParam(value = "currency", required = true) ForexCurrency currency) throws BusinessException {

		if(COINDESK.equals(vendor)) {
			return forexBitcoinService.getCoindeskHistoricRates(currency);

		} else {
			throw new InvalidForexVendorException();
		}

	}

}
