package ch.uzh.csg.mbps.server.web.response;

import net.minidev.json.JSONObject;
import ch.uzh.csg.mbps.responseobject.ServerAccountObject;
import ch.uzh.csg.mbps.responseobject.TransferObject;

public class ServerAccountDataTransferObject extends TransferObject{
	
	private GetHistoryServerTransaction getHistoryTransferObject;
	private ServerAccountObject serverAccountObject;
	
	public ServerAccountObject getServerAccountObject() {
		return serverAccountObject;
	}
	public void setServerAccountObject(ServerAccountObject serverAccountObject) {
		this.serverAccountObject = serverAccountObject;
	}
	public GetHistoryServerTransaction getGetHistoryTransferObject() {
		return getHistoryTransferObject;
	}
	public void setGetHistoryTransferObject(GetHistoryServerTransaction getHistoryTransferObject) {
		this.getHistoryTransferObject = getHistoryTransferObject;
	}
	
	@Override
	public void encode(JSONObject jsonObject) throws Exception {
		super.encode(jsonObject);
		if (getHistoryTransferObject != null) {
			JSONObject jsonObject2 = new JSONObject();
			getHistoryTransferObject.encodeThis(jsonObject2);
			jsonObject.put("getHistoryTransferObject", jsonObject2);
		}
		if (serverAccountObject != null) {
			JSONObject jsonObject2 = new JSONObject();
			serverAccountObject.encodeThis(jsonObject2);
			jsonObject.put("serverAccountObject", jsonObject2);
		}
	}

	@Override
	public JSONObject decode(String responseString) throws Exception {
		JSONObject o = super.decode(responseString);

		JSONObject o3 = toJSONObjectOrNull(o.get("getHistoryTransferObject"));
		if (o3 != null) {
			GetHistoryServerTransaction getHistoryTransferObject = new GetHistoryServerTransaction();
			getHistoryTransferObject.decode(o3);
			setGetHistoryTransferObject(getHistoryTransferObject);
		}

		JSONObject o2 = toJSONObjectOrNull(o.get("serverAccountObject"));
		if (o2 != null) {
			ServerAccountObject userAccount = new ServerAccountObject();
			userAccount.decode(o2);
			setServerAccountObject(userAccount);
		}

		return o;
	}
}
