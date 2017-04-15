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

import com.coinblesk.bitcoin.TimeLockedAddress;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Utils;

import javax.persistence.*;
import java.util.Comparator;

@Entity(name = "TIME_LOCKED_ADDRESS")
@Table(indexes = {@Index(name = "ADDRESS_HASH_INDEX", columnList = "ADDRESS_HASH", unique = true)})
public class TimeLockedAddressEntity {

	@Id
	@Column(name = "ID", nullable = false, unique = true, updatable = false)
	@GeneratedValue(strategy = GenerationType.AUTO)
	private long id;

	@ManyToOne
	@JoinColumn(name = "ACCOUNT_FK")
	private Account account;

	@Column(name = "TIME_CREATED", nullable = false, updatable = false)
	private long timeCreated;

	@Column(name = "ADDRESS_HASH", nullable = false, unique = true, updatable = false)
	private byte[] addressHash;

	@Column(name = "REDEEM_SCRIPT", nullable = false, updatable = false, length = 4096)
	private byte[] redeemScript;

	@Column(name = "LOCK_TIME", nullable = false, updatable = false)
	private long lockTime;

	public TimeLockedAddress toTimeLockedAddress() {
		return TimeLockedAddress.fromRedeemScript(this.redeemScript);
	}

	private long getId() {
		return id;
	}

	public TimeLockedAddressEntity setId(long id) {
		this.id = id;
		return this;
	}

	public Account getAccount() {
		return account;
	}

	public TimeLockedAddressEntity setAccount(Account account) {
		this.account = account;
		return this;
	}

	public long getTimeCreated() {
		return timeCreated;
	}

	public TimeLockedAddressEntity setTimeCreated(long timeCreatedSeconds) {
		this.timeCreated = timeCreatedSeconds;
		return this;
	}

	public byte[] getAddressHash() {
		return addressHash;
	}

	public TimeLockedAddressEntity setAddressHash(byte[] addressHash) {
		this.addressHash = addressHash;
		return this;
	}

	public byte[] getRedeemScript() {
		return redeemScript;
	}

	public TimeLockedAddressEntity setRedeemScript(byte[] redeemScript) {
		this.redeemScript = redeemScript;
		return this;
	}

	public Address toAddress(NetworkParameters params) {
		return Address.fromP2SHHash(params, addressHash);
	}

	public long getLockTime() {
		return lockTime;
	}

	public TimeLockedAddressEntity setLockTime(long lockTime) {
		this.lockTime = lockTime;
		return this;
	}

	private String toString(NetworkParameters params) {
		StringBuilder sb = new StringBuilder();
		sb.append(getClass().getSimpleName());
		sb.append("[Id=").append(id);
		sb.append(", AddressHash=").append(Utils.HEX.encode(addressHash));
		if (params != null) {
			sb.append(", Address=").append(toAddress(params));
		}
		sb.append("]");
		return sb.toString();
	}

	@Override
	public String toString() {
		return toString(null);
	}

	@Override
	public boolean equals(Object object) {
		if (object == null) {
			return false;
		}

		if (object == this) {
			return true;
		}

		if (!(object instanceof TimeLockedAddressEntity)) {
			return false;
		}

		final TimeLockedAddressEntity other = (TimeLockedAddressEntity) object;
		return new EqualsBuilder().append(id, other.getId()).append(addressHash, other.getAddressHash()).append
			(redeemScript, other.getRedeemScript()).isEquals();
	}

	@Override
	public int hashCode() {
		return new HashCodeBuilder().append(id).append(addressHash).append(redeemScript).toHashCode();
	}

	/**
	 * Sorts addresses by {@link TimeLockedAddressEntity#timeCreated} in
	 * ascending order.
	 */
	private static class TimeCreatedComparator implements Comparator<TimeLockedAddressEntity> {
		@Override
		public int compare(TimeLockedAddressEntity lhs, TimeLockedAddressEntity rhs) {
			return Long.compare(lhs.getTimeCreated(), rhs.getTimeCreated());
		}

	}
}
