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
import static java.math.MathContext.DECIMAL128;
import static org.springframework.http.MediaType.APPLICATION_JSON_UTF8_VALUE;
import static org.springframework.web.bind.annotation.RequestMethod.GET;
import static org.springframework.web.bind.annotation.RequestMethod.POST;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.params.TestNet3Params;
import org.bitcoinj.script.ScriptBuilder;
import org.bitcoinj.script.ScriptChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.security.access.annotation.Secured;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import com.coinblesk.dto.AccountBalanceDTO;
import com.coinblesk.dto.FundsDTO;
import com.coinblesk.dto.MicroPaymentViaEmailDTO;
import com.coinblesk.dto.TimeLockedAddressDTO;
import com.coinblesk.dto.UnspentTransactionOutputDTO;
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
import com.coinblesk.server.service.ForexBitcoinService;
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
	private final ForexBitcoinService forexService;

	@Autowired
	public AuthUserController(AppConfig appConfig, UserAccountService userAccountService, WalletService walletService, FeeService feeService, MicropaymentService microPaymentService, MessageSource messageSource, MailService mailService, ForexBitcoinService forexService) {
		this.appConfig = appConfig;
		this.userAccountService = userAccountService;
		this.walletService = walletService;
		this.feeService = feeService;
		this.microPaymentService = microPaymentService;
		this.messageSource = messageSource;
		this.mailService = mailService;
		this.forexService = forexService;
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

		long channelTransactionAmount = microPaymentService.getPendingChannelValue(account).longValue();
		long channelTransactionFees = microPaymentService.getPendingChannelFees(account).longValue();
		totalBalance -= channelTransactionAmount;
		totalBalance -= channelTransactionFees;
		totalBalance += account.virtualBalance();

		AccountBalanceDTO dto = new AccountBalanceDTO();
		dto.setTimeLockedAddresses(resultingTlas);
		dto.setVirtualBalance(account.virtualBalance());
		dto.setChannelTransactionAmount(channelTransactionAmount);
		dto.setChannelTransactionFees(channelTransactionFees);
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
		long satoshiBalance = 0;

		for(TimeLockedAddressEntity tlaEntity : tlaEntities) {
			String bitcoinAddress = tlaEntity.toAddress(params).toString();
			String addressUrl = "http://" + (params.getClass().equals(TestNet3Params.class) ? "tbtc." : "") + "blockr.io/address/info/" + tlaEntity.toAddress(params);
			Date createdAt = Date.from(Instant.ofEpochSecond(tlaEntity.getTimeCreated()));
			Instant lockedUntilInstant = Instant.ofEpochSecond(tlaEntity.getLockTime());
			Date lockedUntil = Date.from(lockedUntilInstant);
			boolean locked = lockedUntilInstant.isAfter(Instant.now());
			String redeemScript = SerializeUtils.bytesToHex(tlaEntity.getRedeemScript());

			Long balance = null;
			for(Map.Entry<Address, Coin> mapSet : balances.entrySet()) {
				Address address = mapSet.getKey();
				Coin coin = mapSet.getValue();
				if(address.toString().equals(bitcoinAddress)) {
					balance = coin.longValue();
					break;
				}
			}
			if (balance != null) {
				satoshiBalance += balance;
			}

			timeLockedAddresses.add(new TimeLockedAddressDTO(bitcoinAddress, addressUrl, createdAt, lockedUntil, locked, redeemScript, balance));
		}

		String clientPublicKey = SerializeUtils.bytesToHex(account.clientPublicKey());
		String serverPublicKey = SerializeUtils.bytesToHex(account.serverPublicKey());
		boolean locked = account.isLocked();

		long channelTransactionAmount = microPaymentService.getPendingChannelValue(account).longValue();
		long channelTransactionFees = microPaymentService.getPendingChannelFees(account).longValue();
		long totalChannelTransaction = channelTransactionAmount + channelTransactionFees;

		long virtualBalance = account.virtualBalance();
		long totalBalance = satoshiBalance + virtualBalance - totalChannelTransaction;

		return new FundsDTO(clientPublicKey, serverPublicKey, virtualBalance, totalBalance, totalChannelTransaction, locked, timeLockedAddresses);
	}

	@RequestMapping(value = "/payment/encrypted-private-key", method = GET, produces = APPLICATION_JSON_UTF8_VALUE)
	@ResponseBody
	public Map<String, String> getEncryptedPrivateKey() throws BusinessException {
		UserAccount user = getAuthenticatedUser();
		Map<String, String> map = new HashMap<>();
		map.put("encryptedClientPrivateKey", user.getClientPrivateKeyEncrypted());
		return map;
	}

	@RequestMapping(value = "/payment/utxo", method = GET, produces = APPLICATION_JSON_UTF8_VALUE)
	@ResponseBody
	public List<UnspentTransactionOutputDTO> getUnspentOutputsOfAddress(@NotNull @Valid @RequestParam("address") String addressString) throws BusinessException {
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

		List<TransactionOutput> UTXOs = walletService.getUTXOByAddress(foundTLA.toAddress(appConfig.getNetworkParameters()));
		List<UnspentTransactionOutputDTO> unspentOutputs = new ArrayList<>();
		for(TransactionOutput utxo : UTXOs) {
			if(!utxo.isAvailableForSpending()) {
				throw new CoinbleskInternalError("The unspent transaction output is not available for spending -> walletService returns wrong transaction outputs.");
			}

			UnspentTransactionOutputDTO utxoDTO = new UnspentTransactionOutputDTO();
			utxoDTO.setValue(utxo.getValue().longValue());
			utxoDTO.setIndex(utxo.getIndex());
			utxoDTO.setTransaction(SerializeUtils.bytesToHex(utxo.getParentTransaction().bitcoinSerialize()));
			unspentOutputs.add(utxoDTO);
		}

		return unspentOutputs;
	}

	@RequestMapping(value = "/payment/locked-address", method = GET, produces = APPLICATION_JSON_UTF8_VALUE)
	@ResponseBody
	public Map<String, String> getCurrentLockedAddress() throws BusinessException {
		UserAccount user = getAuthenticatedUser();

		if(user.getAccount() == null || user.getAccount().getTimeLockedAddresses().size() == 0) {
			throw new AccountNotFoundException();
		}

		TimeLockedAddressEntity lockedAddress = getLockedAddress(user.getAccount());

		Map<String, String> map = new HashMap<>();
		if(lockedAddress == null) {
			map.put("bitcoinAddress", null);
			map.put("redeemScript", null);
		} else {
			map.put("bitcoinAddress", lockedAddress.toAddress(appConfig.getNetworkParameters()).toString());

			if(lockedAddress.getRedeemScript() == null) {
				map.put("redeemScript", null);
			} else {
				map.put("redeemScript", SerializeUtils.bytesToHex(lockedAddress.getRedeemScript()));
			}
		}

		return map;
	}

	private TimeLockedAddressEntity getLockedAddress(Account account) {
		TimeLockedAddressEntity lockedAddress = null;
		for(TimeLockedAddressEntity tla : account.getTimeLockedAddresses()) {
			if(tla.isLocked()) {
				lockedAddress = tla;
				break;
			}
		}
		return lockedAddress;
	}

	@RequestMapping(value = "/payment/locked-address-of-email", method = GET, produces = APPLICATION_JSON_UTF8_VALUE)
	@ResponseBody
	public Map<String, String> getLockedAddressOfEmail(@RequestParam("email") String email) throws BusinessException {
		getAuthenticatedUser();

		Map<String, String> map = new HashMap<>();
		final String key = "bitcoinAddress";

		if(userAccountService.userExists(email)) {
			UserAccount user = userAccountService.getByEmail(email);
			if(user.getAccount() == null || user.hasUnregisteredToken() || getLockedAddress(user.getAccount()) == null) {
				map.put(key, null);
			} else {
				map.put(key, getLockedAddress(user.getAccount()).toAddress(appConfig.getNetworkParameters()).toString());
			}

		} else {
			map.put(key, null);
		}

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

	@RequestMapping(value = "/payment/remaining-channel-amount", method = GET, produces = APPLICATION_JSON_UTF8_VALUE)
	@ResponseBody
	public Map<String, Long> getMaxmimalAvailableChannelAmount() throws BusinessException {
		UserAccount user = getAuthenticatedUser();

		if(user.getAccount() == null) {
			throw new AccountNotFoundException();
		}

		BigDecimal btc_usd = forexService.getBitstampCurrentRateBTCUSD().getRate();
		long channelValueSatoshi = microPaymentService.getPendingChannelValue(user.getAccount()).longValue();
		BigDecimal channelValueBTC = new BigDecimal(channelValueSatoshi, DECIMAL128).divide(new BigDecimal(100000000, DECIMAL128));
		BigDecimal channelValueUSD = channelValueBTC.multiply(btc_usd, DECIMAL128);

		// 1USD is the buffer between the exchange rate and the max amount
		BigDecimal availableFundsUSD = new BigDecimal(appConfig.getMaximumChannelAmountUSD(), DECIMAL128)
				.subtract(channelValueUSD, DECIMAL128)
				.subtract(new BigDecimal(1, DECIMAL128));

		BigDecimal availableFundsSatoshi = new BigDecimal(0, DECIMAL128);

		if (availableFundsUSD.doubleValue() != 0.0) {
			BigDecimal availableFundsBTC = availableFundsUSD.divide(btc_usd, DECIMAL128);
			availableFundsSatoshi = availableFundsBTC.multiply(new BigDecimal(100000000, DECIMAL128), DECIMAL128);

			if(availableFundsSatoshi.doubleValue() < 0) {
				availableFundsSatoshi = new BigDecimal(0, DECIMAL128);
			}
		}

		Map<String, Long> map = new HashMap<>();
		map.put("amount", availableFundsSatoshi.longValue());
		return map;
	}

	@RequestMapping(value = "/payment/channel-transaction", method = GET, produces = APPLICATION_JSON_UTF8_VALUE)
	@ResponseBody
	public Map<String, Long> getChannelTransaction() throws BusinessException {
		UserAccount user = getAuthenticatedUser();
		if(user.getAccount() == null) {
			throw new AccountNotFoundException();
		}
		Account account = user.getAccount();

		Map<String, Long> map = new HashMap<>();
		map.put("amount", microPaymentService.getPendingChannelValue(account).longValue());
		map.put("fees", microPaymentService.getPendingChannelFees(account).longValue());
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
	@Transactional(isolation = Isolation.SERIALIZABLE)
	public void virtualPaymentViaEmail(Locale locale, @RequestBody @Valid VirtualPaymentViaEmailDTO dto) throws BusinessException {
		final String receiverEmail = dto.getReceiverEmail();
		final Long amount = dto.getAmount();

		UserAccount sender = getAuthenticatedUser();
		UserAccount receiver = null;

		if (sender.getAccount() == null) {
			throw new AccountNotFoundException();
		}

		if (userAccountService.userExists(receiverEmail)) {
			receiver = userAccountService.getByEmail(receiverEmail);
		} else {
			receiver = userAccountService.autoCreateWithRegistrationToken(receiverEmail);
		}

		if (sender.equals(receiver)) {
			throw new PaymentFailedException();
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

		if (receiver.hasUnregisteredToken()) {
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
		String btcAmount = (amount.doubleValue() / ONE_BITCOIN_IN_SATOSHI) + "";
		String url = appConfig.getFrontendUrl() + path;
		mailService.sendUserMail(receiverEmail,
				messageSource.getMessage("unregistered.email.title", null, locale),
				messageSource.getMessage("unregistered.email.text", new String[] { btcAmount, url }, locale));
	}

	@RequestMapping(value = "/payment/micro-payment-email", method = POST, produces = APPLICATION_JSON_UTF8_VALUE)
	@Transactional(isolation = Isolation.SERIALIZABLE)
	public void microPaymentViaEmail(Locale locale, @RequestBody @Valid MicroPaymentViaEmailDTO dto) throws BusinessException {
		final String receiverEmail = dto.getReceiverEmail();
		final Long amount = dto.getAmount();
		final String txHex = dto.getTransaction();

		UserAccount sender = getAuthenticatedUser();
		UserAccount receiver = null;

		if (sender.getAccount() == null) {
			throw new AccountNotFoundException();
		}

		if (userAccountService.userExists(receiverEmail)) {
			receiver = userAccountService.getByEmail(receiverEmail);
		} else {
			receiver = userAccountService.autoCreateWithRegistrationToken(receiverEmail);
		}

		if (sender.equals(receiver)) {
			throw new PaymentFailedException();
		}

		ECKey keySender = ECKey.fromPublicOnly(sender.getAccount().clientPublicKey());
		String hexKeyReceiver = DTOUtils.toHex(receiver.getAccount().clientPublicKey());
		long requestNonce = Instant.now().toEpochMilli();

		String transformedTxHex = transformTransaction(txHex);

		try {
			microPaymentService.microPayment(keySender, hexKeyReceiver, transformedTxHex, amount, requestNonce);
		} catch (CoinbleskInternalError e) {
			LOG.error("Error during micropayment: " + e.getMessage());
			throw e;
		} catch (Throwable e) {
			LOG.warn("Bad request for micropayment: " + e.getMessage(), e.getCause());
			throw new PaymentFailedException();
		}

		if(receiver.hasUnregisteredToken()) {
			sendEmailToUnregisteredUser(receiver.getEmail(), receiver.getUnregisteredToken(), amount, locale);
		}
	}

	@RequestMapping(value = "/payment/external-payment", method = POST, produces = APPLICATION_JSON_UTF8_VALUE)
	@Transactional(isolation = Isolation.SERIALIZABLE)
	public void externalPayment(@RequestParam("transaction") String transaction) throws BusinessException {
		UserAccount sender = getAuthenticatedUser();

		if (sender.getAccount() == null) {
			throw new AccountNotFoundException();
		}

		ECKey keySender = ECKey.fromPublicOnly(sender.getAccount().clientPublicKey());
		String tx = transformTransaction(transaction);
		long nonce = Instant.now().toEpochMilli();

		try {
			microPaymentService.microPayment(keySender, "", tx, 0L, nonce);
		} catch (CoinbleskInternalError e) {
			LOG.error("Error during external payment " + e.getMessage());
			throw e;
		} catch (Throwable e) {
			LOG.warn("Bad request for external payment " + e.getMessage());
			throw new PaymentFailedException();
		}
	}

	private String transformTransaction(String txInHex) throws BusinessException {
		byte[] txInBytes = DTOUtils.fromHex(txInHex);
		Transaction tx = new Transaction(appConfig.getNetworkParameters(), txInBytes);

		for (int i = 0; i < tx.getInputs().size(); i++) {
			List<ScriptChunk> chunks = tx.getInput(i).getScriptSig().getChunks();
			ScriptChunk relevantChunk = chunks.get(1);
			tx.getInput(i).setScriptSig(new ScriptBuilder().data(relevantChunk.data).build());
		}

		return DTOUtils.toHex(tx.bitcoinSerialize());
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
