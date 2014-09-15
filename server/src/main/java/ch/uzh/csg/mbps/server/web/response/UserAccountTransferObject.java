package ch.uzh.csg.mbps.server.web.response;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

import net.minidev.json.JSONArray;
import net.minidev.json.JSONObject;
import net.minidev.json.JSONValue;
import ch.uzh.csg.mbps.responseobject.TransferObject;
import ch.uzh.csg.mbps.responseobject.UserAccountObject;

public class UserAccountTransferObject extends TransferObject{
	private List<UserAccountObject> userAccountObjectList;

	public List<UserAccountObject> getUserAccountObjectList() {
		return userAccountObjectList;
	}

	public void setUserAccountObjectList(List<UserAccountObject> list) {
		this.userAccountObjectList = list;
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

	private void decode(JSONObject o) throws ParseException {
		
		JSONArray array = toJSONArrayOrNull(o.get("userAccountObjectList"));
		ArrayList<UserAccountObject> userAccounts = new ArrayList<UserAccountObject>();
		if(array!=null){	
			for(Object o2:array) {
				JSONObject o3 = (JSONObject) o2;
				UserAccountObject user = new UserAccountObject();
				user.decode(o3);
				userAccounts.add(user);
			}
		}
		setUserAccountObjectList(userAccounts);
	}
	
	@Override
	public void encode(JSONObject jsonObject) throws Exception {
		super.encode(jsonObject);
		if(userAccountObjectList != null) {
			JSONArray array = new JSONArray();
			for(UserAccountObject serverPayOutRule: userAccountObjectList) {
				JSONObject o = new JSONObject();
				serverPayOutRule.encode(o);
				array.add(o);
			}
			jsonObject.put("userAccountObjectList", array);
		}
	}
}
