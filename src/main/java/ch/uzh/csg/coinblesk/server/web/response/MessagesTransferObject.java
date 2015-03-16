package ch.uzh.csg.coinblesk.server.web.response;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

import net.minidev.json.JSONArray;
import net.minidev.json.JSONObject;
import net.minidev.json.JSONValue;
import ch.uzh.csg.coinblesk.responseobject.TransferObject;
import ch.uzh.csg.coinblesk.server.web.model.MessagesObject;

public class MessagesTransferObject extends TransferObject {

	private List<MessagesObject> messagesList;
	private Long nofMessages;
	
	public MessagesTransferObject(){
		
	}
	
	public MessagesTransferObject(List<MessagesObject> messages, Long nofMessages){
		this.messagesList = messages;
		this.nofMessages = nofMessages;
	}

	public List<MessagesObject> getMessagesList() {
		return messagesList;
	}

	public void setMessagesList(List<MessagesObject> list) {
		this.messagesList = list;
	}
	
	public Long getNofMessages() {
		return nofMessages;
	}

	public void setNofMessages(Long nofMessages) {
		this.nofMessages = nofMessages;
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
		
		setNofMessages(toLongOrNull(o.get("nofMessages")));
		
		JSONArray array = toJSONArrayOrNull(o.get("messagesList"));
		ArrayList<MessagesObject> messagesList = new ArrayList<MessagesObject>();
		if(array!=null){	
			for(Object o2:array) {
				JSONObject o3 = (JSONObject) o2;
				MessagesObject m = new MessagesObject();
				m.decode(o3);
				messagesList.add(m);
			}
		}
		setMessagesList(messagesList);
	}
	
	@Override
	public void encode(JSONObject jsonObject) throws Exception {
		super.encode(jsonObject);
		encodeThis(jsonObject);
	}

	public void encodeThis(JSONObject jsonObject) {
		if(nofMessages!=null) {
			jsonObject.put("nofMessages", nofMessages);
		}
		
		if(messagesList != null) {
			JSONArray array = new JSONArray();
			for(MessagesObject message: messagesList) {
				JSONObject o = new JSONObject();
				message.encode(o);
				array.add(o);
			}
			jsonObject.put("messagesList", array);
		}
	}
	
}
