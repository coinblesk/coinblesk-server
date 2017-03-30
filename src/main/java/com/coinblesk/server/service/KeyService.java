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

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import com.coinblesk.server.exceptions.InvalidLockTimeException;
import com.coinblesk.server.exceptions.UserNotFoundException;
import com.coinblesk.util.BitcoinUtils;
import lombok.NonNull;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.Utils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.coinblesk.bitcoin.TimeLockedAddress;
import com.coinblesk.server.dao.KeyRepository;
import com.coinblesk.server.dao.TimeLockedAddressRepository;
import com.coinblesk.server.entity.Account;
import com.coinblesk.server.entity.TimeLockedAddressEntity;
import com.coinblesk.util.Pair;

/**
 *
 * @author Thomas Bocek
 * @author Andreas Albrecht
 * @author Sebastian Stephan
 */
@Service
public class KeyService {

	private final KeyRepository keyRepository;

	private final TimeLockedAddressRepository timeLockedAddressRepository;

	@Autowired
	public KeyService(@NonNull KeyRepository keyRepository, @NonNull TimeLockedAddressRepository timeLockedAddressRepository) {
		this.keyRepository = keyRepository;
		this.timeLockedAddressRepository = timeLockedAddressRepository;
	}

	@Transactional(readOnly = true)
	public Account getByClientPublicKey(@NonNull final byte[] clientPublicKey) {
		return keyRepository.findByClientPublicKey(clientPublicKey);
	}

	@Transactional(readOnly = true)
	public List<ECKey> getPublicECKeysByClientPublicKey(final byte[] clientPublicKey) {
		final Account account = keyRepository.findByClientPublicKey(clientPublicKey);
		final List<ECKey> retVal = new ArrayList<>(2);
		retVal.add(ECKey.fromPublicOnly(account.clientPublicKey()));
		retVal.add(ECKey.fromPublicOnly(account.serverPublicKey()));
		return retVal;
	}

	@Transactional(readOnly = true)
	public List<ECKey> getECKeysByClientPublicKey(@NonNull final byte[] clientPublicKey) {
		final Account account = keyRepository.findByClientPublicKey(clientPublicKey);
		if (account == null) {
			return Collections.emptyList();
		}
		final List<ECKey> retVal = new ArrayList<>(2);
		retVal.add(ECKey.fromPublicOnly(account.clientPublicKey()));
		retVal.add(ECKey.fromPrivateAndPrecalculatedPublic(account.serverPrivateKey(), account.serverPublicKey()));
		return retVal;
	}

	@Transactional
	public Pair<Boolean, Account> storeKeysAndAddress(
			@NonNull final byte[] clientPublicKey,
			@NonNull final byte[] serverPublicKey,
			@NonNull final byte[] serverPrivateKey) {

		// need to check if it exists here, as not all DBs do that for us
		final Account account = keyRepository.findByClientPublicKey(clientPublicKey);
		if (account != null) {
			return new Pair<>(false, account);
		}

		final Account clientKey = new Account()
				.clientPublicKey(clientPublicKey)
				.serverPrivateKey(serverPrivateKey)
				.serverPublicKey(serverPublicKey)
				.timeCreated(Instant.now().getEpochSecond());

		final Account storedAccount = keyRepository.save(clientKey);
		return new Pair<>(true, storedAccount);
	}

	@Transactional(readOnly = true)
	public List<List<ECKey>> all() {
		final Iterable<Account> all = keyRepository.findAll();
		final List<List<ECKey>> retVal = new ArrayList<>();
		for (Account entity : all) {
			final List<ECKey> keys = new ArrayList<>(2);
			keys.add(ECKey.fromPublicOnly(entity.clientPublicKey()));
			keys.add(ECKey.fromPublicOnly(entity.serverPublicKey()));
			retVal.add(keys);
		}
		return retVal;
	}

	@Transactional(readOnly = true)
	public List<Account> allKeys() {
		return StreamSupport.stream(keyRepository.findAll().spliterator(), false)
				.collect(Collectors.toList());
	}

	@Transactional
	public TimeLockedAddress createTimeLockedAddress(@NonNull ECKey clientPublicKey, long lockTime)
			throws UserNotFoundException, InvalidLockTimeException {

		// Lock time must be valid
		if (!BitcoinUtils.isLockTimeByTime(lockTime) ||
				BitcoinUtils.isAfterLockTime(Instant.now().getEpochSecond(), lockTime)) {
			throw new InvalidLockTimeException();
		}

		// Get client for which a new address should be created
		Account client = keyRepository.findByClientPublicKey(clientPublicKey.getPubKey());
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
		if (existingAddress != null)
			return address;

		// Create the new address entity and save
		TimeLockedAddressEntity addressEntity = new TimeLockedAddressEntity();
		addressEntity
				.setLockTime(address.getLockTime())
				.setAddressHash(address.getAddressHash())
				.setRedeemScript(address.createRedeemScript().getProgram())
				.setTimeCreated(Utils.currentTimeSeconds())
				.setAccount(client);
		timeLockedAddressRepository.save(addressEntity);

		return address;
	}

	@Transactional(readOnly = true)
	public long getVirtualBalanceByClientPublicKey(@NonNull byte[] publicKey) {
		if (publicKey.length == 0) {
			throw new IllegalArgumentException("publicKey must not be null");
		}
		return keyRepository.findByClientPublicKey(publicKey).virtualBalance();
	}

	public boolean addressExists(@NonNull byte[] addressHash) {
		return timeLockedAddressRepository.findByAddressHash(addressHash) != null;
	}

	TimeLockedAddressEntity getTimeLockedAddressByAddressHash(@NonNull byte[] addressHash) {
		return timeLockedAddressRepository.findByAddressHash(addressHash);
	}

	List<TimeLockedAddressEntity> getTimeLockedAddressesByClientPublicKey(byte[] publicKey) {
		if (publicKey == null || publicKey.length <= 0) {
			throw new IllegalArgumentException("publicKey must not be null");
		}
		return timeLockedAddressRepository.findByAccount_ClientPublicKey(publicKey);
	}

	byte[] getRedeemScriptByAddressHash(byte[] addressHash) {
		TimeLockedAddressEntity address = getTimeLockedAddressByAddressHash(addressHash);
		return address != null ? address.getRedeemScript() : null;
	}
}
