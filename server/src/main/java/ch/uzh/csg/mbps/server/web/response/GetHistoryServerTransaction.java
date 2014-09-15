package ch.uzh.csg.mbps.server.web.response;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

import net.minidev.json.JSONArray;
import net.minidev.json.JSONObject;
import net.minidev.json.JSONValue;
import ch.uzh.csg.mbps.responseobject.TransferObject;
import ch.uzh.csg.mbps.server.web.model.HistoryServerAccountTransaction;

public class GetHistoryServerTransaction extends TransferObject {
	private List<HistoryServerAccountTransaction> transactionHistory;
	
	private Long nofTransactions;

	public GetHistoryServerTransaction() {
	}
	
	public GetHistoryServerTransaction(List<HistoryServerAccountTransaction> transactions, Long nofTransactions) {
		this.transactionHistory = transactions;
		this.nofTransactions = nofTransactions;
	}

	public List<HistoryServerAccountTransaction> getTransactionHistory() {
		return transactionHistory;
	}

	public void setTransactionHistory(List<HistoryServerAccountTransaction> transactionHistory) {
		this.transactionHistory = transactionHistory;
	}
	
	public Long getNofTransactions() {
		return nofTransactions;
	}

	public void setNofTransactions(Long nofTransactions) {
		this.nofTransactions = nofTransactions;
	}
	
	@Override
	public JSONObject decode(String responseString) throws Exception {
		if(responseString == null) {
			return null;
		}
		super.decode(responseString);
		JSONObject o = (JSONObject) JSONValue.parse(responseString);
		decode(o);
		return o;
	}

	public void decode(JSONObject o) throws ParseException {
				
		setNofTransactions(toLongOrNull(o.get("nofTransactions")));

		JSONArray array1 = toJSONArrayOrNull(o.get("transactionHistory"));
		ArrayList<HistoryServerAccountTransaction> transactionHistory = new ArrayList<HistoryServerAccountTransaction>();
		if(array1!=null) {
			for(Object o2:array1) {
				JSONObject o3 = (JSONObject) o2;
				HistoryServerAccountTransaction h1 = new HistoryServerAccountTransaction();
				h1.decode(o3);
				transactionHistory.add(h1);
			}
		}
		setTransactionHistory(transactionHistory);
    }
	
	@Override
	public void encode(JSONObject jsonObject) throws Exception {
		super.encode(jsonObject);
		encodeThis(jsonObject);
	}

	public void encodeThis(JSONObject jsonObject) {
		if(nofTransactions!=null) {
			jsonObject.put("nofTransactions", nofTransactions);
		}
		
		if(transactionHistory != null) {
			JSONArray array = new JSONArray();
			for(HistoryServerAccountTransaction h: transactionHistory) {
				JSONObject o = new JSONObject();
				h.encode(o);
				array.add(o);
			}
			jsonObject.put("transactionHistory", array);
		}
    }
}
