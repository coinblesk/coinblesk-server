package ch.uzh.csg.mbps.server.domain;

import java.math.BigDecimal;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;

@Entity
@Table(name = "SERVER_PAYOUT_RULES", indexes = {@Index(name = "SERVER_ACCOUNT_ID_INDEX_PAYOUTRULES",  columnList="SERVER_ACCOUNT_ID")})
public class ServerPayOutRule {

	@Id
	@SequenceGenerator(name = "pk_sequence", sequenceName = "server_payout_rules_id_seq", allocationSize = 1)
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "pk_sequence")
	@Column(name = "ID", nullable = false)
	private long id;
	@Column(name = "HOUR")
	private int hour;
	@Column(name = "DAY")
	private int day;
	@Column(name = "SERVER_ACCOUNT_ID", nullable = false)
	private long serverAccountId;
	@Column(name = "BALANCE_LIMIT", precision = 25, scale = 8)
	private BigDecimal balanceLimit;
	@Column(name = "PAYOUT_ADDRESS")
	private String payoutAddress;

	public ServerPayOutRule() {
	}

	public ServerPayOutRule(long accountId, BigDecimal balance, String address){
		this.serverAccountId = accountId;
		this.balanceLimit = balance;
		this.payoutAddress = address;
	}
	
	public ServerPayOutRule(long accountId, int hour, int day, String address){
		this.serverAccountId = accountId;
		this.payoutAddress = address;
		this.day = day;
		this.hour = hour;
	}
	
	public long getId() {
		return id;
	}

	public void setId(long id) {
		this.id = id;
	}

	public int getHour() {
		return hour;
	}

	public void setHour(int hour) {
		this.hour = hour;
	}

	public int getDay() {
		return day;
	}

	public void setDay(int day) {
		this.day = day;
	}

	public long getServerAccountId() {
		return serverAccountId;
	}

	public void setServerAccountId(long serverAccountId) {
		this.serverAccountId = serverAccountId;
	}

	public BigDecimal getBalanceLimit() {
		return balanceLimit;
	}

	public void setBalanceLimit(BigDecimal balanceLimit) {
		this.balanceLimit = balanceLimit;
	}

	public String getPayoutAddress() {
		return payoutAddress;
	}

	public void setPayoutAddress(String payoutAddress) {
		this.payoutAddress = payoutAddress;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("id: ");
		sb.append(getId());
		sb.append(" hour: ");
		sb.append(getHour());
		sb.append(" day: ");
		sb.append(getDay());
		sb.append(" serverAccountID: ");
		sb.append(getServerAccountId());
		sb.append(" payoutAddress: ");
		sb.append(getPayoutAddress());
		sb.append(" balanceLimit: ");
		sb.append(getBalanceLimit());
		return sb.toString();
	}
}
