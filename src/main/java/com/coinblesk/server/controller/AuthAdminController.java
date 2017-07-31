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

import static com.coinblesk.server.config.UserRole.ROLE_ADMIN;
import static org.springframework.http.MediaType.APPLICATION_JSON_UTF8_VALUE;
import static org.springframework.web.bind.annotation.RequestMethod.DELETE;
import static org.springframework.web.bind.annotation.RequestMethod.GET;
import static org.springframework.web.bind.annotation.RequestMethod.POST;
import static org.springframework.web.bind.annotation.RequestMethod.PUT;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.core.Utils;
import org.bitcoinj.params.TestNet3Params;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.annotation.Secured;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.coinblesk.dto.AccountDTO;
import com.coinblesk.dto.AccountDetailsDTO;
import com.coinblesk.dto.ServerBalanceDTO;
import com.coinblesk.dto.TimeLockedAddressDTO;
import com.coinblesk.dto.UserAccountAdminDTO;
import com.coinblesk.server.config.AppConfig;
import com.coinblesk.server.entity.Account;
import com.coinblesk.server.entity.Event;
import com.coinblesk.server.entity.ServerPotBaseline;
import com.coinblesk.server.entity.TimeLockedAddressEntity;
import com.coinblesk.server.enumerator.EventUrgence;
import com.coinblesk.server.exceptions.AccountNotFoundException;
import com.coinblesk.server.exceptions.BusinessException;
import com.coinblesk.server.service.AccountService;
import com.coinblesk.server.service.EventService;
import com.coinblesk.server.service.MicropaymentService;
import com.coinblesk.server.service.ServerBalanceService;
import com.coinblesk.server.service.ServerPotBaselineService;
import com.coinblesk.server.service.UserAccountService;
import com.coinblesk.server.service.WalletService;
import com.coinblesk.util.Pair;
import com.coinblesk.util.SerializeUtils;

/**
 * @author Thomas Bocek
 * @author Andreas Albrecht
 * @author Sebastian Stephan
 */

@Controller
@RequestMapping(value = "/auth/admin")
@Secured(ROLE_ADMIN)
public class AuthAdminController {

	private static Logger LOG = LoggerFactory.getLogger(AuthAdminController.class);

	private final AppConfig appConfig;
	private final WalletService walletService;
	private final UserAccountService userAccountService;
	private final AccountService accountService;
	private final EventService eventService;
	private final ServerPotBaselineService serverPotBaselineService;
	private final ServerBalanceService serverBalanceService;
	private final MicropaymentService microPaymentService;

	@Autowired
	public AuthAdminController(AppConfig appConfig, WalletService walletService, UserAccountService userAccountService,
			AccountService accountService, EventService eventService, ServerPotBaselineService serverPotBaselineService,
			ServerBalanceService serverBalanceService, MicropaymentService microPaymentService) {
		this.appConfig = appConfig;
		this.walletService = walletService;
		this.userAccountService = userAccountService;
		this.accountService = accountService;
		this.eventService = eventService;
		this.serverPotBaselineService = serverPotBaselineService;
		this.serverBalanceService = serverBalanceService;
		this.microPaymentService = microPaymentService;
	}

	@RequestMapping(value = "/balance", method = GET)
	@ResponseBody
	public Coin balance() {
		return walletService.getBalance();
	}

