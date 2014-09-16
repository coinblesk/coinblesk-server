package ch.uzh.csg.mbps.server.web.model;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

import net.minidev.json.JSONObject;
import ch.uzh.csg.mbps.responseobject.TransferObject;

public abstract class AbstractServerHistory implements Serializable {
	private static final long serialVersionUID = -5481972138777437871L;
	
	protected BigDecimal amount;
	protected Date timestamp;
	
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
	
	public void encode(JSONObject o) {
		if(amount!=null) {
			o.put("amount", amount+"BTC");
		}
		if(timestamp!=null) {
			o.put("timestamp", TransferObject.encodeToString(timestamp));
		}
    }

	public void decode(JSONObject o) {
		setAmount(TransferObject.toBigDecimalOrNull(o.get("amount")));
		setTimestamp(TransferObject.toDateOrNull(o.get("timestamp")));
    }
	
}
