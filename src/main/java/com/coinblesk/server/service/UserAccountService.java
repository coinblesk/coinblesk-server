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
package com.coinblesk.server.service;

import static com.coinblesk.server.config.UserRole.USER;
import static com.coinblesk.util.BitcoinUtils.ONE_BITCOIN_IN_SATOSHI;
import static java.util.Locale.ENGLISH;
import static java.util.UUID.randomUUID;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionOutput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.coinblesk.bitcoin.TimeLockedAddress;
import com.coinblesk.json.v1.Type;
import com.coinblesk.json.v1.UserAccountTO;
import com.coinblesk.server.config.AppConfig;
import com.coinblesk.server.dao.TimeLockedAddressRepository;
import com.coinblesk.server.dao.UserAccountRepository;
import com.coinblesk.server.dto.UserAccountCreateDTO;
import com.coinblesk.server.dto.UserAccountCreateVerifyDTO;
import com.coinblesk.server.dto.UserAccountDTO;
import com.coinblesk.server.dto.UserAccountForgotVerifyDTO;
import com.coinblesk.server.entity.UserAccount;
import com.coinblesk.server.exceptions.BusinessException;
import com.coinblesk.server.exceptions.EmailAlreadyRegisteredException;
import com.coinblesk.server.exceptions.InvalidEmailProvidedException;
import com.coinblesk.server.exceptions.InvalidEmailTokenException;
import com.coinblesk.server.exceptions.NoEmailProvidedException;
import com.coinblesk.server.exceptions.PasswordTooShortException;
import com.coinblesk.server.exceptions.UserAccountNotFoundException;
import com.coinblesk.util.BitcoinUtils;
import com.coinblesk.util.CoinbleskException;
import com.coinblesk.util.InsufficientFunds;

/**
 * @author draft
 */
@Service
public class UserAccountService {

	private final static Logger LOG = LoggerFactory.getLogger(UserAccountService.class);

	// as seen in: http://www.mkyong.com/regular-expressions/how-to-validate-email-address-with-regular-expression/
	public static final String EMAIL_PATTERN = "^[_A-Za-z0-9-+]+(\\.[_A-Za-z0-9-]+)*@[A-Za-z0-9-]+(\\.[A-Za-z0-9]+)*(\\.[A-Za-z]{2,})$";
	public static final int MINIMAL_PASSWORD_LENGTH = 6;

	private final UserAccountRepository repository;
	private final TimeLockedAddressRepository addressRepository;
	private final PasswordEncoder passwordEncoder;
	private final MailService mailService;
	private final AppConfig appConfig;
	private final WalletService walletService;

	@Autowired
	public UserAccountService(UserAccountRepository repository, TimeLockedAddressRepository addressRepository,
			PasswordEncoder passwordEncoder, MailService mailService, AppConfig appConfig,
			WalletService walletService) {
		this.repository = repository;
		this.addressRepository = addressRepository;
		this.passwordEncoder = passwordEncoder;
		this.mailService = mailService;
		this.appConfig = appConfig;
		this.walletService = walletService;
	}

	@Transactional(readOnly = true)
	public UserAccount getByEmail(String email) {
		return repository.findByEmail(email.toLowerCase(ENGLISH));
	}

	@Transactional(readOnly = true)
	public boolean userExists(String email) {
		UserAccount found = repository.findByEmail(email.toLowerCase(ENGLISH));
		return found != null;
	}

	@Transactional
	public UserAccount create(UserAccountCreateDTO userAccountCreateDTO) throws BusinessException {
		String email = userAccountCreateDTO.getEmail();

		if (email == null) {
			throw new NoEmailProvidedException();
		}
		if (!email.matches(EMAIL_PATTERN)) {
			throw new InvalidEmailProvidedException();
		}
		if (userAccountCreateDTO.getPassword() == null || userAccountCreateDTO.getPassword().length() < MINIMAL_PASSWORD_LENGTH) {
			throw new PasswordTooShortException();
		}
		if (userExists(email)) {
			throw new EmailAlreadyRegisteredException();
		}

		// convert DTO to Entity
		UserAccount userAccount = new UserAccount();
		userAccount.setEmail(userAccountCreateDTO.getEmail().toLowerCase(ENGLISH));
		userAccount.setPassword(passwordEncoder.encode(userAccountCreateDTO.getPassword()));
		userAccount.setCreationDate(new Date());
		userAccount.setDeleted(false);
		userAccount.setEmailToken(randomUUID().toString());
		userAccount.setUserRole(USER);
		userAccount.setBalance(BigDecimal.valueOf(0L).divide(BigDecimal.valueOf(ONE_BITCOIN_IN_SATOSHI)));
		repository.save(userAccount);

		return userAccount;
	}

