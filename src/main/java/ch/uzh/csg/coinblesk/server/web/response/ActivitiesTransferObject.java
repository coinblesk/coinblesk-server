package ch.uzh.csg.coinblesk.server.web.response;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

import net.minidev.json.JSONArray;
import net.minidev.json.JSONObject;
import net.minidev.json.JSONValue;
import ch.uzh.csg.coinblesk.responseobject.TransferObject;
import ch.uzh.csg.coinblesk.server.domain.Activities;

public class ActivitiesTransferObject extends TransferObject{

	private List<Activities> activitiesList;

	public List<Activities> getActivitiessList() {
		return activitiesList;
	}

	public void setActivitiessList(List<Activities> list) {
		this.activitiesList = list;
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
		
		JSONArray array = toJSONArrayOrNull(o.get("activitiesList"));
		ArrayList<Activities> activities = new ArrayList<Activities>();
		if(array!=null){	
			for(Object o2:array) {
				JSONObject o3 = (JSONObject) o2;
				Activities activity = new Activities();
				activity.decode(o3);
				activities.add(activity);
			}
		}
		setActivitiessList(activities);
	}
	
	@Override
	public void encode(JSONObject jsonObject) throws Exception {
		super.encode(jsonObject);
		if(activitiesList != null) {
			JSONArray array = new JSONArray();
			for(Activities serverPayOutRule: activitiesList) {
				JSONObject o = new JSONObject();
				serverPayOutRule.encode(o);
				array.add(o);
			}
			jsonObject.put("activitiesList", array);
		}
	}
	
}
