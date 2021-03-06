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

import static com.coinblesk.json.v1.Type.ACCOUNT_ERROR;
import static com.coinblesk.server.config.UserRole.ADMIN;
import static com.coinblesk.server.config.UserRole.ROLE_USER;
import static com.coinblesk.server.config.UserRole.USER;
import static com.coinblesk.server.enumerator.EventType.ACCOUNT_COULD_NOT_BE_CREATED;
import static com.coinblesk.util.BitcoinUtils.ONE_BITCOIN_IN_SATOSHI;
import static java.util.Locale.ENGLISH;
import static java.util.UUID.randomUUID;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.List;

import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.core.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.coinblesk.bitcoin.TimeLockedAddress;
import com.coinblesk.dto.UserAccountAdminDTO;
import com.coinblesk.dto.UserAccountCreateDTO;
import com.coinblesk.dto.UserAccountCreateVerifyDTO;
import com.coinblesk.dto.UserAccountCreateWithTokenDTO;
import com.coinblesk.dto.UserAccountDTO;
import com.coinblesk.dto.UserAccountForgotVerifyDTO;
import com.coinblesk.json.v1.Type;
import com.coinblesk.json.v1.UserAccountTO;
import com.coinblesk.server.config.AppConfig;
import com.coinblesk.server.dao.TimeLockedAddressRepository;
import com.coinblesk.server.dao.UserAccountRepository;
import com.coinblesk.server.entity.Account;
import com.coinblesk.server.entity.UserAccount;
import com.coinblesk.server.enumerator.EventType;
import com.coinblesk.server.exceptions.BusinessException;
import com.coinblesk.server.exceptions.CoinbleskInternalError;
import com.coinblesk.server.exceptions.EmailAlreadyRegisteredException;
import com.coinblesk.server.exceptions.InvalidEmailProvidedException;
import com.coinblesk.server.exceptions.InvalidEmailTokenException;
import com.coinblesk.server.exceptions.InvalidKeyProvidedException;
import com.coinblesk.server.exceptions.PasswordTooShortException;
import com.coinblesk.server.exceptions.UserAccountDeletedException;
import com.coinblesk.server.exceptions.UserAccountHasUnregisteredToken;
import com.coinblesk.server.exceptions.UserAccountNotActivatedException;
import com.coinblesk.server.exceptions.UserAccountNotFoundException;
import com.coinblesk.server.exceptions.UserAccountUnregisteredTokenInvalid;
import com.coinblesk.util.BitcoinUtils;
import com.coinblesk.util.CoinbleskException;
import com.coinblesk.util.DTOUtils;
import com.coinblesk.util.InsufficientFunds;
import com.coinblesk.util.SerializeUtils;

