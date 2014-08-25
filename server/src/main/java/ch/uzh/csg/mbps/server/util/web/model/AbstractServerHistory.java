package ch.uzh.csg.mbps.server.util.web.model;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

public abstract class AbstractServerHistory implements Serializable {
	private static final long serialVersionUID = -5481972138777437871L;
	protected Date timestamp;
	protected BigDecimal amount;
	
	public Date getTimestamp() {
		return timestamp;
	}

	public void setTimestamp(Date date) {
		this.timestamp = date;
	}

	public void setAmount(BigDecimal amount) {
		this.amount = amount;
	}

	public BigDecimal getAmount() {
		return amount;
	}
	
	public abstract String toString();
	
}