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

import static org.springframework.web.bind.annotation.RequestMethod.GET;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.servlet.ServletContext;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.TransactionOutput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import com.coinblesk.server.service.WalletService;
import com.coinblesk.server.utils.ApiVersion;
import com.coinblesk.util.Pair;

/**
 *
 * @author Thomas Bocek
 * @author Andreas Albrecht
 */

@Controller
@RequestMapping(value = "/admin")
@ApiVersion({ "v1", "" })
public class AdminController {

	private static Logger LOG = LoggerFactory.getLogger(AdminController.class);

	@Autowired
	ServletContext context;

	@Autowired
	private WalletService walletService;

	@RequestMapping(value = "/balance", method = GET)
	@ResponseBody
	public Coin balance() {
		return walletService.getBalance();
	}

	@RequestMapping(value = "/addresses", method = GET)
	@ResponseBody
	public Map<Address, Coin> addresses() {
		Map<Address, Coin> addressBalances = walletService.getBalanceByAddresses();
		LOG.debug("Total addresses: " + addressBalances.size());
		return addressBalances;
	}

	@RequestMapping(value = "/utxo", method = GET)
	@ResponseBody
	public List<Pair<String, String>> utxo() {
		// cannot return utxo due to GSON recursion
		List<TransactionOutput> utxo = walletService.getUnspentOutputs();
		List<Pair<String, String>> txOuts = new ArrayList<>();
		for (TransactionOutput txOut : utxo) {
			txOuts.add(new Pair<>(txOut.getOutPointFor().toString(), txOut.toString()));
		}
		return txOuts;
	}
}
