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

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

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

import com.coinblesk.json.v1.Type;
import com.coinblesk.json.v1.UserAccountStatusTO;
import com.coinblesk.json.v1.UserAccountTO;
import com.coinblesk.server.config.AppConfig;
import com.coinblesk.server.config.UserRole;
import com.coinblesk.server.dao.UserAccountRepository;
import com.coinblesk.server.entity.Account;
import com.coinblesk.server.entity.UserAccount;
import com.coinblesk.util.BitcoinUtils;
import com.coinblesk.util.CoinbleskException;
import com.coinblesk.util.InsufficientFunds;
import com.coinblesk.util.Pair;

/**
 *
 * @author draft
 */
@Service
public class UserAccountService {

	private final static SecureRandom random = new SecureRandom();

	// as senn in:
	// http://www.mkyong.com/regular-expressions/how-to-validate-email-address-with-regular-expression/
	public static final String EMAIL_PATTERN = "^[_A-Za-z0-9-+]+(\\.[_A-Za-z0-9-]+)*@"
			+ "[A-Za-z0-9-]+(\\.[A-Za-z0-9]+)*(\\.[A-Za-z]{2,})$";

	private final static Logger LOG = LoggerFactory.getLogger(UserAccountService.class);

	@Autowired
	private UserAccountRepository repository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private MailService mailService;

	@Autowired
	private AppConfig appConfig;

	@Autowired
	private WalletService walletService;

	@Autowired
	private AccountService accountService;

	@Transactional(readOnly = true)
	public UserAccount getByEmail(String email) {
		return repository.findByEmail(email);
	}

	// TODO: only used for testing. remove if possible
	@Transactional()
	public void save(UserAccount userAccount) {
		repository.save(userAccount);
	}

	@Transactional()
	public Pair<UserAccountStatusTO, UserAccount> create(final UserAccountTO userAccountTO) {
		final String email = userAccountTO.email();
		if (email == null) {
			return new Pair<>(new UserAccountStatusTO().type(Type.NO_EMAIL), null);
		}
		if (!email.matches(EMAIL_PATTERN)) {
			return new Pair<>(new UserAccountStatusTO().type(Type.INVALID_EMAIL), null);
		}
		if (userAccountTO.password() == null || userAccountTO.password().length() < 6) {
			return new Pair<>(new UserAccountStatusTO().type(Type.PASSWORD_TOO_SHORT), null);
		}

		userAccountTO.balance(0);
		return createEntity(userAccountTO);
	}

	// call this for testing only directy!!
	@Transactional()
	private Pair<UserAccountStatusTO, UserAccount> createEntity(final UserAccountTO userAccountTO) {
		final String email = userAccountTO.email().toLowerCase(Locale.ENGLISH);
		final UserAccount found = repository.findByEmail(email);
		if (found != null) {
			if (found.getEmailToken() != null) {
				return new Pair<>(new UserAccountStatusTO().type(Type.SUCCESS_BUT_EMAIL_ALREADY_EXISTS_NOT_ACTIVATED),
						found);
			}
			return new Pair<>(new UserAccountStatusTO().type(Type.SUCCESS_BUT_EMAIL_ALREADY_EXISTS_ACTIVATED), found);
		}
		// convert TO to Entity
		UserAccount userAccount = new UserAccount();
		userAccount.setEmail(email);
		userAccount.setPassword(passwordEncoder.encode(userAccountTO.password()));
		userAccount.setCreationDate(new Date());
		userAccount.setDeleted(false);
		userAccount.setEmailToken(UUID.randomUUID().toString());
		userAccount.setUserRole(UserRole.USER);
		userAccount.setBalance(BigDecimal.valueOf(userAccountTO.balance())
				.divide(BigDecimal.valueOf(BitcoinUtils.ONE_BITCOIN_IN_SATOSHI)));
		repository.save(userAccount);
		return new Pair<>(new UserAccountStatusTO().setSuccess(), userAccount);
	}

	@Transactional()
	public UserAccountStatusTO activate(String email, String token) {
		final UserAccount found = repository.findByEmail(email);
		if (found == null) {
			// no such email address - notok
			return new UserAccountStatusTO().type(Type.NO_EMAIL);
		}
		if (found.getEmailToken() == null) {
			// already acitaveted - ok
			return new UserAccountStatusTO().setSuccess();
		}

		if (!found.getEmailToken().equals(token)) {
			// wrong token - notok
			return new UserAccountStatusTO().type(Type.INVALID_EMAIL_TOKEN);
		}

		// activate, enitiy is in attached state
		found.setEmailToken(null);
		return new UserAccountStatusTO().setSuccess();
	}

	@Transactional()
	public UserAccountStatusTO delete(String email) {

		final UserAccount found = repository.findByEmail(email);
		if (found == null) {
			// no such email address - notok
			return new UserAccountStatusTO().type(Type.NO_EMAIL);
		}
		found.setDeleted(true);
		return new UserAccountStatusTO().setSuccess();
	}

