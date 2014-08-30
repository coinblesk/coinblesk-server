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

import ch.uzh.csg.mbps.server.util.exceptions.UserAccountNotFoundException;

import com.azazar.bitcoin.jsonrpcclient.Bitcoin.Transaction;

@Entity
@Table(name = "PAY_IN_TRANSACTION_UNVERIFIED", indexes = {
		@Index(name = "USER_ID_INDEX_PAY_IN_TX_UN",  columnList="USER_ID"),
		@Index(name = "TX_ID_INDEX_PAY_IN_TX_UN",  columnList="TX_ID")})
public class PayInTransactionUnverified implements Serializable {
	private static final long serialVersionUID = -5777010150563320837L;
	
	@Id
	@SequenceGenerator(name="pk_sequence",sequenceName="pay_in_transaction_unverified_id_seq", allocationSize=1)
	@GeneratedValue(strategy=GenerationType.SEQUENCE,generator="pk_sequence")
	@Column(name="ID")
	private long id;
	@Column(name="USER_ID")
	private long userID;
	@Column(name="TIMESTAMP")
	private Date timestamp;
	@Column(name="AMOUNT", precision = 25, scale=8)
	private BigDecimal amount;
	@Column(name="TX_ID")
	private String transactionID;
	@Column(name="BTC_ADDRESS")
	private String btcAddress;
	
	public PayInTransactionUnverified() {
	}
	
	public PayInTransactionUnverified(long userID, Transaction transaction) throws UserAccountNotFoundException {
		this.userID = userID;
		this.timestamp = transaction.timeReceived();
		this.amount = BigDecimal.valueOf(transaction.amount());
		this.transactionID = transaction.txId();
		this.btcAddress = transaction.address();
	}

	public long getId() {
		return id;
	}

	public void setId(long id) {
		this.id = id;
	}

	public long getUserID() {
		return userID;
	}

	public void setUserID(long userID) {
		this.userID = userID;
	}

	public Date getTimestamp() {
		return timestamp;
	}

	public void setTimestamp(Date timestamp) {
		this.timestamp = timestamp;
	}

	public BigDecimal getAmount() {
		return amount;
	}

	public void setAmount(BigDecimal amount) {
		this.amount = amount;
	}

	public String getTransactionID() {
		return transactionID;
	}

	public void setTransactionID(String transactionID) {
		this.transactionID = transactionID;
	}
	
	public String getBtcAddress() {
		return btcAddress;
	}

	public void setBtcAddress(String btcAddress) {
		this.btcAddress = btcAddress;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("id: ");
		sb.append(getId());
		sb.append(" userId: ");
		sb.append(getUserID());
		sb.append(" timestamp: ");
		sb.append(getTimestamp());
		sb.append(" amount: ");
		sb.append(getAmount());
		sb.append(" transactionID: ");
		sb.append(getTransactionID());
		sb.append(" BtcAddress: ");
		sb.append(getBtcAddress());
		return sb.toString();
	}
}