/**
 * @author Thomas Bocek
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
	private final AppConfig appConfig;
	private final WalletService walletService;
	private final EventService eventService;
	private final AccountService accountService;

	@Autowired
	public UserAccountService(UserAccountRepository repository, TimeLockedAddressRepository addressRepository,
			PasswordEncoder passwordEncoder, AppConfig appConfig, WalletService walletService, EventService eventService,
			AccountService accountService) {
		this.repository = repository;
		this.addressRepository = addressRepository;
		this.passwordEncoder = passwordEncoder;
		this.appConfig = appConfig;
		this.walletService = walletService;
		this.eventService = eventService;
		this.accountService = accountService;
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
		String password = userAccountCreateDTO.getPassword();
		String privateKey = userAccountCreateDTO.getClientPrivateKeyEncrypted();
		String publicKey = userAccountCreateDTO.getClientPublicKey().toLowerCase();
		Long lockTime = userAccountCreateDTO.getLockTime();

		if (!email.matches(EMAIL_PATTERN)) {
			throw new InvalidEmailProvidedException();
		}
		if (password.length() < MINIMAL_PASSWORD_LENGTH) {
			throw new PasswordTooShortException();
		}
		if (userExists(email)) {
			if (getByEmail(email).isDeleted()) {
				throw new UserAccountDeletedException();
			} else if(getByEmail(email).hasUnregisteredToken()) {
				throw new UserAccountHasUnregisteredToken();
			} else {
				throw new EmailAlreadyRegisteredException();
			}
		}

		// the encrypted private key must be valid base64
		try {
			Base64.getDecoder().decode(privateKey);
		} catch(IllegalArgumentException e) {
			throw new InvalidKeyProvidedException();
		}

		Account account = createAccountAndTimeLockedAddress(publicKey, lockTime);

		// convert DTO to Entity
		UserAccount userAccount = new UserAccount();
		userAccount.setAccount(account);
		userAccount.setEmail(userAccountCreateDTO.getEmail().toLowerCase(ENGLISH));
		userAccount.setPassword(passwordEncoder.encode(userAccountCreateDTO.getPassword()));
		userAccount.setCreationDate(new Date());
		userAccount.setDeleted(false);
		userAccount.setActivationEmailToken(randomUUID().toString());
		userAccount.setUserRole(USER);
		userAccount.setUnregisteredToken(null);
		userAccount.setBalance(BigDecimal.valueOf(0L).divide(BigDecimal.valueOf(ONE_BITCOIN_IN_SATOSHI)));
		userAccount.setClientPrivateKeyEncrypted(userAccountCreateDTO.getClientPrivateKeyEncrypted());
		repository.save(userAccount);

		return userAccount;
	}

	private Account createAccountAndTimeLockedAddress(String publicKey, long lockTime) {
		// creates the (bitcoin) account
		Account account = null;
		try {
			accountService.createAccount(DTOUtils.getECKeyFromHexPublicKey(publicKey));
			// retrieve the account
			byte[] publicKeyBytes = null;
			publicKeyBytes = Utils.HEX.decode(publicKey);
			account = accountService.getByClientPublicKey(publicKeyBytes);
		} catch(Exception e) {
			eventService.error(ACCOUNT_COULD_NOT_BE_CREATED, "Account with public key " + publicKey + " could not be created: createAccount failed or publicKey is invalid.");
			throw new CoinbleskInternalError("Account could not be created");
		}

		// create timelockedaddress
		try {
			ECKey publicECKey = DTOUtils.getECKeyFromHexPublicKey(publicKey);
			AccountService.CreateTimeLockedAddressResponse response = accountService.createTimeLockedAddress(publicECKey, lockTime);
			TimeLockedAddress tla = response.getTimeLockedAddress();
			walletService.addWatching(tla.getAddress(appConfig.getNetworkParameters()));

		} catch(Exception e) {
			eventService.error(ACCOUNT_COULD_NOT_BE_CREATED, "Time Locked Address could not be created for account with client public key " + publicKey);
			throw new CoinbleskInternalError("Account could not be created");
		}

		return account;
	}

	@Transactional
	public UserAccount autoCreateWithRegistrationToken(String email) throws BusinessException {

		// password and keys are set during the activation
		String password = randomUUID().toString();
		String unregisteredToken = randomUUID().toString();
		ECKey key = new ECKey();

		if (!email.matches(EMAIL_PATTERN)) {
			throw new InvalidEmailProvidedException();
		}
		if (userExists(email)) {
			if (getByEmail(email).isDeleted()) {
				throw new UserAccountDeletedException();
			} else {
				throw new EmailAlreadyRegisteredException();
			}
		}

		Account account = null;
		try {
			accountService.createAccount(DTOUtils.getECKeyFromHexPublicKey(key.getPublicKeyAsHex()));
			byte[] publicKeyBytes = Utils.HEX.decode(key.getPublicKeyAsHex());
			account = accountService.getByClientPublicKey(publicKeyBytes);

		} catch(Exception e) {
			eventService.error(ACCOUNT_COULD_NOT_BE_CREATED, "Temporary account for " + email + " could not be created: createAccount failed or publicKey is invalid");
			throw new CoinbleskInternalError("Account could not be created");
		}

		UserAccount userAccount = new UserAccount();
		userAccount.setAccount(account);
		userAccount.setEmail(email);
		userAccount.setPassword(passwordEncoder.encode(password));
		userAccount.setCreationDate(new Date());
		userAccount.setDeleted(false);
		userAccount.setActivationEmailToken(null);
		userAccount.setUserRole(USER);
		userAccount.setBalance(BigDecimal.valueOf(0L));
		userAccount.setClientPrivateKeyEncrypted(key.getPrivateKeyAsHex());
		userAccount.setUnregisteredToken(unregisteredToken);
		repository.save(userAccount);

		return userAccount;
	}

	@Transactional
	public void activateWithRegistrationToken(UserAccountCreateWithTokenDTO dto) throws BusinessException {

		String email = dto.getEmail();
		String password = dto.getPassword();
		String privateKey = dto.getClientPrivateKeyEncrypted();
		String publicKey = dto.getClientPublicKey();
		String unregisteredToken = dto.getUnregisteredToken();
		Long lockTime = dto.getLockTime();
		UserAccount userAccount = getByEmail(email);

		if (!userExists(email)) {
			throw new UserAccountNotFoundException();
		}
		if (!email.matches(EMAIL_PATTERN)) {
			throw new InvalidEmailProvidedException();
		}
		if (password.length() < MINIMAL_PASSWORD_LENGTH) {
			throw new PasswordTooShortException();
		}
		if (!userAccount.hasUnregisteredToken() || unregisteredToken == null) {
			throw new UserAccountUnregisteredTokenInvalid();
		}
		if (!unregisteredToken.equals(userAccount.getUnregisteredToken())) {
			throw new UserAccountUnregisteredTokenInvalid();
		}

		Account accountA = userAccount.getAccount();
		Account accountB = createAccountAndTimeLockedAddress(publicKey, lockTime);
		accountService.moveVirtualBalanceFromAToBAndDeleteA(accountA, accountB);

		userAccount.setAccount(accountB);
		userAccount.setPassword(passwordEncoder.encode(password));
		userAccount.setCreationDate(new Date());
		userAccount.setActivationEmailToken(null);
		userAccount.setClientPrivateKeyEncrypted(privateKey);
		userAccount.setUnregisteredToken(null);
		repository.save(userAccount);
	}

	@Transactional
	public void activate(UserAccountCreateVerifyDTO createVerifyDTO) throws BusinessException {
		UserAccount userAccount = getByEmail(createVerifyDTO.getEmail());
		if (userAccount == null) {
			throw new UserAccountNotFoundException();
		}
		if (userAccount.getActivationEmailToken() == null) {
			throw new InvalidEmailTokenException();
		}
		if (userAccount.isDeleted()) {
			throw new UserAccountDeletedException();
		}
		if (userAccount.hasUnregisteredToken()) {
			throw new UserAccountHasUnregisteredToken();
		}
		if (!userAccount.getActivationEmailToken().equals(createVerifyDTO.getToken())) {
			throw new InvalidEmailTokenException();
		}

		userAccount.setActivationEmailToken(null);
	}

	@Transactional
	public void delete(String email) throws BusinessException {
		UserAccount userAccount = repository.findByEmail(email);
		if (userAccount == null) {
			throw new UserAccountNotFoundException();
		}

		userAccount.setDeleted(true);
	}

	@Transactional
	public void undelete(String email) throws BusinessException {
		UserAccount userAccount = repository.findByEmail(email);
		if (userAccount == null) {
			throw new UserAccountNotFoundException();
		}

		userAccount.setDeleted(false);
	}

	@Transactional
	public void switchRole(String email) throws BusinessException {
		UserAccount userAccount = repository.findByEmail(email);
		if (userAccount == null) {
			throw new UserAccountNotFoundException();
		}

		if(ROLE_USER.equals(userAccount.getUserRole())) {
			userAccount.setUserRole(ADMIN);
		} else {
			userAccount.setUserRole(USER);
		}
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

	@Transactional(readOnly = true)
	public UserAccountAdminDTO getAdminDTO(String email) throws BusinessException {
		UserAccount userAccount = repository.findByEmail(email);
		if (userAccount == null) {
			throw new UserAccountNotFoundException();
		}
		return mapUserAccountToAdminDTO(userAccount);
	}

	@Transactional(readOnly = true)
	public List<UserAccountAdminDTO> getAllAdminDTO() {
		List<UserAccount> userAccounts = repository.findAllByOrderByCreationDateAsc();
		List<UserAccountAdminDTO> userAccountAdminDTOs = new ArrayList<>();
		for(UserAccount userAccount : userAccounts) {
			UserAccountAdminDTO userAccountAdminDTO = mapUserAccountToAdminDTO(userAccount);
			userAccountAdminDTOs.add(userAccountAdminDTO);
		}
		return userAccountAdminDTOs;
	}

	private UserAccountAdminDTO mapUserAccountToAdminDTO(UserAccount userAccount) {
		UserAccountAdminDTO userAccountAdminDTO = new UserAccountAdminDTO();
		userAccountAdminDTO.setBalance(userAccount.getBalance().longValue());
		userAccountAdminDTO.setEmail(userAccount.getEmail());
		userAccountAdminDTO.setCreationDate(userAccount.getCreationDate());
		userAccountAdminDTO.setUserRole(userAccount.getUserRole());
		userAccountAdminDTO.setDeleted(userAccount.isDeleted());
		userAccountAdminDTO.setActivated(userAccount.isActivationVerified());
		userAccountAdminDTO.setUnregistered(userAccount.hasUnregisteredToken());
		if(userAccount.getAccount() != null) {
			Account account = userAccount.getAccount();
			userAccountAdminDTO.setAccountPublicKeyClient(SerializeUtils.bytesToHex(account.clientPublicKey()));
		}
		return userAccountAdminDTO;
	}

	@Transactional
	public UserAccountTO transferP2SH(ECKey clientKey, String email) {
		final NetworkParameters params = appConfig.getNetworkParameters();
		final UserAccount userAccount = repository.findByEmail(email);
		if (userAccount == null) {
			return new UserAccountTO().type(Type.NO_ACCOUNT);
		}
		final ECKey pot = appConfig.getPotPrivKey();
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
			long satoshiNew = userAccount.getBalance()
											.multiply(new BigDecimal(BitcoinUtils.ONE_BITCOIN_IN_SATOSHI))
											.longValue();

			final UserAccountTO userAccountTO = new UserAccountTO();
			userAccountTO.email(userAccount.getEmail()).balance(satoshiNew);
			return userAccountTO;
		} catch (CoinbleskException | InsufficientFunds e) {
			LOG.error("Cannot create transaction", e);
			eventService.error(EventType.USER_ACCOUNT_COULD_NOT_TRANSFER_P2SH, "Cannot create transaction: " + e.getMessage());
			return new UserAccountTO().type(ACCOUNT_ERROR).message(e.getMessage());
		}
	}

	@Transactional
	public void changePassword(String email, String password) throws BusinessException {
		final UserAccount userAccount = repository.findByEmail(email);
		if (userAccount == null) {
			throw new UserAccountNotFoundException();
		}
		if (userAccount.getActivationEmailToken() != null) {
			throw new UserAccountNotActivatedException();
		}
		if (userAccount.isDeleted()) {
			throw new UserAccountDeletedException();
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
		if (userAccount.isDeleted()) {
			throw new UserAccountDeletedException();
		}
		if (userAccount.hasUnregisteredToken()) {
			throw new UserAccountHasUnregisteredToken();
		}
		if (userAccount.getActivationEmailToken() != null) {
			throw new UserAccountNotActivatedException();
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
		if (userAccount.isDeleted()) {
			throw new UserAccountDeletedException();
		}
		if (userAccount.hasUnregisteredToken()) {
			throw new UserAccountHasUnregisteredToken();
		}
		if (userAccount.getActivationEmailToken() != null) {
			throw new UserAccountNotActivatedException();
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
