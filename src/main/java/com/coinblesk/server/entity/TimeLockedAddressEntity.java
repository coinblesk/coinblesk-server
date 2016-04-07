package com.coinblesk.server.entity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.bitcoinj.core.Utils;

@Entity
@Table(name="TIME_LOCKED_ADDRESSES")
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
