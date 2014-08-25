package ch.uzh.csg.mbps.server.util.web.model;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * DatabaseAccessObject for {@link ServerPayOutTransaction}s. Handles all DB operations
 * regarding {@link ServerPayOutTransaction}s.
 * 
 */
public class HistoryServerPayOutTransaction extends AbstractServerHistory {
	private static final long serialVersionUID = -6105683540166317472L;

	private String btcAddress;
	private long serverId;

	public HistoryServerPayOutTransaction() {
	}

	public HistoryServerPayOutTransaction(Date timestamp, BigDecimal amount) {
		this.timestamp = timestamp;
		this.amount = amount;
	}
	
	public String getBtcAddress() {
		return btcAddress;
	}
	
	public void setBtcAddress(long serverId) {
		this.serverId = serverId;
	}

	public long getServerId() {
		return serverId;
	}
	
	public void setServerId(String btcAddress) {
		this.btcAddress = btcAddress;
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
}
