package ch.uzh.csg.mbps.server.web.model;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

import net.minidev.json.JSONObject;
import ch.uzh.csg.mbps.responseobject.TransferObject;

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
	
	public void encode(JSONObject o) {
		if(timestamp!=null) {
			o.put("timestamp", TransferObject.encodeToString(timestamp));
		}
		if(amount!=null) {
			o.put("amount", amount+"BTC");
		}
    }

	public void decode(JSONObject o) {
		setTimestamp(TransferObject.toDateOrNull(o.get("timestamp")));
		setAmount(TransferObject.toBigDecimalOrNull(o.get("amount")));
    }
	
}
