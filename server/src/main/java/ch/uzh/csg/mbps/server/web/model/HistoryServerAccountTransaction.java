package ch.uzh.csg.mbps.server.web.model;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import net.minidev.json.JSONObject;
import ch.uzh.csg.mbps.responseobject.TransferObject;

public class HistoryServerAccountTransaction extends AbstractServerHistory {
	private static final long serialVersionUID = -6411119193220293391L;

	private Boolean received;
	private String serverUrl;
	private String transactionID;
	private Boolean verified;
	
	public HistoryServerAccountTransaction(){
	}
	
	public HistoryServerAccountTransaction(Date timestamp, BigDecimal amount, String serverUrl, 
			Boolean received, Boolean verified, String txId){
		this.timestamp = timestamp;
		this.amount = amount;
		this.serverUrl = serverUrl;
		this.received = received;
		this.verified = verified;
		this.transactionID = txId;
	}
	
	public void setServerUrl(String url){
		this.serverUrl = url;
	}
	
	public String getServerUrl(){
		return this.serverUrl;
	}
	
	public void setReceived(Boolean received){
		this.received = received;
	}
	
	public Boolean getReceived(){
		return this.received;
	}
	
	public void setVerified(Boolean verified){
		this.verified = verified;
	}
	
	public Boolean getVerified(){
		return this.verified;
	}

	public void setTransactionId(String txId){
		this.transactionID = txId;
	}
	
	public String getTransactionId(){
		return this.transactionID;
	}
	
	@Override
	public String toString() {
		DecimalFormat DisplayFormatBTC = new DecimalFormat("#.########");
		SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy' 'HH:mm:ss", Locale.getDefault());
		sdf.setTimeZone(TimeZone.getDefault());
		
		StringBuilder sb = new StringBuilder();
		sb.append(sdf.format(getTimestamp()));
		sb.append("\n");
		sb.append("ServerAccount Transaction from BTC Network: ");
		sb.append(DisplayFormatBTC.format(getAmount()));
		sb.append(" BTC");
		sb.append(", URL: ");
		sb.append(getServerUrl());
		sb.append(", Received: ");
		sb.append(getReceived());
		sb.append(", Verified: ");
		sb.append(getVerified());
		sb.append(", TxID: ");
		sb.append(getTransactionId());
		return sb.toString();
	}

	public void encode(JSONObject o) {
		super.encode(o);
		if(received!=null){
			o.put("received", received);
		}
		if(serverUrl!=null) {
			o.put("serverUrl", serverUrl);
		}
		if(transactionID!=null){
			o.put("transactionID", transactionID);
		}
		if(verified!=null){
			o.put("verified", verified);
		}
    }

	public void decode(JSONObject o) {
		super.decode(o);
		setReceived(TransferObject.toBooleanOrNull(o.get("received")));
		setServerUrl(TransferObject.toStringOrNull(o.get("serverUrl")));
		setTransactionId(TransferObject.toStringOrNull(o.get("transactionID")));
		setVerified(TransferObject.toBooleanOrNull(o.get("verified")));
    }
}
