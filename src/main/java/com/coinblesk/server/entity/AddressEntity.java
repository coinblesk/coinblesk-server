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

import java.util.Comparator;

import javax.persistence.Column;
import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.Inheritance;
import javax.persistence.InheritanceType;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Utils;

@Entity(name = "ADDRESSES")
@Table(indexes = {
	    @Index(name = "ADDRESS_HASH_INDEX", columnList = "addressHash", unique = true)})
@Inheritance(strategy=InheritanceType.SINGLE_TABLE)
@DiscriminatorValue("P2SH_ADDRESS")
public class AddressEntity {
	
	@Id
	@GeneratedValue(strategy=GenerationType.SEQUENCE)
	private long id;
	
	@ManyToOne
    @JoinColumn(name="keys_fk")
	private Keys keys;
	
	@Column(nullable = false, updatable = false)
	private long timeCreated;
	
	@Column(nullable = false, unique=true, updatable = false, length=255)
	private byte[] addressHash;
	
	@Column(nullable = false, updatable = false, length=4096)
	private byte[] redeemScript;
	
	public long getId() {
		return id;
	}
	
	public AddressEntity setId(long id) {
		this.setId(id);
		return this;
	}
	
	public Keys getKeys() {
		return keys;
	}
	
	public AddressEntity setKeys(Keys keys) {
		this.keys = keys;
		return this;
	}
	
	public long getTimeCreated() {
		return timeCreated;
	}
	
	public AddressEntity setTimeCreated(long timeCreatedSeconds) {
		this.timeCreated = timeCreatedSeconds;
		return this;
	}
	
	public byte[] getAddressHash() {
		return addressHash;
	}
	
	public AddressEntity setAddressHash(byte[] addressHash) {
		this.addressHash = addressHash;
		return this;
	}
	
	public byte[] getRedeemScript() {
		return redeemScript;
	}
	
	public AddressEntity setRedeemScript(byte[] redeemScript) {
		this.redeemScript = redeemScript;
		return this;
	}
	
	public Address toAddress(NetworkParameters params) {
		return Address.fromP2SHHash(params, addressHash);
	}
	
	public String toString(NetworkParameters params) {
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
		
		if (!(object instanceof AddressEntity)) {
			return false;
		}
		
		final AddressEntity other = (AddressEntity) object;
		return new EqualsBuilder()
				.append(id, other.getId())
				.append(addressHash, other.getAddressHash())
				.append(redeemScript, other.getRedeemScript())
				.isEquals();
	}
	
	@Override
	public int hashCode() {
	     return new HashCodeBuilder()
	    		 .append(id)
	    		 .append(addressHash)
	    		 .append(redeemScript)
	    		 .toHashCode();
	}
	
	/**
	 * Sorts addresses by {@link AddressEntity#timeCreated} in ascending order.
	 */
	public static class TimeCreatedComparator implements Comparator<AddressEntity> {
		@Override
		public int compare(AddressEntity lhs, AddressEntity rhs) {
			return Long.compare(lhs.getTimeCreated(), rhs.getTimeCreated());
		}
		
	}
}
