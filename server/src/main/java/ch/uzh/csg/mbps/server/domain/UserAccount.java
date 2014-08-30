package ch.uzh.csg.mbps.server.domain;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import ch.uzh.csg.mbps.server.util.UserRoles.Role;

@Entity
@Table(name = "USER_ACCOUNT", indexes = {@Index(name = "USERNAME_INDEX",  columnList="USERNAME")})
public class UserAccount implements Serializable {
	private static final long serialVersionUID = 1L;
	
	@Id
	@SequenceGenerator(name="pk_sequence",sequenceName="useracccount_id_seq", allocationSize=1)
	@GeneratedValue(strategy=GenerationType.SEQUENCE,generator="pk_sequence")
	@Column(name = "ID", nullable = false)
	private long id;
	@Temporal(TemporalType.TIMESTAMP)
	@Column(name = "CREATIONDATE", nullable = false)
	private Date creationDate;
	@Column(name = "USERNAME", unique = true, nullable = false)
	private String username;
	@Column(name = "EMAIL", unique = true, nullable = false)
	private String email;
	@Column(name = "PASSWORD", nullable = false)
	private String password;
	@Column(name = "DELETED", nullable = false)
	private boolean deleted;
	@Column(name = "BALANCE", nullable = false, precision = 25, scale = 8)
	private BigDecimal balance;
	@Column(name = "EMAIL_VERIFIED")
	private boolean emailVerified;
	@Column(name = "PAYMENT_ADDRESS")
	private String paymentAddress;
	@Column(name = "ROLES")
	private byte roles;
	@Column(name = "NOF_KEYS")
	private int nofKeys;
	
	public UserAccount() {
	}

	/**
	 * Creates a new account with balance 0, actual timestamp and given
	 * parameters.
	 * 
	 * @param username
	 *            != NULL
	 * @param email
	 *            != NULL
	 * @param password
	 *            != NULL
	 */
	public UserAccount(String username, String email, String password) {
		this.username = username;
		this.email = email;
		this.password = password;
		this.deleted = false;
		this.balance = new BigDecimal(0.0);
		this.creationDate = new Date();
		this.emailVerified = false;
		this.roles = Role.USER.getCode();
		this.nofKeys = 0;
	}

	public boolean isDeleted() {
		return deleted;
	}

	public void setDeleted(boolean deleted) {
		this.deleted = deleted;
	}

	public long getId() {
		return id;
	}

	public void setId(long id) {
		this.id = id;
	}

	public String getUsername() {
		return username;
	}

	public void setUsername(String username) {
		this.username = username;
	}

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email;
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	public BigDecimal getBalance() {
		return balance;
	}

	public void setBalance(BigDecimal balance) {
		this.balance = balance;
	}

	public Date getCreationDate() {
		return creationDate;
	}

	public void setCreationDate(Date creationDate) {
		this.creationDate = creationDate;
	}
	
	public boolean isEmailVerified() {
		return emailVerified;
	}
	
	public void setEmailVerified(boolean emailVerified) {
		this.emailVerified = emailVerified;
	}
	
	public void setPaymentAddress(String paymentAddress) {
		this.paymentAddress = paymentAddress;
	}

	public String getPaymentAddress() {
		return this.paymentAddress;
	}
	
	public byte getRoles() {
		return roles;
	}
	
	public void setRoles(byte roles) {
		this.roles = roles;
	}
	
	public int getNofKeys() {
		return nofKeys;
	}
	
	public void setNofKeys(int nofKeys) {
		this.nofKeys = nofKeys;
	}
	
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("id: ");
		sb.append(getId());
		sb.append(", username: ");
		sb.append(getUsername());
		sb.append(", email: ");
		sb.append(getEmail());
		sb.append(", isDeleted: ");
		sb.append(isDeleted());
		sb.append(", creationDate: ");
		sb.append(getCreationDate());
		sb.append(", balance: ");
		sb.append(getBalance());
		sb.append(", emailVeryfied: ");
		sb.append(isEmailVerified());
		return sb.toString();
	}

	@Override
	public boolean equals(Object o) {
		if (o == null)
			return false;

		if (o == this)
			return true;

		if (!(o instanceof UserAccount))
			return false;

		UserAccount other = (UserAccount) o;
		return new EqualsBuilder().append(getId(), other.getId())
				.append(getUsername(), other.getUsername())
				.append(getEmail(), other.getEmail())
				.append(isDeleted(), other.isDeleted())
				.append(getCreationDate(), other.getCreationDate())
				.append(getBalance(), other.getBalance())
				.append(getRoles(), other.getRoles()).isEquals();
	}

	@Override
	public int hashCode() {
		return new HashCodeBuilder(31, 59).append(getId())
				.append(getUsername()).append(getUsername()).append(getEmail())
				.append(getCreationDate()).append(isDeleted())
				.append(getBalance())
				.append(getRoles()).toHashCode();
	}

}
