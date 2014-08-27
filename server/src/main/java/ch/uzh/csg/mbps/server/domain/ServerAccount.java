package ch.uzh.csg.mbps.server.domain;

import java.math.BigDecimal;
import java.util.Date;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import javax.persistence.Index;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;


@Entity(name="SERVER_ACCOUNT")
@Table(indexes={
		@Index(name="URL_INDEX", columnList="URL")})
public class ServerAccount {
	
	@Id
	@SequenceGenerator(name="pk_sequence",sequenceName="serveraccount_id_seq", allocationSize=1)
	@GeneratedValue(strategy=GenerationType.SEQUENCE,generator="pk_sequence")
	@Column(name = "ID", nullable = false)
	private long id;
	@Column(name = "URL", unique = true, nullable = false)
	private String url;
	@Temporal(TemporalType.TIMESTAMP)
	@Column(name = "CREATION_DATE", nullable = false)
	private Date creationDate;
	@Column(name = "EMAIL", nullable = false)
	private String email;
	@Column(name = "PAYIN_ADDRESS")
	private String payinAddress;
	@Column(name = "PAYOUT_ADDRESS")
	private String payoutAddress;
	@Column(name = "TRUST_LEVEL", nullable = false)
	private int trustLevel;
	@Column(name = "PUBLIC_KEY", length = 1024, nullable = false)
	private String publicKey;
	@Column(name = "DELETED", nullable = false)
	private boolean deleted;
	@Column(name = "ACTIVE_BALANCE", nullable = false, precision = 25, scale = 8)
	private BigDecimal activeBalance;
	@Column(name = "BALANCE_LIMIT", nullable = false, precision = 25, scale = 8)
	private BigDecimal balanceLimit;
	
	public ServerAccount(){
	}
	
	/**
	 * Creates a new server account with active balance 0, actual timestamp and given
	 * parameters.
	 * 
	 * @param url
	 *            != NULL
	 * @param email
	 *            != NULL
	 * @param public key
	 *            != NULL
	 */
	public ServerAccount(String url, String email, String publicKey) {
		this.url = url;
		this.email = email;
		this.deleted = false;
		this.balanceLimit = new BigDecimal(0.0);
		this.activeBalance = new BigDecimal(0.0);
		this.creationDate = new Date();
		this.publicKey = publicKey;
		this.trustLevel = 0;
	}
	
	public ServerAccount(String url, String email, String publicKey, int trustLevel, BigDecimal balanceLimit){
		this.url = url;
		this.email = email;
		this.deleted = false;
		this.balanceLimit = balanceLimit;
		this.activeBalance = new BigDecimal(0.0);
		this.creationDate = new Date();
		this.publicKey = publicKey;
		this.trustLevel = trustLevel;
	}

	public long getId() {
		return id;
	}

	public void setId(long id) {
		this.id = id;
	}

	public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		this.url = url;
	}

	public Date getCreationDate() {
		return creationDate;
	}

	public void setCreationDate(Date creationDate) {
		this.creationDate = creationDate;
	}

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email;
	}

	public String getPayinAddress() {
		return payinAddress;
	}

	public void setPayouAddress(String btcAddress) {
		this.payoutAddress = btcAddress;
	}
	
	public String getPayoutAddress() {
		return payoutAddress;
	}

	public void setPayinAddress(String btcAddress) {
		this.payinAddress = btcAddress;
	}

	public int getTrustLevel() {
		return trustLevel;
	}

	public void setTrustLevel(int trustLevel) {
		this.trustLevel = trustLevel;
	}

	public String getPublicKey() {
		return publicKey;
	}

	public void setPublicKey(String publicKey) {
		this.publicKey = publicKey;
	}

	public boolean isDeleted() {
		return deleted;
	}

	public void setDeleted(boolean deleted) {
		this.deleted = deleted;
	}

	public BigDecimal getActiveBalance() {
		return activeBalance;
	}

	public void setActiveBalance(BigDecimal activeBalance) {
		this.activeBalance = activeBalance;
	}

	public BigDecimal getBalanceLimit() {
		return balanceLimit;
	}

	public void setBalanceLimit(BigDecimal balanceLimit) {
		this.balanceLimit = balanceLimit;
	}

	@Override
	public String toString(){
		StringBuilder sb = new StringBuilder();
		sb.append("id: ");
		sb.append(getId());
		sb.append(", url: ");
		sb.append(getUrl());
		sb.append(", email: ");
		sb.append(getEmail());
		sb.append(", isDeleted: ");
		sb.append(isDeleted());
		sb.append(", creationDate: ");
		sb.append(getCreationDate());
		sb.append(", active balance: ");
		sb.append(getActiveBalance());
		sb.append(", trust level: ");
		sb.append(getTrustLevel());
		return sb.toString();
	}
	
	@Override
	public boolean equals(Object o) {
		if (o == null)
			return false;

		if (o == this)
			return true;

		if (!(o instanceof ServerAccount))
			return false;

		ServerAccount other = (ServerAccount) o;
		return new EqualsBuilder().append(getId(), other.getId())
				.append(getUrl(), other.getUrl())
				.append(getEmail(), other.getEmail())
				.append(isDeleted(), other.isDeleted())
				.append(getCreationDate(), other.getCreationDate())
				.append(getActiveBalance(), other.getActiveBalance())
				.append(getTrustLevel(), other.getTrustLevel()).isEquals();
	}
	
	@Override
	public int hashCode() {
		return new HashCodeBuilder(31, 59).append(getId())
				.append(getUrl()).append(getEmail())
				.append(getCreationDate()).append(isDeleted())
				.append(getActiveBalance())
				.append(getTrustLevel()).toHashCode();
	}
}