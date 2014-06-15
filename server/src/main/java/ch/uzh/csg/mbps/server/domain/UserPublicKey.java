package ch.uzh.csg.mbps.server.domain;

import java.io.Serializable;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.SequenceGenerator;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.hibernate.annotations.Index;

@Entity(name = "USER_PUBLIC_KEY")
public class UserPublicKey implements Serializable {
	private static final long serialVersionUID = -5668060751789666658L;
	
	@Id
	@SequenceGenerator(name="pk_sequence", sequenceName="user_public_key_id_seq", allocationSize=1)
	@GeneratedValue(strategy=GenerationType.SEQUENCE, generator="pk_sequence")
	@Column(name = "ID", nullable = false)
	private long id;
	@Column(name = "USER_ID", nullable = false)
	@Index(name = "USER_ID_INDEX_PKI")
	private long userId;
	@Column(name = "KEY_NUMBER", nullable = false)
	private byte keyNumber;
	@Column(name = "KEY_ALGORITHM", nullable = false)
	private byte pkiAlgorithm;
	@Column(name = "PUBLIC_KEY", nullable = false)
	private String publicKey;
	
	public UserPublicKey() {
	}
	
	public UserPublicKey(long userid, byte keyNumber, byte pkiAlgorithm, String publicKey) {
		this.userId = userid;
		this.keyNumber = keyNumber;
		this.pkiAlgorithm = pkiAlgorithm;
		this.publicKey = publicKey;
	}
	
	public long getId() {
		return id;
	}
	
	public void setId(long id) {
		this.id = id;
	}

	public long getUserId() {
		return userId;
	}

	public void setUserId(long userId) {
		this.userId = userId;
	}

	public byte getKeyNumber() {
		return keyNumber;
	}

	public void setKeyNumber(byte keyNumber) {
		this.keyNumber = keyNumber;
	}

	public byte getPKIAlgorithm() {
		return pkiAlgorithm;
	}

	public void setPKIAlgorithm(byte pkiAlgorithm) {
		this.pkiAlgorithm = pkiAlgorithm;
	}

	public String getPublicKey() {
		return publicKey;
	}

	public void setPublicKey(String publicKey) {
		this.publicKey = publicKey;
	}
	
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("user id: ");
		sb.append(getUserId());
		sb.append(", key number: ");
		sb.append(getKeyNumber());
		sb.append(", pki algorithm");
		sb.append(getPKIAlgorithm());
		return sb.toString();
	}
	
	@Override
	public boolean equals(Object o) {
		if (o == null)
			return false;

		if (o == this)
			return true;

		if (!(o instanceof UserPublicKey))
			return false;

		UserPublicKey other = (UserPublicKey) o;
		return new EqualsBuilder().append(getUserId(), other.getUserId())
				.append(getKeyNumber(), other.getKeyNumber())
				.append(getPKIAlgorithm(), other.getPKIAlgorithm())
				.append(getPublicKey(), other.getPublicKey()).isEquals();
	}

	@Override
	public int hashCode() {
		return new HashCodeBuilder(73, 89).append(getUserId())
				.append(getKeyNumber())
				.append(getPKIAlgorithm())
				.append(getPublicKey()).toHashCode();
	}
	
}