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

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import com.coinblesk.server.config.AppConfig;
import com.coinblesk.server.exceptions.InvalidLockTimeException;
import com.coinblesk.server.exceptions.UserNotFoundException;
import lombok.Data;
import lombok.NonNull;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.Utils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.coinblesk.bitcoin.TimeLockedAddress;
import com.coinblesk.server.dao.AccountRepository;
import com.coinblesk.server.dao.TimeLockedAddressRepository;
import com.coinblesk.server.entity.Account;
import com.coinblesk.server.entity.TimeLockedAddressEntity;

/**
 *
 * @author Thomas Bocek
 * @author Andreas Albrecht
 * @author Sebastian Stephan
 */
@Service
public class AccountService {

	private final AccountRepository accountRepository;

	private final TimeLockedAddressRepository timeLockedAddressRepository;

	private final AppConfig appConfig;

	@Autowired
	public AccountService(@NonNull AccountRepository accountRepository, @NonNull TimeLockedAddressRepository timeLockedAddressRepository, AppConfig appConfig) {
		this.accountRepository = accountRepository;
		this.timeLockedAddressRepository = timeLockedAddressRepository;
		this.appConfig = appConfig;
	}

	@Transactional(readOnly = true)
	public Account getByClientPublicKey(@NonNull final byte[] clientPublicKey) {
		return accountRepository.findByClientPublicKey(clientPublicKey);
	}

	/**
	 * Creates a new account for a given client public key.
	 * Returns the public key of the server for the newly created or already existing account.
	 *
	 * Idempodent: Calling this function with a public key that already exists will not make any changes.
	 *
	 * @param clientPublicKey The client public key for which an account should be generated
	 * @return The server ECKey public key associated with that account.
	 * 		   Does not contain the private key.
	 */
	@Transactional
	public ECKey createAcount( @NonNull ECKey clientPublicKey ) {

		// Check if client has already account
		final Account existingAccount = accountRepository.findByClientPublicKey(clientPublicKey.getPubKey());
		if (existingAccount != null) {
			return ECKey.fromPublicOnly(existingAccount.serverPublicKey());
		}

		// Not in database => Create new account with new server key pair
		ECKey serverKeyPair = new ECKey();
		final Account clientKey = new Account()
				.clientPublicKey(clientPublicKey.getPubKey())
				.serverPrivateKey(serverKeyPair.getPrivKeyBytes())
				.serverPublicKey(serverKeyPair.getPubKey())
				.timeCreated(Instant.now().getEpochSecond());

		final Account newAccount = accountRepository.save(clientKey);

		// Don't return the the private key of the server, makes it harder to not actually leak it somewhere.
		return ECKey.fromPublicOnly(newAccount.serverPublicKey());
	}

	@Transactional(readOnly = true)
	public List<Account> allAccounts() {
		return StreamSupport.stream(accountRepository.findAll().spliterator(), false)
				.collect(Collectors.toList());
	}

	@Data public static class CreateTimeLockedAddressResponse {
		@NonNull final private TimeLockedAddress timeLockedAddress;
		@NonNull final private ECKey serverPrivateKey;

	}

	@Transactional
	public CreateTimeLockedAddressResponse createTimeLockedAddress(@NonNull ECKey clientPublicKey, long lockTime)
			throws UserNotFoundException, InvalidLockTimeException {

		// Lock time must be valid
		final long minLockTime = Instant.now().getEpochSecond() + appConfig.getMinimumLockTimeSeconds();
		final long maxLockTime = Instant.now().plus(Duration.ofDays(appConfig.getMaximumLockTimeDays())).getEpochSecond();
		if (lockTime < minLockTime || lockTime > maxLockTime) {
			throw new InvalidLockTimeException();
		}

		// Get client for which a new address should be created
		Account client = accountRepository.findByClientPublicKey(clientPublicKey.getPubKey());
		if (client == null)
			throw new UserNotFoundException(clientPublicKey.getPublicKeyAsHex());

		// Create address
		final TimeLockedAddress address = new TimeLockedAddress(
				client.clientPublicKey(),
				client.serverPublicKey(),
				lockTime);

		// Check if address is already in database, if so nothing to do
		TimeLockedAddressEntity existingAddress =
				timeLockedAddressRepository.findByAddressHash(address.getAddressHash());

		ECKey serverPrivateKey = ECKey.fromPrivateAndPrecalculatedPublic(
				client.serverPrivateKey(),
				client.serverPublicKey());

		if (existingAddress != null) {
			return new CreateTimeLockedAddressResponse( address, serverPrivateKey );
		}

		// Create the new address entity and save
		TimeLockedAddressEntity addressEntity = new TimeLockedAddressEntity();
		addressEntity
				.setLockTime(address.getLockTime())
				.setAddressHash(address.getAddressHash())
				.setRedeemScript(address.createRedeemScript().getProgram())
				.setTimeCreated(Utils.currentTimeSeconds())
				.setAccount(client);
		timeLockedAddressRepository.save(addressEntity);

		return new CreateTimeLockedAddressResponse(address, serverPrivateKey);
	}

	@Transactional(readOnly = true)
	public long getVirtualBalanceByClientPublicKey(@NonNull byte[] publicKey) {
		if (publicKey.length == 0) {
			throw new IllegalArgumentException("publicKey must not be null");
		}
		return accountRepository.findByClientPublicKey(publicKey).virtualBalance();
	}

	public boolean addressExists(@NonNull byte[] addressHash) {
		return timeLockedAddressRepository.findByAddressHash(addressHash) != null;
	}

	TimeLockedAddress getTimeLockedAddressByAddressHash(@NonNull byte[] addressHash) {
		TimeLockedAddressEntity entity = timeLockedAddressRepository.findByAddressHash(addressHash);
		return entity == null ? null : TimeLockedAddress.fromRedeemScript(entity.getRedeemScript());
	}

}
