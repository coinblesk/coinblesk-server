package ch.uzh.csg.mbps.server.domain;

import java.io.Serializable;
import java.math.BigDecimal;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.SequenceGenerator;

import org.hibernate.annotations.Index;

@Entity(name = "PAYOUT_RULES")
public class PayOutRule implements Serializable {
	private static final long serialVersionUID = -6789290299273381688L;

	@Id
	@SequenceGenerator(name = "pk_sequence", sequenceName = "payout_rules_id_seq", allocationSize = 1)
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "pk_sequence")
	@Column(name = "ID")
	private long id;
	@Column(name = "HOUR")
	private int hour;
	@Column(name = "DAY")
	private int day;
	@Column(name = "USER_ID")
	@Index(name = "USER_ID_INDEX_PAYOUTRULES")
	private long userId;
	@Column(name = "BALANCE_LIMIT", precision = 25, scale = 8)
	private BigDecimal balanceLimit;
	@Column(name = "PAYOUT_ADDRESS")
	private String payoutAddress;

	public PayOutRule() {
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

	public long getUserId() {
		return userId;
	}

	public void setUserId(long userId) {
		this.userId = userId;
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
		sb.append(" userID: ");
		sb.append(getUserId());
		sb.append(" payoutAddress: ");
		sb.append(getPayoutAddress());
		sb.append(" balanceLimit: ");
		sb.append(getBalanceLimit());
		return sb.toString();
	}
}
