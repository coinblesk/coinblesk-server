package ch.uzh.csg.coinblesk.server.entity;

import javax.persistence.Column;
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
@Inheritance(strategy=InheritanceType.TABLE_PER_CLASS)
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
}