	@RequestMapping(value = "/server-balance", method = GET)
	@ResponseBody
	public ServerBalanceDTO getServerBalance() {
		return serverBalanceService.getServerBalance();
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

	@RequestMapping(value = "/user-accounts", method = GET)
	@ResponseBody
	public List<UserAccountAdminDTO> getAllUserAccounts() {
		return userAccountService.getAllAdminDTO();
	}

	// see http://stackoverflow.com/a/16333149/3233827
	@RequestMapping(value = "/user-accounts/{email:.+}", method = GET)
	@ResponseBody
	public UserAccountAdminDTO getUserAccount(@PathVariable("email") String email) throws BusinessException {
		return userAccountService.getAdminDTO(email);
	}
	// see http://stackoverflow.com/a/16333149/3233827
	@RequestMapping(value = "/user-accounts/{email:.+}/delete", method = DELETE)
	@ResponseBody
	public void deleteUser(@PathVariable("email") String email) throws BusinessException {
		userAccountService.delete(email);
	}
	// see http://stackoverflow.com/a/16333149/3233827
	@RequestMapping(value = "/user-accounts/{email:.+}/undelete", method = PUT)
	@ResponseBody
	public void undeleteUser(@PathVariable("email") String email) throws BusinessException {
		userAccountService.undelete(email);
	}

	// see http://stackoverflow.com/a/16333149/3233827
	@RequestMapping(value = "/user-accounts/{email:.+}/switch-role", method = POST)
	@ResponseBody
	public void switchUserRole(@PathVariable("email") String email) throws BusinessException {
		userAccountService.switchRole(email);
	}

	@RequestMapping(value = "/accounts", method = GET)
	@ResponseBody
	public List<AccountDTO> getAllAccounts() {
		// Pre-calculate balances for each address
		Map<Address, Coin> balances = walletService.getBalanceByAddresses();

		return accountService.allAddresses().stream().collect(Collectors.groupingBy
				(TimeLockedAddressEntity::getAccount)).entrySet().stream().map(accountListEntry -> {

			Account account = accountListEntry.getKey();

			List<TimeLockedAddressDTO> addresses = accountListEntry.getValue().stream().map(tla -> {
				return mapTimeLockedAddressDTO(tla, balances);
			}).collect(Collectors.toList());

			long satoshiBalance = addresses.stream().mapToLong(TimeLockedAddressDTO::getBalance).sum();
			return mapAccountDTO(account, satoshiBalance);

		}).collect(Collectors.toList());
	}

	private TimeLockedAddressDTO mapTimeLockedAddressDTO(TimeLockedAddressEntity tla, Map<Address, Coin> balances) {
		NetworkParameters params = appConfig.getNetworkParameters();
		Instant createdAt = Instant.ofEpochSecond(tla.getTimeCreated());
		Instant lockedUntil = Instant.ofEpochSecond(tla.getLockTime());
		Coin balance = balances.get(tla.toAddress(params));
		String redeemScript = SerializeUtils.bytesToHex(tla.getRedeemScript());
		return new TimeLockedAddressDTO(tla.toAddress(params).toString(), "http://" + (params.getClass()
				.equals(TestNet3Params.class) ? "tbtc." : "") + "blockr.io/address/info/" + tla.toAddress(params),
				Date.from(createdAt), Date.from(lockedUntil), tla.isLocked(), redeemScript, balance
				.longValue());
	}

	private AccountDTO mapAccountDTO(Account account, long satoshiBalance) {
		String clientPublicKey = SerializeUtils.bytesToHex(account.clientPublicKey());
		String serverPublicKey = SerializeUtils.bytesToHex(account.serverPublicKey());
		String serverPrivateKey = SerializeUtils.bytesToHex(account.serverPrivateKey());
		Date timeCreated = Date.from(Instant.ofEpochSecond(account.timeCreated()));
		long virtualBalance = account.virtualBalance();
		long channelTransactionAmount = microPaymentService.getPendingChannelValue(account).longValue();
		long totalBalance = account.virtualBalance() + satoshiBalance - channelTransactionAmount;
		boolean isLocked = account.isLocked();
		Date broadcastBefore = account.getBroadcastBefore() == 0 ? null : Date.from(Instant.ofEpochSecond(account.getBroadcastBefore()));
		Date nonce = account.getNonce() == 0 ? null : Date.from(Instant.ofEpochMilli(account.getNonce()));
		String channelTransaction = account.getChannelTransaction() != null ? SerializeUtils.bytesToHex(account.getChannelTransaction()) : null;

		String userAccountEmail = null;
		if(account.getUserAccount() != null) {
			userAccountEmail = account.getUserAccount().getEmail();
		}

		return new AccountDTO(clientPublicKey, serverPublicKey, serverPrivateKey, timeCreated, virtualBalance,
				satoshiBalance, channelTransactionAmount, totalBalance, isLocked, broadcastBefore, nonce,
				channelTransaction, userAccountEmail);
	}

	@RequestMapping(value = "/accounts/{client-public-key}", method = GET)
	@ResponseBody
	public AccountDetailsDTO getAccount(@PathVariable("client-public-key") String clientPublicKey) throws BusinessException {
		byte[] clientPublicKeyBytes = null;
		try {
			clientPublicKeyBytes = Utils.HEX.decode(clientPublicKey.toLowerCase());
		} catch(Exception e) {
			throw new AccountNotFoundException();
		}

		Account account = accountService.getByClientPublicKey(clientPublicKeyBytes);
		if(account == null) {
			throw new AccountNotFoundException();
		}

		// Pre-calculate balances for each address
		Map<Address, Coin> balances = walletService.getBalanceByAddresses();

		List<TimeLockedAddressDTO> timeLockedAddressDTOs = new ArrayList<>();
		for(TimeLockedAddressEntity tla : account.getTimeLockedAddresses()) {
			timeLockedAddressDTOs.add(mapTimeLockedAddressDTO(tla, balances));
		}
		long satoshiBalance = timeLockedAddressDTOs.stream().mapToLong(TimeLockedAddressDTO::getBalance).sum();
		AccountDTO accountDTO = mapAccountDTO(account, satoshiBalance);

		AccountDetailsDTO dto = new AccountDetailsDTO();
		dto.setTimeLockedAddresses(timeLockedAddressDTOs);
		dto.setAccount(accountDTO);

		return dto;
	}

	@RequestMapping(value = "/events", method = GET, produces = APPLICATION_JSON_UTF8_VALUE)
	@ResponseBody
	public List<Event> getEvents(@RequestParam("urgence") EventUrgence urgence) {
		return eventService.getEventsWithUrgenceOrHigher(urgence);
	}

	@RequestMapping(value = "/server-pot-baseline", method = POST)
	@ResponseBody
	public void addNewAmountToServerBaselinePot(@RequestParam("amount") long amount) {
		serverPotBaselineService.addNewServerPotBaselineAmount(amount);
	}

	@RequestMapping(value = "/server-pot-baseline", method = GET)
	@ResponseBody
	public List<ServerPotBaseline> getAllServerPotBaslineRows() {
		return serverPotBaselineService.getAllServerPotBaselineRows();
	}

	@RequestMapping(value = "/server-pot-baseline/total", method = GET)
	@ResponseBody
	public long getTotalServerPotBasline() {
		return serverPotBaselineService.getTotalServerPotBaseline();
	}

}
