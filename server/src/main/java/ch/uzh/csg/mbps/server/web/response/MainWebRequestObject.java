package ch.uzh.csg.mbps.server.web.response;

import net.minidev.json.JSONObject;
import ch.uzh.csg.mbps.responseobject.TransactionObject;
import ch.uzh.csg.mbps.server.web.model.UserModelObject;

public class MainWebRequestObject extends TransactionObject {
	
	private GetHistoryServerTransaction getHistoryTransferObject;
	private MessagesTransferObject getMessageTransferObject;
	private UserModelObject userModelObject;

	public UserModelObject getUserModelObject() {
		return userModelObject;
	}
	public void setUserModelObject(UserModelObject userModelObject) {
		this.userModelObject = userModelObject;
	}
	public GetHistoryServerTransaction getGetHistoryTransferObject() {
		return getHistoryTransferObject;
	}
	public void setGetHistoryTransferObject(GetHistoryServerTransaction getHistoryTransferObject) {
		this.getHistoryTransferObject = getHistoryTransferObject;
	}
	public MessagesTransferObject getGetMessageTransferObject() {
		return getMessageTransferObject;
	}
	public void setGetMessageTransferObject(MessagesTransferObject getMessageTransferObject) {
		this.getMessageTransferObject = getMessageTransferObject;
	}
	
	@Override
	public void encode(JSONObject jsonObject) throws Exception {
		super.encode(jsonObject);
		if (getHistoryTransferObject != null) {
			JSONObject jsonObject2 = new JSONObject();
			getHistoryTransferObject.encodeThis(jsonObject2);
			jsonObject.put("getHistoryTransferObject", jsonObject2);
		}
		if (getMessageTransferObject != null) {
			JSONObject jsonObject2 = new JSONObject();
			getMessageTransferObject.encodeThis(jsonObject2);
			jsonObject.put("getMessageTransferObject", jsonObject2);
		}
		if (userModelObject != null) {
			JSONObject jsonObject2 = new JSONObject();
			userModelObject.encodeThis(jsonObject2);
			jsonObject.put("userModelObject", jsonObject2);
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

		JSONObject o4 = toJSONObjectOrNull(o.get("getMessageTransferObject"));
		if (o3 != null) {
			MessagesTransferObject getMessageTransferObject = new MessagesTransferObject();
			getMessageTransferObject.decode(o4);
			setGetMessageTransferObject(getMessageTransferObject);
		}

		JSONObject o2 = toJSONObjectOrNull(o.get("userModelObject"));
		if (o2 != null) {
			UserModelObject userAccount = new UserModelObject();
			userAccount.decode(o2);
			setUserModelObject(userAccount);
		}

		return o;
	}
}
