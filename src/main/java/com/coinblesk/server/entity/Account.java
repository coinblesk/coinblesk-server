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
package com.coinblesk.server.entity;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Objects;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.OneToOne;
import javax.persistence.Table;

/**
 * @author Thomas Bocek
 */
@Entity(name = "ACCOUNT")
@Table(indexes = {@Index(name = "ACCOUNT_CLIENT_PUBLIC_KEY", columnList = "CLIENT_PUBLIC_KEY")})
public class Account implements Serializable {

	private static final long serialVersionUID = -7496348013847426913L;

	@Id
	@GeneratedValue(strategy = GenerationType.AUTO)
	private long id;

	@Column(name = "CLIENT_PUBLIC_KEY", unique = true, nullable = false, updatable = false)
	private byte[] clientPublicKey;

	@Column(name = "SERVER_PUBLIC_KEY", unique = true, nullable = false, updatable = false)
	private byte[] serverPublicKey;

	@Column(name = "SERVER_PRIVATE_KEY", unique = true, nullable = false, updatable = false)
	private byte[] serverPrivateKey;

	@Column(name = "TIME_CREATED", updatable = false, nullable = false)
	private long timeCreated;

	@Column(name = "VIRTUAL_BALANCE", nullable = false)
	private long virtualBalance = 0L;

	@Column(name = "NONCE", nullable = false)
	private long nonce = 0L;

	@Column(name = "CHANNEL_TRANSACTION", length = 65536)
	private byte[] channelTransaction;

	@Column(name = "LOCKED", nullable = false)
	private boolean locked;

	@OneToOne(mappedBy = "account")
	private transient UserAccount userAccount;

	public byte[] clientPublicKey() {
		return clientPublicKey;
	}

	public Account clientPublicKey(byte[] clientPublicKey) {
		this.clientPublicKey = clientPublicKey;
		return this;
	}

	public byte[] serverPublicKey() {
		return serverPublicKey;
	}

	public Account serverPublicKey(byte[] serverPublicKey) {
		this.serverPublicKey = serverPublicKey;
		return this;
	}

	public byte[] serverPrivateKey() {
		return serverPrivateKey;
	}

	public Account serverPrivateKey(byte[] serverPrivateKey) {
		this.serverPrivateKey = serverPrivateKey;
		return this;
	}

	public long timeCreated() {
		return timeCreated;
	}

	public Account timeCreated(long timeCreatedSeconds) {
		this.timeCreated = timeCreatedSeconds;
		return this;
	}

	public long virtualBalance() {
		return virtualBalance;
	}

	public Account virtualBalance(long virtualBalance) {
		this.virtualBalance = virtualBalance;
		return this;
	}

	public long nonce() {
		return nonce;
	}

	public Account nonce(long nonce) {
		this.nonce = nonce;
		return this;
	}

	public byte[] getChannelTransaction() {
		return channelTransaction;
	}

	public Account channelTransaction(byte[] channelTransaction) {
		this.channelTransaction = channelTransaction;
		return this;
	}

	public boolean isLocked() {
		return locked;
	}

	public Account locked(boolean locked) {
		this.locked = locked;
		return this;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (o == null || getClass() != o.getClass())
			return false;
		Account account = (Account) o;

		return Arrays.equals(clientPublicKey, account.clientPublicKey) && Arrays.equals(serverPublicKey, account
			.serverPublicKey) && Arrays.equals(serverPrivateKey, account.serverPrivateKey);
	}

	@Override
	public int hashCode() {
		return Objects.hash(Arrays.hashCode(clientPublicKey), Arrays.hashCode(serverPublicKey), Arrays.hashCode
			(serverPrivateKey));
	}

}
