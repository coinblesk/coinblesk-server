package ch.uzh.csg.mbps.server.web.model;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import net.minidev.json.JSONObject;
import ch.uzh.csg.mbps.responseobject.TransferObject;

/**
 * DatabaseAccessObject for {@link ServerPayOutTransaction}s. Handles all DB operations
 * regarding {@link ServerPayOutTransaction}s.
 * 
 */
public class HistoryServerPayOutTransaction extends AbstractServerHistory {
	private static final long serialVersionUID = -6105683540166317472L;

	private String btcAddress;
	private Long serverId;
	private Boolean verified;

	public HistoryServerPayOutTransaction() {
	}

	public HistoryServerPayOutTransaction(Date timestamp, BigDecimal amount) {
		this.timestamp = timestamp;
		this.amount = amount;
	}
	
	public HistoryServerPayOutTransaction(Date timestamp, BigDecimal amount, String btcAddress, Long serverId, Boolean verified) {
		this.timestamp = timestamp;
		this.amount = amount;
		this.btcAddress = btcAddress;
		this.serverId = serverId;
		this.verified = verified;
	}
	
	public String getBtcAddress() {
		return btcAddress;
	}
	
	public void setBtcAddress(String btcAddress) {
		this.btcAddress = btcAddress;
	}

	public void setServerId(Long serverId) {
		this.serverId = serverId;
	}

	public Long getServerId() {
		return serverId;
	}
	
	public Boolean isVerified() {
		return verified;
	}
	
	public void setVerified(Boolean verified) {
		this.verified = verified;
	}
	
	@Override
	public String toString() {
		DecimalFormat DisplayFormatBTC = new DecimalFormat("#.########");
		SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy' 'HH:mm:ss", Locale.getDefault());
		sdf.setTimeZone(TimeZone.getDefault());
		
		StringBuilder sb = new StringBuilder();
		sb.append(sdf.format(getTimestamp()));
		sb.append("\n");
		sb.append("Server PayOut Transaction: ");
		sb.append(DisplayFormatBTC.format(getAmount()));
		sb.append(" BTC to address: ");
		sb.append(getBtcAddress());
		sb.append(" Server ID: ");
		sb.append(getServerId());
		return sb.toString();
	}
	
	public void encode(JSONObject o) {
		super.encode(o);
		if(btcAddress!=null) {
			o.put("btcAddress", btcAddress);
		}
		if(serverId!=null){
			o.put("serverId", serverId);
		}
		if(verified!=null){
			o.put("verified", verified);
		}
    }

	public void decode(JSONObject o) {
		super.decode(o);
		setBtcAddress(TransferObject.toStringOrNull(o.get("btcAddress")));
		setServerId(TransferObject.toLongOrNull(o.get("serverId")));
		setVerified(TransferObject.toBooleanOrNull(o.get("verified")));
    }
}
