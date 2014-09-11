package ch.uzh.csg.mbps.server.domain;

import java.io.Serializable;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

@Entity
@Table(name = "SERVER_PUBLIC_KEY", indexes = {@Index(name = "SERVER_ID_INDEX_PKI",  columnList="SERVER_ID")})
public class ServerPublicKey implements Serializable{
	private static final long serialVersionUID = 6501617333724668051L;

	@Id
	@SequenceGenerator(name="pk_sequence", sequenceName="server_public_key_id_seq", allocationSize=1)
	@GeneratedValue(strategy=GenerationType.SEQUENCE, generator="pk_sequence")
	@Column(name = "ID", nullable = false)
	private long id;
	@Column(name = "SERVER_ID", nullable = false)
	private long serverId;
	@Column(name = "KEY_NUMBER", nullable = false)
	private byte keyNumber;
	@Column(name = "KEY_ALGORITHM", nullable = false)
	private byte pkiAlgorithm;
	@Column(name = "PUBLIC_KEY", nullable = false)
	private String publicKey;
	
	public ServerPublicKey() {
	}
	
	public ServerPublicKey(long serverId, byte keyNumber, byte pkiAlgorithm, String publicKey) {
		this.serverId = serverId;
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

	public long getServerId() {
		return serverId;
	}

	public void setUserId(long serverId) {
		this.serverId = serverId;
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
		sb.append("Server id: ");
		sb.append(getServerId());
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
		return new EqualsBuilder().append(getServerId(), other.getUserId())
				.append(getKeyNumber(), other.getKeyNumber())
				.append(getPKIAlgorithm(), other.getPKIAlgorithm())
				.append(getPublicKey(), other.getPublicKey()).isEquals();
	}

	@Override
	public int hashCode() {
		return new HashCodeBuilder(73, 89).append(getServerId())
				.append(getKeyNumber())
				.append(getPKIAlgorithm())
				.append(getPublicKey()).toHashCode();
	}
	
}
