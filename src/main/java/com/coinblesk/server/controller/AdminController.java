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

import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.coinblesk.server.config.AppConfig;
import com.coinblesk.server.dto.KeysDTO;
import com.coinblesk.server.dto.TimeLockedAddressDTO;
import com.coinblesk.server.entity.Account;
import com.coinblesk.server.service.KeyService;
import com.coinblesk.util.SerializeUtils;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.params.TestNet3Params;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import com.coinblesk.server.service.WalletService;
import com.coinblesk.util.Pair;

/**
 *
 * @author Thomas Bocek
 * @author Andreas Albrecht
 * @author Sebastian Stephan
 */

@Controller
@RequestMapping(value = "/admin")
public class AdminController {

	private static Logger LOG = LoggerFactory.getLogger(AdminController.class);

	private final AppConfig appConfig;

	private final WalletService walletService;

	private final KeyService keyService;

	@Autowired
	public AdminController(AppConfig appConfig, WalletService walletService, KeyService keyService) {
		this.appConfig = appConfig;
		this.walletService = walletService;
		this.keyService = keyService;
	}

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

	@RequestMapping(value = "/keys", method = GET)
	@ResponseBody
	public List<KeysDTO> getAllKeys() {
		NetworkParameters params = appConfig.getNetworkParameters();

		// Pre-calculate balances for each address
		Map<Address, Coin> balances = walletService.getBalanceByAddresses();

		List<Account> keys = keyService.allKeys();

		// ...and summed for each public key
		Map<Account, Long> balancesPerKeys = keys.stream()
				.collect(Collectors.toMap(Function.identity(),
						key ->
								key.timeLockedAddresses()
										.stream()
										.map(tla -> tla.toAddress(params))
										.map(balances::get)
										.mapToLong(Coin::longValue)
										.sum()
				));

		// Map the Account entities to DTOs including the containing TimeLockedAddresses
		return keys.stream()
				.map(key -> new KeysDTO(
						SerializeUtils.bytesToHex(key.clientPublicKey()),
						SerializeUtils.bytesToHex(key.serverPublicKey()),
						SerializeUtils.bytesToHex(key.serverPrivateKey()),
						Date.from(Instant.ofEpochSecond(key.timeCreated())),
						key.virtualBalance(),
						balancesPerKeys.get(key),
						key.virtualBalance() + balancesPerKeys.get(key),
						key.timeLockedAddresses().stream() .map(tla -> {
									Instant createdAt = Instant.ofEpochSecond(tla.getTimeCreated());
									Instant lockedUntil = Instant.ofEpochSecond(tla.getLockTime());
									Coin balance = balances.get(tla.toAddress(params));
									return new TimeLockedAddressDTO(
											tla.toAddress(params).toString(),
											"http://" + (params.getClass().equals(TestNet3Params.class) ? "tbtc." : "")
													+ "blockr.io/address/info/" + tla.toAddress(params),
											Date.from(createdAt),
											Date.from(lockedUntil),
											lockedUntil.isAfter(Instant.now()),
											balance.longValue()
									);
								}
						).collect(Collectors.toList())
				))
				.collect(Collectors.toList());
	}
}
