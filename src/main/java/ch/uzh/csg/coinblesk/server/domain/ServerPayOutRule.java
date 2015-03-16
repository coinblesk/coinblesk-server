package ch.uzh.csg.coinblesk.server.domain;

import java.math.BigDecimal;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;

import net.minidev.json.JSONObject;
import ch.uzh.csg.coinblesk.responseobject.TransferObject;

@Entity
@Table(name = "SERVER_PAYOUT_RULES", indexes = {@Index(name = "SERVER_ACCOUNT_ID_INDEX_PAYOUTRULES",  columnList="SERVER_ACCOUNT_ID")})
public class ServerPayOutRule {

	@Id
	@SequenceGenerator(name = "pk_sequence", sequenceName = "server_payout_rules_id_seq", allocationSize = 1)
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "pk_sequence")
	@Column(name = "ID", nullable = false)
	private Long id;
	@Column(name = "HOUR")
	private Integer hour;
	@Column(name = "DAY")
	private Integer day;
	@Column(name = "SERVER_ACCOUNT_ID", nullable = false)
	private Long serverAccountId;
	@Column(name = "BALANCE_LIMIT", precision = 25, scale = 8)
	private BigDecimal balanceLimit;
	@Column(name = "PAYOUT_ADDRESS")
	private String payoutAddress;

	public ServerPayOutRule() {
	}

	public ServerPayOutRule(Long accountId, BigDecimal balance, String address){
		this.serverAccountId = accountId;
		this.balanceLimit = balance;
		this.payoutAddress = address;
	}
	
	public ServerPayOutRule(Long accountId, Integer hour, Integer day, String address){
		this.serverAccountId = accountId;
		this.payoutAddress = address;
		this.day = day;
		this.hour = hour;
	}
	
	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public Integer getHour() {
		return hour;
	}

	public void setHour(Integer hour) {
		this.hour = hour;
	}

	public Integer getDay() {
		return day;
	}

	public void setDay(Integer day) {
		this.day = day;
	}

	public Long getServerAccountId() {
		return serverAccountId;
	}

	public void setServerAccountId(Long serverAccountId) {
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
	
	public void encode(JSONObject o) {
		if(hour!=null) {
			o.put("hour", hour);
		}
		if(day!=null) {
			o.put("day", day);
		}
		if(balanceLimit!=null) {
			o.put("balanceLimit", balanceLimit + "BTC");
		}
		if(id!=null){
			o.put("id", id);
		}
		if(serverAccountId!=null){
			o.put("serverAccountId", serverAccountId);
		}
	    o.put("payoutAddress", payoutAddress);
	    //userId never sent like this over the network
    }

	public void decode(JSONObject o) {
		setHour(TransferObject.toIntOrNull(o.get("hour")));
		setDay(TransferObject.toIntOrNull(o.get("day")));
		setId(TransferObject.toLongOrNull((o).get("id")));
		setServerAccountId(TransferObject.toLongOrNull(o.get("serverAccountId")));
		setPayoutAddress(TransferObject.toStringOrNull(o.get("payoutAddress")));
    }
}
