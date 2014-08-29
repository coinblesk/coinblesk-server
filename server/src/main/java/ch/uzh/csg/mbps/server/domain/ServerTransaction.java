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

import org.apache.commons.lang3.builder.HashCodeBuilder;

import com.azazar.bitcoin.jsonrpcclient.Bitcoin.Transaction;

@Entity(name = "SERVER_TRANSACTION")
@Table(indexes = {
		@Index(name = "BTC_ADDRESS_INDEX", columnList = "BTC_ADDRESS"),
		@Index(name = "TX_ID_INDEX_SERVER_TX", columnList = "TX_ID"),
		@Index(name = "SERVER_URL_INDEX", columnList = "SERVER_URL") })
public class ServerTransaction {

	@Id
	@SequenceGenerator(name = "pk_sequence", sequenceName = "server_transaction_id_seq", allocationSize = 1)
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "pk_sequence")
	@Column(name = "ID")
	private long id;
	@Column(name = "TIMESTAMP")
	@Temporal(TemporalType.TIMESTAMP)
	private Date timestamp;
	@Column(name = "AMOUNT", precision = 25, scale = 8)
	private BigDecimal amount;
	@Column(name = "SERVER_URL")
	private String serverUrl;
	@Column(name = "BTC_ADDRESS")
	private String btcAddress;
	@Column(name = "TX_ID")
	private String transactionID;
	@Column(name = "VERIFIED")
	private boolean verified;
	@Column(name = "RECEIVED")
	private boolean received;

	public ServerTransaction() {
	}

	/**
	 * Creates a new server transaction.
	 * 
	 * @param tx
	 *            != NULL
	 * @param received
	 *            != NULL
	 */
	public ServerTransaction(Transaction tx, String serverUrl, boolean received) {
		this.timestamp = tx.time();
		this.amount = new BigDecimal(tx.amount());
		this.serverUrl = serverUrl;
		this.btcAddress = tx.address();
		this.verified = false;
		this.transactionID = tx.txId();
		this.received = received;
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

	public BigDecimal getAmount() {
		return amount;
	}

	public void setAmount(BigDecimal amount) {
		this.amount = amount;
	}

	public String getServerUrl() {
		return serverUrl;
	}

	public void setServerUrl(String url) {
		this.serverUrl = url;
	}

	public String getBTCAddress() {
		return btcAddress;
	}

	public void setBTCAddress(String address) {
		this.btcAddress = address;
	}

	public String getTransactionID() {
		return transactionID;
	}

	public void setTransactionID(String transactionID) {
		this.transactionID = transactionID;
	}

	public boolean isVerified() {
		return verified;
	}

	public void setVerified(boolean verified) {
		this.verified = verified;
	}

	public boolean isReceived() {
		return received;
	}

	public void setReceived(boolean received) {
		this.received = received;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("id: ");
		sb.append(getId());
		sb.append(" amount: ");
		sb.append(getServerUrl());
		sb.append(" url: ");
		sb.append(getAmount());
		sb.append(" timestamp: ");
		sb.append(getTimestamp());
		sb.append(" verified: ");
		sb.append(isVerified());
		sb.append(" address: ");
		sb.append(getBTCAddress());
		sb.append(" received: ");
		sb.append(isReceived());
		return sb.toString();
	}

	@Override
	public int hashCode() {
		return new HashCodeBuilder(47, 83).append(getId())
				.append(getTimestamp()).append(getServerUrl())
				.append(getBTCAddress()).append(isVerified())
				.append(getAmount()).append(isReceived())
				.append(getTransactionID()).toHashCode();
	}
}