	@Transactional(readOnly = true)
	public UserAccountTO get(String email) {
		final UserAccount userAccount = repository.findByEmail(email);
		if (userAccount == null) {
			return null;
		}
		long satoshi = userAccount.getBalance()
				.multiply(new BigDecimal(BitcoinUtils.ONE_BITCOIN_IN_SATOSHI))
				.longValue();
		final UserAccountTO userAccountTO = new UserAccountTO();
		userAccountTO.email(userAccount.getEmail()).balance(satoshi);
		return userAccountTO;
	}

	@Transactional()
	public UserAccountTO transferP2SH(ECKey clientKey, String email) {
		final NetworkParameters params = appConfig.getNetworkParameters();
		final UserAccount userAccount = repository.findByEmail(email);
		if (userAccount == null) {
			return new UserAccountTO().type(Type.NO_ACCOUNT);
		}
		final ECKey pot = appConfig.getPotPrivateKeyAddress();
		long satoshi = userAccount.getBalance()
				.multiply(new BigDecimal(BitcoinUtils.ONE_BITCOIN_IN_SATOSHI))
				.longValue();

		List<TransactionOutput> outputs = walletService.potTransactionOutput(params);

		// TODO: get current timelocked multisig address
		Account account = accountService.getByClientPublicKey(clientKey.getPubKey());
		Transaction tx;
		try {
			tx = BitcoinUtils.createTx(params, outputs, pot.toAddress(params),
					account.latestTimeLockedAddresses().toAddress(params), satoshi, false);

			tx = BitcoinUtils.sign(params, tx, pot);
			BitcoinUtils.verifyTxFull(tx);
			LOG.debug("About tot zero balance with tx {}", tx);
			userAccount.setBalance(BigDecimal.ZERO);
			LOG.debug("About to broadcast tx");
			walletService.broadcast(tx);
			LOG.debug("Broadcast done");
			long satoshiNew = userAccount.getBalance()
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

	// for debugging
	@Transactional(readOnly = true)
	public String getToken(String email) {
		final UserAccount userAccount = repository.findByEmail(email);
		if (userAccount == null) {
			return null;
		}
		return userAccount.getEmailToken();
	}

	@Transactional()
	public UserAccountStatusTO changePassword(String email, String password) {
		final UserAccount found = repository.findByEmail(email);
		if (found == null) {
			// no such email address - notok
			return new UserAccountStatusTO().type(Type.NO_EMAIL);
		}
		found.setPassword(passwordEncoder.encode(password));
		return new UserAccountStatusTO().setSuccess();
	}

	@Transactional()
	public UserAccountStatusTO activateForgot(String email, String forgetToken) {
		final UserAccount found = repository.findByEmail(email);
		if (found == null) {
			// no such email address - notok
			return new UserAccountStatusTO().type(Type.NO_EMAIL);
		}
		if (found.getForgotEmailToken() == null) {
			// wrong no password forget requested
			return new UserAccountStatusTO().type(Type.INVALID_EMAIL_TOKEN);
		}

		if (!found.getForgotEmailToken().equals(forgetToken)) {
			// wrong token - notok
			return new UserAccountStatusTO().type(Type.INVALID_EMAIL_TOKEN);
		}

		// activate, change password
		found.setForgotEmailToken(null);
		found.setPassword(found.getForgotPassword());

		return new UserAccountStatusTO().setSuccess();
	}

	@Transactional()
	public Pair<UserAccountStatusTO, UserAccountTO> forgot(String email) {
		final UserAccount found = repository.findByEmail(email);
		if (found == null) {
			LOG.debug("no such email found in DB {}", email);
			// no such email address - notok
			return new Pair<UserAccountStatusTO, UserAccountTO>(new UserAccountStatusTO().type(Type.NO_EMAIL), null);
		}
		String password = createPassword();
		found.setForgotPassword(passwordEncoder.encode(password));
		String token = UUID.randomUUID().toString();
		found.setForgotEmailToken(token);
		UserAccountTO to = new UserAccountTO();
		to.email(found.getEmail());
		to.password(password);
		to.message(token);
		return new Pair<UserAccountStatusTO, UserAccountTO>(new UserAccountStatusTO().setSuccess(), to);
	}

	private static String createPassword() {
		// put here all characters that are allowed in password
		final char[] ALLOWED_CHARACTERS = "abcdefghijkmnopqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ123456789".toCharArray();
		final int PASSWORD_LENGTH = 8;

		final StringBuffer password = new StringBuffer();

		final int len = ALLOWED_CHARACTERS.length;
		for (int i = 0; i < PASSWORD_LENGTH; i++) {
			password.append(ALLOWED_CHARACTERS[random.nextInt(len)]);
		}
		return password.toString();
	}
}
