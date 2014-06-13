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

import org.hibernate.annotations.Index;

import com.azazar.bitcoin.jsonrpcclient.Bitcoin.Transaction;

@Entity(name = "PAY_OUT_TRANSACTION")
public class PayOutTransaction implements Serializable {
	private static final long serialVersionUID = -3754792381238747631L;
	
	@Id
	@SequenceGenerator(name="pk_sequence",sequenceName="pay_out_transaction_id_seq", allocationSize=1)
	@GeneratedValue(strategy=GenerationType.SEQUENCE,generator="pk_sequence")
	@Column(name="ID")
	private long id;
	@Column(name="USER_ID")
	@Index(name = "USER_ID_INDEX_PAY_OUT_TX")
	private long userID;
	@Column(name="TIMESTAMP")
	private Date timestamp;
	@Column(name="AMOUNT", precision = 25, scale=8)
	private BigDecimal amount;
	@Column(name="BTC_ADDRESS")
	private String btcAddress;
	@Column(name="VERIFIED")
	private boolean verified;
	@Column(name="TRANSACTION_ID")
	@Index(name = "TRANSACTION_ID_INDEX_PAY_OUT_TX")
	private String transactionID;
	
	public PayOutTransaction() {
	}
	
	public PayOutTransaction(Transaction tx) {
		setTimestamp(tx.time());
		setAmount(new BigDecimal(tx.amount()));
		setBtcAddress(tx.address());
		setVerified(false);
		setTransactionID(tx.txId());
	}
	
	public long getId() {
		return id;
	}

	public void setId(long id) {
		this.id = id;
	}

	public String getBtcAddress() {
		return this.btcAddress;
	}

	public void setBtcAddress(String btcAddress) {
		this.btcAddress = btcAddress;
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

	public boolean isVerified() {
		return verified;
	}

	public void setVerified(boolean verified) {
		this.verified = verified;
	}

	public String getTransactionID() {
		return transactionID;
	}

	public void setTransactionID(String transactionID) {
		this.transactionID = transactionID;
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
		sb.append(" address: ");
		sb.append(getBtcAddress());
		sb.append(" verified: ");
		sb.append(isVerified());
		return sb.toString();
	}


}
