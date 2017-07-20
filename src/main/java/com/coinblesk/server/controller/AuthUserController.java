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

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.params.TestNet3Params;
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
import com.coinblesk.dto.FundsDTO;
import com.coinblesk.dto.PaymentRequirementsDTO;
import com.coinblesk.dto.PaymentRequirementsRequestDTO;
import com.coinblesk.dto.TimeLockedAddressDTO;
import com.coinblesk.json.v1.BaseTO;
import com.coinblesk.json.v1.Type;
import com.coinblesk.json.v1.UserAccountTO;
import com.coinblesk.server.config.AppConfig;
import com.coinblesk.server.entity.Account;
import com.coinblesk.server.entity.TimeLockedAddressEntity;
import com.coinblesk.server.entity.UserAccount;
import com.coinblesk.server.exceptions.AccountNotFoundException;
import com.coinblesk.server.exceptions.BusinessException;
import com.coinblesk.server.exceptions.CoinbleskInternalError;
import com.coinblesk.server.exceptions.UserAccountNotFoundException;
import com.coinblesk.server.service.FeeService;
import com.coinblesk.server.service.PaymentForkService;
import com.coinblesk.server.service.UserAccountService;
import com.coinblesk.server.service.WalletService;
import com.coinblesk.util.SerializeUtils;

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
	private final FeeService feeService;
	private final PaymentForkService paymentForkService;

	@Autowired
	public AuthUserController(AppConfig appConfig, UserAccountService userAccountService, WalletService walletService, FeeService feeService, PaymentForkService paymentForkService) {
		this.appConfig = appConfig;
		this.userAccountService = userAccountService;
		this.walletService = walletService;
		this.feeService = feeService;
		this.paymentForkService = paymentForkService;
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

	@RequestMapping(value = "/funds", method = GET, produces = APPLICATION_JSON_UTF8_VALUE)
	@ResponseBody
	public FundsDTO getFunds() throws BusinessException {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if(auth != null && userAccountService.userExists(auth.getName())) {
			UserAccount userAccount = userAccountService.getByEmail(auth.getName());
			Account account = userAccount.getAccount();

			if(account == null) {
				throw new AccountNotFoundException();
			}

			NetworkParameters params = appConfig.getNetworkParameters();
			Map<Address, Coin> balances = walletService.getBalanceByAddresses();

			List<TimeLockedAddressEntity> tlaEntities = account.getTimeLockedAddresses();
			List<TimeLockedAddressDTO> timeLockedAddresses = new ArrayList<>();

			for(TimeLockedAddressEntity tlaEntity : tlaEntities) {
				String bitcoinAddress = tlaEntity.toAddress(params).toString();
				String addressUrl = "http://" + (params.getClass().equals(TestNet3Params.class) ? "tbtc." : "") + "blockr.io/address/info/" + tlaEntity.toAddress(params);
				Date createdAt = Date.from(Instant.ofEpochSecond(tlaEntity.getTimeCreated()));
				Instant lockedUntilInstant = Instant.ofEpochSecond(tlaEntity.getLockTime());
				Date lockedUntil = Date.from(lockedUntilInstant);
				boolean locked = lockedUntilInstant.isAfter(Instant.now());

				Long balance = null;
				for(Map.Entry<Address, Coin> mapSet : balances.entrySet()) {
					Address address = mapSet.getKey();
					Coin coin = mapSet.getValue();
					if(address.toString().equals(bitcoinAddress)) {
						balance = coin.longValue();
						break;
					}
				}

				timeLockedAddresses.add(new TimeLockedAddressDTO(bitcoinAddress, addressUrl, createdAt, lockedUntil, locked, balance));
			}

			String clientPublicKey = SerializeUtils.bytesToHex(account.clientPublicKey());
			String serverPublicKey = SerializeUtils.bytesToHex(account.serverPublicKey());
			Long virtualBalance = account.virtualBalance();
			boolean locked = account.isLocked();

			return new FundsDTO(clientPublicKey, serverPublicKey, virtualBalance, locked, timeLockedAddresses);

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

	@RequestMapping(value = "/payment-requirements", method = POST, produces = APPLICATION_JSON_UTF8_VALUE)
	@ResponseBody
	public PaymentRequirementsDTO getPaymentRequirement(@RequestBody PaymentRequirementsRequestDTO requestDTO) throws BusinessException {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth != null && userAccountService.userExists(auth.getName())) {
			UserAccount user = userAccountService.getByEmail(auth.getName());

			PaymentRequirementsDTO dto = new PaymentRequirementsDTO();
			dto.setEncryptedClientPrivateKey(user.getClientPrivateKeyEncrypted());

			try {
				dto.setCurrentTransactionFees(feeService.fee());
			} catch (IOException e) {
				throw new CoinbleskInternalError("The fees could not be loaded.");
			}

			FundsDTO funds = getFunds();
			long totalLockedAndVirtualBalance = funds.getVirtualBalance();
			for(TimeLockedAddressDTO tla :funds.getTimeLockedAddresses()) {
				if(tla.getLocked()) {
					totalLockedAndVirtualBalance += tla.getBalance();
				}
			}
			dto.setTotalLockedAndVirtualBalance(totalLockedAndVirtualBalance);

			// TODO set previous transactions
			dto.setPreviousTransactions(new ArrayList<>());

			dto.setPaymentDecisionDTO(paymentForkService.getPaymentDecision(user.getAccount(), requestDTO.getReceiver(), requestDTO.getAmount()));

			return dto;

		} else {
			throw new UserAccountNotFoundException();
		}
	}

}
