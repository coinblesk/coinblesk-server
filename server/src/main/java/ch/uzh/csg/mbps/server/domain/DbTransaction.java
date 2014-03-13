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

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.hibernate.annotations.Index;

import ch.uzh.csg.mbps.model.Transaction;
import ch.uzh.csg.mbps.server.service.UserAccountService;
import ch.uzh.csg.mbps.server.util.exceptions.UserAccountNotFoundException;

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
	@Column(name="TRANSACTION_NR_BUYER")
	private long transactionNrBuyer;
	@Column(name="TRANSACTION_NR_SELLER")
	private long transactionNrSeller;
	@Column(name="BUYER_ID")
	@Index(name = "BUYER_ID_INDEX")
	private long buyerID;
	@Column(name="SELLER_ID")
	@Index(name = "SELLER_ID_INDEX")
	private long sellerID;
	@Column(name="AMOUNT", precision = 25, scale=8)
	private BigDecimal amount;
	@Column(name="INPUT_CURRENCY")
	private String inputCurrency;
	@Column(name="INPUT_CURRENCY_AMOUNT", precision = 25, scale=2)
	private BigDecimal inputCurrencyAmount;
	
	public DbTransaction() {
	}
	
	public DbTransaction(Transaction transaction) throws UserAccountNotFoundException {
		this.amount = transaction.getAmount();
		this.buyerID = UserAccountService.getInstance().getByUsername(transaction.getBuyerUsername()).getId();
		this.sellerID = UserAccountService.getInstance().getByUsername(transaction.getSellerUsername()).getId();
		this.transactionNrBuyer = transaction.getTransactionNrBuyer();
		this.transactionNrSeller = transaction.getTransactionNrSeller();
		this.inputCurrency = transaction.getInputCurrency();
		this.inputCurrencyAmount = transaction.getAmountInputCurrency();
		this.timestamp = new Date();
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

	public long getTransactionNrBuyer() {
		return transactionNrBuyer;
	}

	public void setTransactionNrBuyer(long transactionNr) {
		this.transactionNrBuyer = transactionNr;
	}
	
	public long getTransactionNrSeller() {
		return transactionNrSeller;
	}

	public void setTransactionNrSeller(long transactionNr) {
		this.transactionNrSeller = transactionNr;
	}
	public long getBuyerId() {
		return buyerID;
	}
	
	public void setBuyerId(long buyerId) {
		this.buyerID = buyerId;
	}

	public long getSellerId() {
		return sellerID;
	}

	public void setSellerId(long sellerId) {
		this.sellerID = sellerId;
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

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("id: ");
		sb.append(getId());
		sb.append(", date:");
		sb.append(getTimestamp());
		sb.append(", transaction number of buyer: ");
		sb.append(getTransactionNrBuyer());
		sb.append(", transaction number of seller: ");
		sb.append(getTransactionNrSeller());
		sb.append(", buyer: ");
		sb.append(getBuyerId());
		sb.append(", seller: ");
		sb.append(getSellerId());
		sb.append(", amount: ");
		sb.append(getAmount());
		if (inputCurrency != null && !inputCurrency.isEmpty() && inputCurrencyAmount != null && inputCurrencyAmount.compareTo(BigDecimal.ZERO) >  0) {
			sb.append(" BTC, ");
			sb.append(getInputCurrencyAmount());
			sb.append(" ");
			sb.append(getInputCurrency());
		} else {
			sb.append(" BTC");
		}
		return sb.toString();
	}
	
	@Override
	public boolean equals(Object o) {
		if (o == null)
			return false;

		if (o == this)
			return true;

		if (!(o instanceof Transaction))
			return false;

		DbTransaction other = (DbTransaction) o;
		return new EqualsBuilder().append(getId(), other.getId())
				.append(getTimestamp(), other.getTimestamp())
				.append(getTransactionNrBuyer(), other.getTransactionNrBuyer())
				.append(getTransactionNrSeller(), other.getTransactionNrSeller())
				.append(getBuyerId(), other.getBuyerId())
				.append(getSellerId(), other.getSellerId())
				.append(getAmount(), other.getAmount())
				.append(getInputCurrency(), other.getInputCurrency())
				.append(getInputCurrencyAmount(), other.getInputCurrencyAmount())
				.isEquals();
	}

	@Override
	public int hashCode() {
		return new HashCodeBuilder(47, 83).append(getId())
				.append(getTimestamp())
				.append(getTransactionNrBuyer())
				.append(getTransactionNrSeller())
				.append(getBuyerId())
				.append(getSellerId())
				.append(getAmount())
				.append(getInputCurrency())
				.append(getInputCurrencyAmount())
				.toHashCode();
	}
	
}
