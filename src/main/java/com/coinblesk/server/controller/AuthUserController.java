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
import static com.coinblesk.util.BitcoinUtils.ONE_BITCOIN_IN_SATOSHI;
import static org.springframework.http.MediaType.APPLICATION_JSON_UTF8_VALUE;
import static org.springframework.web.bind.annotation.RequestMethod.GET;
import static org.springframework.web.bind.annotation.RequestMethod.POST;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.transaction.Transactional;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.params.TestNet3Params;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.security.access.annotation.Secured;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import com.coinblesk.dto.AccountBalanceDTO;
import com.coinblesk.dto.EncryptedClientPrivateKeyDTO;
import com.coinblesk.dto.FundsDTO;
import com.coinblesk.dto.MicroPaymentViaEmailDTO;
import com.coinblesk.dto.TimeLockedAddressDTO;
import com.coinblesk.dto.VirtualPaymentViaEmailDTO;
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
import com.coinblesk.server.exceptions.InvalidAddressException;
import com.coinblesk.server.exceptions.InvalidAmountException;
import com.coinblesk.server.exceptions.InvalidNonceException;
import com.coinblesk.server.exceptions.InvalidRequestException;
import com.coinblesk.server.exceptions.PaymentFailedException;
import com.coinblesk.server.exceptions.UserAccountNotFoundException;
import com.coinblesk.server.exceptions.UserNotFoundException;
import com.coinblesk.server.service.FeeService;
import com.coinblesk.server.service.MailService;
import com.coinblesk.server.service.MicropaymentService;
import com.coinblesk.server.service.UserAccountService;
import com.coinblesk.server.service.WalletService;
import com.coinblesk.util.DTOUtils;
import com.coinblesk.util.InsufficientFunds;
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
	private final MicropaymentService microPaymentService;
	private final MessageSource messageSource;
	private final MailService mailService;

	@Autowired
	public AuthUserController(AppConfig appConfig, UserAccountService userAccountService, WalletService walletService, FeeService feeService, MicropaymentService microPaymentService, MessageSource messageSource, MailService mailService) {
		this.appConfig = appConfig;
		this.userAccountService = userAccountService;
		this.walletService = walletService;
		this.feeService = feeService;
		this.microPaymentService = microPaymentService;
		this.messageSource = messageSource;
		this.mailService = mailService;
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
		UserAccount user = getAuthenticatedUser();
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
	}

	@RequestMapping(value = "/funds", method = GET, produces = APPLICATION_JSON_UTF8_VALUE)
	@ResponseBody
	public FundsDTO getFunds() throws BusinessException {
		UserAccount userAccount = getAuthenticatedUser();
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
	}

	@RequestMapping(value = "/payment/encrypted-private-key", method = GET, produces = APPLICATION_JSON_UTF8_VALUE)
	@ResponseBody
	public EncryptedClientPrivateKeyDTO getEncryptedPrivateKey() throws BusinessException {
		UserAccount user = getAuthenticatedUser();
		EncryptedClientPrivateKeyDTO dto = new EncryptedClientPrivateKeyDTO();
		dto.setEncryptedClientPrivateKey(user.getClientPrivateKeyEncrypted());
		return dto;
	}

	@RequestMapping(value = "/payment/utxo", method = GET, produces = APPLICATION_JSON_UTF8_VALUE)
	@ResponseBody
	public List<TransactionOutput> getUnspentOutputsOfAddress(@NotNull @Valid @RequestParam("address") String addressString) throws BusinessException {
		UserAccount user = getAuthenticatedUser();

		if(user.getAccount() == null || user.getAccount().getTimeLockedAddresses().size() == 0) {
			throw new AccountNotFoundException();
		}

		TimeLockedAddressEntity foundTLA = null;
		for(TimeLockedAddressEntity tla : user.getAccount().getTimeLockedAddresses()) {
			if (addressString.equals(tla.toAddress(appConfig.getNetworkParameters()).toString())) {
				foundTLA = tla;
			}
		}
		if(foundTLA == null) {
			throw new InvalidAddressException();
		}

		return walletService.getUTXOByAddress(foundTLA.toAddress(appConfig.getNetworkParameters()));
	}

	@RequestMapping(value = "/payment/locked-address", method = GET, produces = APPLICATION_JSON_UTF8_VALUE)
	@ResponseBody
	public Map<String, String> getCurrentLockedAddress() throws BusinessException {
		UserAccount user = getAuthenticatedUser();

		if(user.getAccount() == null || user.getAccount().getTimeLockedAddresses().size() == 0) {
			throw new AccountNotFoundException();
		}

		TimeLockedAddressEntity lockedAddress = null;
		for(TimeLockedAddressEntity tla : user.getAccount().getTimeLockedAddresses()) {
			if(tla.isLocked()) {
				lockedAddress = tla;
				break;
			}
		}

		Map<String, String> map = new HashMap<>();
		map.put("bitcoinAddress", (lockedAddress == null) ? null : lockedAddress.toAddress(appConfig.getNetworkParameters()).toString());

		return map;
	}

	@RequestMapping(value = "/payment/fee", method = GET, produces = APPLICATION_JSON_UTF8_VALUE)
	@ResponseBody
	public Map<String, Integer> getFees() throws BusinessException {
		getAuthenticatedUser();

		Map<String, Integer> map = new HashMap<>();
		try {
			map.put("fee", feeService.fee());
		} catch (IOException e) {
			map.put("fee", null);
		}
		return map;
	}

	@RequestMapping(value = "/payment/channel-transaction", method = GET, produces = APPLICATION_JSON_UTF8_VALUE)
	@ResponseBody
	public Map<String, String> getChannelTransaction() throws BusinessException {
		UserAccount user = getAuthenticatedUser();
		if(user.getAccount() == null) {
			throw new AccountNotFoundException();
		}
		Account account = user.getAccount();

		Map<String, String> map = new HashMap<>();
		map.put("channelTransaction", (account.getChannelTransaction() == null) ? null : map.put("channelTransaction", SerializeUtils.bytesToHex(account.getChannelTransaction())));
		return map;
	}

	@RequestMapping(value = "/payment/server-pot-address", method = GET, produces = APPLICATION_JSON_UTF8_VALUE)
	@ResponseBody
	public Map<String, String> getServerPotAddress() throws BusinessException {
		getAuthenticatedUser();

		Address serverPotAddress = appConfig.getMicroPaymentPotPrivKey().toAddress(appConfig.getNetworkParameters());
		Map<String, String> map = new HashMap<>();
		map.put("serverPotAddress", serverPotAddress.toString());
		return map;
	}

	@RequestMapping(value = "/payment/virtual-payment-email", method = POST, produces = APPLICATION_JSON_UTF8_VALUE)
	@Transactional
	public void virtualPaymentViaEmail(Locale locale, @RequestBody @Valid VirtualPaymentViaEmailDTO dto) throws BusinessException {
		final String receiverEmail = dto.getReceiverEmail();
		final Long amount = dto.getAmount();
		boolean receiverWasAutoCreated = false;

		UserAccount sender = getAuthenticatedUser();
		UserAccount receiver = null;

		if (sender.getAccount() == null) {
			throw new AccountNotFoundException();
		}

		if (userAccountService.userExists(receiverEmail)) {
			receiver = userAccountService.getByEmail(receiverEmail);
		} else {
			receiverWasAutoCreated = true;
			receiver = userAccountService.autoCreateWithRegistrationToken(receiverEmail);
		}

		if (receiver.getAccount() == null) {
			throw new AccountNotFoundException();
		}

		ECKey keySender = ECKey.fromPublicOnly(sender.getAccount().clientPublicKey());
		ECKey keyReceiver = ECKey.fromPublicOnly(receiver.getAccount().clientPublicKey());
		long requestNonce = Instant.now().toEpochMilli();

		try {
			microPaymentService.virtualPayment(keySender, keyReceiver, amount, requestNonce);
		} catch (InvalidNonceException | InvalidAmountException | InsufficientFunds | UserNotFoundException | InvalidRequestException e) {
			throw new PaymentFailedException();
		} catch (Throwable e) {
			throw new CoinbleskInternalError("An internal error occured");
		}

		if (receiverWasAutoCreated) {
			sendEmailToUnregisteredUser(receiver.getEmail(), receiver.getUnregisteredToken(), amount, locale);
		}
	}

	private void sendEmailToUnregisteredUser(String receiverEmail, String unregisteredToken, Long amount, Locale locale) {
		LOG.debug("send unregistered email to {}", receiverEmail);
		String path = "";
		try {
			path = "registration/" + URLEncoder.encode(receiverEmail, "UTF-8") + "/" + unregisteredToken;
		} catch (UnsupportedEncodingException e) {
			// cannot happen because UTF-8 is hard-coded
			throw new CoinbleskInternalError("An internal error occurred.");
		}
		String btcAmount = (amount / ONE_BITCOIN_IN_SATOSHI) + "";
		String url = appConfig.getFrontendUrl() + path;
		mailService.sendUserMail(receiverEmail,
				messageSource.getMessage("unregistered.email.title", null, locale),
				messageSource.getMessage("unregistered.email.text", new String[] { btcAmount, url }, locale));
	}

	@RequestMapping(value = "/payment/micro-payment-email", method = POST, produces = APPLICATION_JSON_UTF8_VALUE)
	@Transactional
	public void microPaymentViaEmail(Locale locale, @RequestBody @Valid MicroPaymentViaEmailDTO dto) throws BusinessException {
		final String receiverEmail = dto.getReceiverEmail();
		final Long amount = dto.getAmount();
		final String txHex = dto.getTransaction();
		boolean receiverWasAutoCreated = false;

		UserAccount sender = getAuthenticatedUser();
		UserAccount receiver = null;

		if (sender.getAccount() == null) {
			throw new AccountNotFoundException();
		}

		if (userAccountService.userExists(receiverEmail)) {
			receiver = userAccountService.getByEmail(receiverEmail);
		} else {
			receiverWasAutoCreated = true;
			receiver = userAccountService.autoCreateWithRegistrationToken(receiverEmail);
		}

		ECKey keySender = ECKey.fromPublicOnly(sender.getAccount().clientPublicKey());
		String hexKeyReceiver = DTOUtils.toHex(receiver.getAccount().clientPublicKey());
		long requestNonce = Instant.now().toEpochMilli();

		try {
			microPaymentService.microPayment(keySender, hexKeyReceiver, txHex, amount, requestNonce);
		} catch (CoinbleskInternalError e) {
			LOG.error("Error during micropayment: " + e.getMessage());
			throw e;
		} catch (Throwable e) {
			LOG.warn("Bad request for micropayment: " + e.getMessage(), e.getCause());
			throw new PaymentFailedException();
		}

		if(receiverWasAutoCreated) {
			sendEmailToUnregisteredUser(receiver.getEmail(), receiver.getUnregisteredToken(), amount, locale);
		}
	}

	private UserAccount getAuthenticatedUser() throws UserAccountNotFoundException {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if(auth == null || !userAccountService.userExists(auth.getName())) {
			throw new UserAccountNotFoundException();
		}

		UserAccount userAccount = userAccountService.getByEmail(auth.getName());
		if (userAccount.isDeleted() || userAccount.hasUnregisteredToken()) {
			throw new UserAccountNotFoundException();
		}

		return userAccount;
	}

}
