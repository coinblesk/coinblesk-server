package ch.uzh.csg.coinblesk.server.web.response;

import java.util.ArrayList;
import java.util.List;

import net.minidev.json.JSONArray;
import net.minidev.json.JSONObject;
import ch.uzh.csg.coinblesk.responseobject.TransferObject;
import ch.uzh.csg.coinblesk.server.domain.ServerPayOutRule;

public class ServerPayOutRulesTransferObject extends TransferObject {
	
	private List<ServerPayOutRule> serverPayOutRulesList;

	public List<ServerPayOutRule> getServerPayOutRulesList() {
		return serverPayOutRulesList;
	}

	public void setServerPayOutRulesList(List<ServerPayOutRule> list) {
		this.serverPayOutRulesList = list;
	}
	
	@Override
	public void encode(JSONObject jsonObject) throws Exception {
		super.encode(jsonObject);
		if(serverPayOutRulesList != null) {
			JSONArray array = new JSONArray();
			for(ServerPayOutRule serverPayOutRule: serverPayOutRulesList) {
				JSONObject o = new JSONObject();
				serverPayOutRule.encode(o);
				array.add(o);
			}
			jsonObject.put("serverPayOutRulesList", array);
		}
	}
		
	@Override
	public JSONObject decode(String responseString) throws Exception {
		JSONObject o = super.decode(responseString);
		JSONArray a = toJSONArrayOrNull(o.get("serverPayOutRulesList"));
		if(a!=null) {
			List<ServerPayOutRule> serverPayOutRulesList = new ArrayList<ServerPayOutRule>();
			for(Object o2:a) {
				if(o2 instanceof JSONObject) {
					ServerPayOutRule serverPayOutRule = new ServerPayOutRule();
					serverPayOutRule.decode((JSONObject) o2);
					serverPayOutRulesList.add(serverPayOutRule);
				}
			}
			setServerPayOutRulesList(serverPayOutRulesList);
		}
		return o;
	}
}