	@Transactional
	public void activate(UserAccountCreateVerifyDTO createVerifyDTO) throws BusinessException {
		UserAccount userAccount = getByEmail(createVerifyDTO.getEmail());
		if (userAccount == null) {
			throw new UserAccountNotFoundException();
		}
		if (userAccount.getEmailToken() == null) {
			throw new InvalidEmailTokenException();
		}
		if (!userAccount.getEmailToken().equals(createVerifyDTO.getToken())) {
			throw new InvalidEmailTokenException();
		}

		userAccount.setEmailToken(null);
	}

	@Transactional
	public void delete(String email) throws BusinessException {

		UserAccount userAccount = repository.findByEmail(email);
		if (userAccount == null) {
			throw new UserAccountNotFoundException();
		}

		userAccount.setDeleted(true);
	}

	@Transactional(readOnly = true)
	public UserAccountDTO getDTO(String email) throws BusinessException {
		UserAccount userAccount = repository.findByEmail(email);
		if (userAccount == null) {
			throw new UserAccountNotFoundException();
		}
		long satoshi = userAccount.getBalance().multiply(new BigDecimal(ONE_BITCOIN_IN_SATOSHI)).longValue();

		UserAccountDTO userAccountDTO = new UserAccountDTO();
		userAccountDTO.setEmail(userAccount.getEmail());
		userAccountDTO.setBalance(satoshi);

		return userAccountDTO;
	}

	@Transactional
	public UserAccountTO transferP2SH(ECKey clientKey, String email) {
		final NetworkParameters params = appConfig.getNetworkParameters();
		final UserAccount userAccount = repository.findByEmail(email);
		if (userAccount == null) {
			return new UserAccountTO().type(Type.NO_ACCOUNT);
		}
		final ECKey pot = appConfig.getPotPrivateKeyAddress();
		long satoshi = userAccount	.getBalance()
									.multiply(new BigDecimal(ONE_BITCOIN_IN_SATOSHI))
									.longValue();

		List<TransactionOutput> outputs = walletService.potTransactionOutput(params);

		Transaction tx;
		try {
			TimeLockedAddress latestTLA = addressRepository.findTopByAccount_clientPublicKeyOrderByLockTimeDesc(
					clientKey.getPubKey()).toTimeLockedAddress();
			tx = BitcoinUtils.createTx(params, outputs, pot.toAddress(params), latestTLA.getAddress(params), satoshi,
					false);

			tx = BitcoinUtils.sign(params, tx, pot);
			BitcoinUtils.verifyTxFull(tx);
			LOG.debug("About tot zero balance with tx {}", tx);
			userAccount.setBalance(BigDecimal.ZERO);
			LOG.debug("About to broadcast tx");
			walletService.broadcast(tx);
			LOG.debug("Broadcast done");
			long satoshiNew = userAccount	.getBalance()
											.multiply(new BigDecimal(BitcoinUtils.ONE_BITCOIN_IN_SATOSHI))
											.longValue();

			final UserAccountTO userAccountTO = new UserAccountTO();
			userAccountTO.email(userAccount.getEmail()).balance(satoshiNew);
			return userAccountTO;
		} catch (CoinbleskException | InsufficientFunds e) {
			LOG.error("Cannot create transaction", e);
			mailService.sendAdminMail("transfer-p2sh error", "Cannot create transaction: " + e.getMessage());
			return new UserAccountTO().type(Type.ACCOUNT_ERROR).message(e.getMessage());
		}
	}

	@Transactional
	public void changePassword(String email, String password) throws BusinessException {
		final UserAccount userAccount = repository.findByEmail(email);
		if (userAccount == null) {
			throw new UserAccountNotFoundException();
		}
		if (password.length() < MINIMAL_PASSWORD_LENGTH) {
			throw new PasswordTooShortException();
		}

		userAccount.setPassword(passwordEncoder.encode(password));
	}

	@Transactional
	public void forgot(String email) throws BusinessException {
		UserAccount userAccount = getByEmail(email);
		if (userAccount == null) {
			throw new UserAccountNotFoundException();
		}

		String token = randomUUID().toString();
		userAccount.setForgotEmailToken(token);
	}

	@Transactional
	public void activateForgot(UserAccountForgotVerifyDTO forgotVerifyDTO) throws BusinessException {
		UserAccount userAccount = getByEmail(forgotVerifyDTO.getEmail());
		if (userAccount == null) {
			throw new UserAccountNotFoundException();
		}
		if (userAccount.getForgotEmailToken() == null || !userAccount.getForgotEmailToken().equals(forgotVerifyDTO.getToken())) {
			throw new InvalidEmailTokenException();
		}
		if (forgotVerifyDTO.getNewPassword().length() < MINIMAL_PASSWORD_LENGTH) {
			throw new PasswordTooShortException();
		}

		userAccount.setForgotEmailToken(null);
		userAccount.setPassword(passwordEncoder.encode(forgotVerifyDTO.getNewPassword()));
	}
}
