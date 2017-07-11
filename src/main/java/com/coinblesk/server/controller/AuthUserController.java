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

import static com.coinblesk.server.config.UserRole.ROLE_USER;
import static org.springframework.http.MediaType.APPLICATION_JSON_UTF8_VALUE;
import static org.springframework.web.bind.annotation.RequestMethod.GET;
import static org.springframework.web.bind.annotation.RequestMethod.POST;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.NetworkParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.annotation.Secured;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import com.coinblesk.dto.AccountBalanceDTO;
import com.coinblesk.dto.EncryptedClientPrivateKeyDTO;
import com.coinblesk.json.v1.BaseTO;
import com.coinblesk.json.v1.Type;
import com.coinblesk.json.v1.UserAccountTO;
import com.coinblesk.server.config.AppConfig;
import com.coinblesk.server.entity.Account;
import com.coinblesk.server.entity.TimeLockedAddressEntity;
import com.coinblesk.server.entity.UserAccount;
import com.coinblesk.server.exceptions.AccountNotFoundException;
import com.coinblesk.server.exceptions.BusinessException;
import com.coinblesk.server.exceptions.UserAccountNotFoundException;
import com.coinblesk.server.service.UserAccountService;
import com.coinblesk.server.service.WalletService;

/**
 * @author Thomas Bocek
 */
@RestController
@RequestMapping(value = { "/auth/user" })
@Secured(ROLE_USER)
public class AuthUserController {

	private final static Logger LOG = LoggerFactory.getLogger(AuthUserController.class);

	private final AppConfig appConfig;
	private final UserAccountService userAccountService;
	private final WalletService walletService;

	@Autowired
	public AuthUserController(AppConfig appConfig, UserAccountService userAccountService, WalletService walletService) {
		this.appConfig = appConfig;
		this.userAccountService = userAccountService;
		this.walletService = walletService;
	}

	@RequestMapping(value = "/transfer-p2sh", method = POST, produces = APPLICATION_JSON_UTF8_VALUE)
	@ResponseBody
	public UserAccountTO transferToP2SH(@RequestBody BaseTO request) {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		LOG.debug("Get account for {}", auth.getName());
		try {
			final ECKey clientKey = ECKey.fromPublicOnly(request.publicKey());
			UserAccountTO status = userAccountService.transferP2SH(clientKey, auth.getName());
			if (status != null) {
				LOG.debug("Transfer P2SH success for {}, tx:{}", auth.getName(), status.message());
				return status;
			} else {
				return new UserAccountTO().type(Type.ACCOUNT_ERROR);
			}
		} catch (Exception e) {
			LOG.error("User create error", e);
			return new UserAccountTO().type(Type.SERVER_ERROR).message(e.getMessage());
		}
	}

	@RequestMapping(value = "/balance", method = GET, produces = APPLICATION_JSON_UTF8_VALUE)
	@ResponseBody
	public AccountBalanceDTO getTimeLockedAddressesWithBalance() throws BusinessException {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if(auth != null && userAccountService.userExists(auth.getName())) {
			UserAccount user = userAccountService.getByEmail(auth.getName());
			Account account = user.getAccount();

			if(account == null) {
				throw new AccountNotFoundException();
			}

			NetworkParameters params = appConfig.getNetworkParameters();
			Map<String, Long> resultingTlas = new HashMap<>();
			Long totalBalance = 0L;

			List<TimeLockedAddressEntity> tlaList = account.getTimeLockedAddresses();
			Map<String, TimeLockedAddressEntity> tlas = new HashMap<>();
			for(TimeLockedAddressEntity tla : tlaList) {
				tlas.put(tla.toAddress(params).toString(), tla);
			}

			Map<Address, Coin> balancesByAddresses = walletService.getBalanceByAddresses();
			for(Map.Entry<Address, Coin> balance : balancesByAddresses.entrySet()) {
				String addressString = balance.getKey().toString();
				if(tlas.keySet().contains(addressString)) {
					resultingTlas.put(addressString, balance.getValue().longValue());
					totalBalance += balance.getValue().longValue();
				}
			}

			AccountBalanceDTO dto = new AccountBalanceDTO();
			dto.setTimeLockedAddresses(resultingTlas);
			dto.setVirtualBalance(account.virtualBalance());
			dto.setTotalBalance(totalBalance);

			return dto;

		} else {
			throw new UserAccountNotFoundException();
		}
	}

	@RequestMapping(value = "/encrypted-private-key", method = GET, produces = APPLICATION_JSON_UTF8_VALUE)
	@ResponseBody
	public EncryptedClientPrivateKeyDTO getEncryptedPrivateKey() throws BusinessException {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if(auth != null && userAccountService.userExists(auth.getName())) {
			UserAccount user = userAccountService.getByEmail(auth.getName());

			EncryptedClientPrivateKeyDTO dto = new EncryptedClientPrivateKeyDTO();
			dto.setEncryptedClientPrivateKey(user.getClientPrivateKeyEncrypted());
			return dto;

		} else {
			throw new UserAccountNotFoundException();
		}
	}

}
