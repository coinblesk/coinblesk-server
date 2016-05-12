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

import javax.persistence.Column;
import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.bitcoinj.core.Utils;

@Entity
@DiscriminatorValue("TIME_LOCKED_ADDRESS")
public class TimeLockedAddressEntity extends AddressEntity {
	
	@Column(nullable = false, updatable = false)
	private long lockTime;
	
	public long getLockTime() {
		return lockTime;
	}
	
	public TimeLockedAddressEntity setLockTime(long lockTime) {
		this.lockTime = lockTime;
		return this;
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
		return new EqualsBuilder()
				.appendSuper(super.equals(other))
				.append(lockTime, other.getLockTime())
				.isEquals();
	}
	
	@Override
	public int hashCode() {
	     return new HashCodeBuilder()
	    		 .appendSuper(super.hashCode())
	    		 .append(lockTime)
	    		 .toHashCode();
	}
	
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append(getClass().getSimpleName());
		sb.append("[Id=").append(getId());
		sb.append(", AddressHash=").append(Utils.HEX.encode(getAddressHash()));
		sb.append(", lockTime=").append(lockTime).append("]");
		return sb.toString();
	}
}
