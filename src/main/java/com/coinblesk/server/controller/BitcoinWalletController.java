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

import java.math.BigDecimal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import com.coinblesk.server.service.ForexExchangeRateService;
import com.coinblesk.server.utils.ApiVersion;
import com.coinblesk.json.ExchangeRateTO;
import com.coinblesk.json.Type;
import java.util.regex.Pattern;

/**
 * Controller for client http requests regarding Transactions between two UserAccounts.
 *
 */
@RestController
@RequestMapping({"/wallet", "/w"})
@ApiVersion({"v1", ""})
public class BitcoinWalletController {

    private static final Logger LOG = LoggerFactory.getLogger(BitcoinWalletController.class);

    @Autowired
    private ForexExchangeRateService forexExchangeRateService;

    /**
     * Returns up to date exchangerate BTC/CHF
     *
     * @return CustomResponseObject with exchangeRate BTC/CHF as a String
     */
    @RequestMapping(value = {"/exchangeRate/{from}{to}", "/x/{from}{to}"},
            method = RequestMethod.GET, produces = "application/json; charset=UTF-8")
    @ResponseBody
    public ResponseEntity<ExchangeRateTO> forexExchangeRate(@PathVariable(value = "from") String from,
            @PathVariable(value = "to") String to) {
        LOG.debug("Received exchange rate request for currency {}/{}", from, to);
        ExchangeRateTO output = new ExchangeRateTO();
        try {
            if (!Pattern.matches("[A-Z]{3}", from) || !Pattern.matches("[A-Z]{3}", to)) {
                output.type(Type.SERVER_ERROR).message("unknown symbol");
                return new ResponseEntity<>(output, HttpStatus.BAD_REQUEST);
            }
            BigDecimal exchangeRate = forexExchangeRateService.getExchangeRate(from, to);
            output.name(from + to);
            output.rate(exchangeRate.toString());
            output.setSuccess();
            return new ResponseEntity<>(output, HttpStatus.OK);
        } catch (Exception e) {
            output.type(Type.SERVER_ERROR);
            output.message(e.getMessage());
            return new ResponseEntity<>(output, HttpStatus.BAD_REQUEST);
        }
    }
}
