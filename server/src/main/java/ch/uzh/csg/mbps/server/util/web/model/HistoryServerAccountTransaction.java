package ch.uzh.csg.mbps.server.util.web.model;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class HistoryServerAccountTransaction extends AbstractServerHistory {
	private static final long serialVersionUID = -6411119193220293391L;

	private String serverUrl;
	private boolean received;
	
	public HistoryServerAccountTransaction(){
	}
	
	public HistoryServerAccountTransaction(Date timestamp, BigDecimal amount, String serverUrl, boolean received){
		this.timestamp = timestamp;
		this.amount = amount;
		this.serverUrl = serverUrl;
		this.received = received;
	}
	
	public void setServerUrl(String url){
		this.serverUrl = url;
	}
	
	public String getServerUrl(){
		return this.serverUrl;
	}
	
	public void setReceived(boolean received){
		this.received = received;
	}
	
	public boolean getReceived(){
		return this.received;
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
		return sb.toString();
	}

}
