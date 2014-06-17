package ch.uzh.csg.mbps.server.domain;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.SequenceGenerator;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.hibernate.annotations.Index;

import ch.uzh.csg.mbps.customserialization.PaymentRequest;
import ch.uzh.csg.mbps.server.util.exceptions.UserAccountNotFoundException;
import ch.uzh.csg.mbps.util.Converter;

@Entity(name = "DB_TRANSACTION")
public class DbTransaction implements Serializable {
	private static final long serialVersionUID = 6937127333699090182L;
	
	@Id
	@SequenceGenerator(name="pk_sequence",sequenceName="db_transaction_id_seq", allocationSize=1)
	@GeneratedValue(strategy=GenerationType.SEQUENCE,generator="pk_sequence")
	@Column(name="ID")
	private long id;
	@Column(name="TIMESTAMP")
	@Temporal(TemporalType.TIMESTAMP)
	private Date timestamp;
	@Column(name="USERNAME_PAYER")
	@Index(name = "USERNAME_PAYER_INDEX")
	private String usernamePayer;
	@Column(name="USERNAME_PAYEE")
	@Index(name = "USERNAME_PAYEE_INDEX")
	private String usernamePayee;
	@Column(name="CURRENCY")
	private String currency;
	@Column(name="AMOUNT", precision = 25, scale=8)
	private BigDecimal amount;
	@Column(name="INPUT_CURRENCY")
	private String inputCurrency;
	@Column(name="INPUT_CURRENCY_AMOUNT", precision = 25, scale=2)
	private BigDecimal inputCurrencyAmount;
	@Column(name="TIMESTAMP_PAYER")
	@Index(name="TIMESTAMP_PAYER_INDEX")
	private long timestampPayer;
	@Column(name="SIGNATURE")
	private String signature;
	
	public DbTransaction() {
	}
	
	public DbTransaction(PaymentRequest paymentRequest) throws UserAccountNotFoundException {
		this.usernamePayer = paymentRequest.getUsernamePayer();
		this.usernamePayee = paymentRequest.getUsernamePayee();
		this.currency = paymentRequest.getCurrency().getCurrencyCode();
		this.amount = Converter.getBigDecimalFromLong(paymentRequest.getAmount());
		this.timestampPayer = paymentRequest.getTimestamp();
		this.signature = new String(Base64.encodeBase64(paymentRequest.getSignature()));
		this.timestamp = new Date();
		if (paymentRequest.getInputCurrency() != null) {
			this.inputCurrency = paymentRequest.getInputCurrency().getCurrencyCode();
			this.inputCurrencyAmount = Converter.getBigDecimalFromLong(paymentRequest.getInputAmount());
		}
	}
	
	public long getId() {
		return id;
	}

	public void setId(long id) {
		this.id = id;
	}

	public Date getTimestamp() {
		return timestamp;
	}

	public void setTimestamp(Date timestamp) {
		this.timestamp = timestamp;
	}
	
	public String getUsernamePayer() {
		return usernamePayer;
	}

	public void setUsernamePayer(String usernamePayer) {
		this.usernamePayer = usernamePayer;
	}

	public String getUsernamePayee() {
		return usernamePayee;
	}

	public void setUsernamePayee(String usernamePayee) {
		this.usernamePayee = usernamePayee;
	}
	
	public String getCurrency() {
		return currency;
	}
	
	public void setCurrency(String currency) {
		this.currency = currency;
	}

	public BigDecimal getAmount() {
		return amount;
	}

	public void setAmount(BigDecimal amount) {
		this.amount = amount;
	}
	
	public String getInputCurrency() {
		return inputCurrency;
	}

	public void setInputCurrency(String inputCurrency) {
		this.inputCurrency = inputCurrency;
	}

	public BigDecimal getInputCurrencyAmount() {
		return inputCurrencyAmount;
	}

	public void setInputCurrencyAmount(BigDecimal inputCurrencyAmount) {
		this.inputCurrencyAmount = inputCurrencyAmount;
	}

	public long getTimestampPayer() {
		return timestampPayer;
	}

	public void setTimestampPayer(long timestamp) {
		this.timestampPayer = timestamp;
	}
	
	public String getSignature() {
		return signature;
	}

	public void setSignature(String signature) {
		this.signature = signature;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("id: ");
		sb.append(getId());
		sb.append(", date:");
		sb.append(getTimestamp());
		sb.append(", payer: ");
		sb.append(getUsernamePayer());
		sb.append(", payee: ");
		sb.append(getUsernamePayee());
		sb.append(", currency: ");
		sb.append(getCurrency());
		sb.append(", amount: ");
		sb.append(getAmount());
		sb.append(" BTC, ");
		if (inputCurrency != null && !inputCurrency.isEmpty() && inputCurrencyAmount != null && inputCurrencyAmount.compareTo(BigDecimal.ZERO) >  0) {
			sb.append(getInputCurrencyAmount());
			sb.append(" ");
			sb.append(getInputCurrency());
		}
		sb.append(", timestamp payer: ");
		sb.append(getTimestampPayer());
		return sb.toString();
	}
	
	@Override
	public boolean equals(Object o) {
		if (o == null)
			return false;

		if (o == this)
			return true;

		if (!(o instanceof PaymentRequest))
			return false;

		DbTransaction other = (DbTransaction) o;
		return new EqualsBuilder().append(getId(), other.getId())
				.append(getTimestamp(), other.getTimestamp())
				.append(getUsernamePayer(), other.getUsernamePayer())
				.append(getUsernamePayee(), other.getUsernamePayee())
				.append(getAmount(), other.getAmount())
				.append(getCurrency(), other.getCurrency())
				.append(getTimestampPayer(), other.getTimestampPayer())
				.isEquals();
	}

	@Override
	public int hashCode() {
		return new HashCodeBuilder(47, 83).append(getId())
				.append(getTimestamp())
				.append(getUsernamePayer())
				.append(getUsernamePayee())
				.append(getAmount())
				.append(getCurrency())
				.append(getTimestampPayer())
				.toHashCode();
	}
	
}
