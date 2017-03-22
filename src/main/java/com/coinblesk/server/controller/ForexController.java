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

import static com.coinblesk.json.v1.Type.SERVER_ERROR;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.MediaType.APPLICATION_JSON_UTF8_VALUE;
import static org.springframework.web.bind.annotation.RequestMethod.GET;

import java.math.BigDecimal;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import com.coinblesk.json.v1.ExchangeRateTO;
import com.coinblesk.server.service.ForexService;
import com.coinblesk.server.utils.ApiVersion;

/**
 * Controller for client http requests regarding Transactions between two
 * UserAccounts.
 *
 */
@RestController
// "/wallet" is for v1 only and should not be used anymore
@RequestMapping({ "/wallet", "/forex" })
@ApiVersion({ "v1", "" })
public class ForexController {

	private static final Logger LOG = LoggerFactory.getLogger(ForexController.class);

	@Autowired
	private ForexService forexExchangeRateService;

	@RequestMapping(value = "/exchangeRate/{symbol}", method = GET, produces = APPLICATION_JSON_UTF8_VALUE)
	@ResponseBody
	public ResponseEntity<ExchangeRateTO> forexExchangeRate(@PathVariable(value = "symbol") String symbol) {
		return forexExchangeRate(symbol, "USD");
	}

	/**
	 * Returns up to date exchangerate BTC/CHF
	 *
	 * @return CustomResponseObject with exchangeRate BTC/CHF as a String
	 */
	@RequestMapping(
			value = "/rate/{from}-{to}",
			method = GET,
			produces = APPLICATION_JSON_UTF8_VALUE)
	@ApiVersion({ "v2" })
	@ResponseBody
	public ResponseEntity<ExchangeRateTO> forexExchangeRate(@PathVariable(value = "from") String from,
			@PathVariable(value = "to") String to) {

		LOG.debug("{exchange-rate} - Received exchange rate request for currency {}/{}", from, to);
		ExchangeRateTO output = new ExchangeRateTO();
		try {
			if (!Pattern.matches("[A-Z]{3}", from) || !Pattern.matches("[A-Z]{3}", to)) {
				output.type(SERVER_ERROR).message("unknown currency symbol");
				return new ResponseEntity<>(output, BAD_REQUEST);
			}
			BigDecimal exchangeRate = forexExchangeRateService.getExchangeRate(from, to);
			output.name(from + to);
			output.rate(exchangeRate.toString());
			output.setSuccess();

			LOG.debug("{exchange-rate} - {}, rate: {}", output.name(), output.rate());
			return new ResponseEntity<>(output, OK);

		} catch (Exception e) {
			LOG.error("{exchange-rate} - SERVER_ERROR - exception: ", e);
			output.type(SERVER_ERROR);
			output.message(e.getMessage());
			return new ResponseEntity<>(output, BAD_REQUEST);
		}
	}
}
