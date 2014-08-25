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

@Entity(name = "SERVER_PAY_OUT_TRANSACTION")
public class ServerPayOutTransaction implements Serializable{
	private static final long serialVersionUID = -8174904243568625313L;
	
	@Id
	@SequenceGenerator(name="pk_sequence",sequenceName="server_pay_out_transaction_id_seq", allocationSize=1)
	@GeneratedValue(strategy=GenerationType.SEQUENCE,generator="pk_sequence")
	@Column(name="ID")
	private long id;
	@Column(name="SERVER_ACCOUNT_ID")
	@Index(name = "SERVER_ACCOUNT_ID_INDEX_PAY_OUT_TX")
	private long serverAccountID;
	@Column(name="TIMESTAMP")
	private Date timestamp;
	@Column(name="AMOUNT", precision = 25, scale=8)
	private BigDecimal amount;
	@Column(name="PAYOUT_ADDRESS")
	private String payoutAddress;
	@Column(name="VERIFIED")
	private boolean verified;
	@Column(name="SERVER_TRANSACTION_ID")
	@Index(name = "SERVER_TRANSACTION_ID_INDEX_PAY_OUT_TX")
	private String serverTransactionID;
	
	public ServerPayOutTransaction() {
	}
	
	public ServerPayOutTransaction(Transaction tx) {
		setTimestamp(tx.time());
		setAmount(new BigDecimal(tx.amount()));
		setPayoutAddress(tx.address());
		setVerified(false);
		setTransactionID(tx.txId());
	}
	
	public long getId() {
		return id;
	}

	public void setId(long id) {
		this.id = id;
	}

	public String getPayoutAddress() {
		return this.payoutAddress;
	}

	public void setPayoutAddress(String address) {
		this.payoutAddress = address;
	}

	public long getServerAccountID() {
		return serverAccountID;
	}

	public void setServerAccountID(long serverAccountID) {
		this.serverAccountID = serverAccountID;
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
		return serverTransactionID;
	}

	public void setTransactionID(String transactionID) {
		this.serverTransactionID = transactionID;
	}
	
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("id: ");
		sb.append(getId());
		sb.append(" userId: ");
		sb.append(getServerAccountID());
		sb.append(" timestamp: ");
		sb.append(getTimestamp());
		sb.append(" amount: ");
		sb.append(getAmount());
		sb.append(" address: ");
		sb.append(getPayoutAddress());
		sb.append(" verified: ");
		sb.append(isVerified());
		return sb.toString();
	}
}
