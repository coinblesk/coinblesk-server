package ch.uzh.csg.mbps.server.web.response;

import net.minidev.json.JSONObject;
import ch.uzh.csg.mbps.responseobject.TransferObject;

public class ServerAccountUpdatedObject extends TransferObject{

	private String email;
	private Integer trustLevel;
	private String url;
	
	public ServerAccountUpdatedObject(){
		
	}

	public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		this.url = url;
	}
	
	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email;
	}
	
	public Integer getTrustLevel() {
		return trustLevel;
	}

	public void setTrustLevel(Integer trustLevel) {
		this.trustLevel = trustLevel;
	}
	
	@Override
	public String toString(){
		StringBuilder sb = new StringBuilder();
		sb.append(", url: ");
		sb.append(getUrl());
		sb.append(", email: ");
		sb.append(getEmail());
		sb.append(", trust level: ");
		sb.append(getTrustLevel());
		
		return sb.toString();
	}
	
	public void encodeThis(JSONObject o) {
		if(email!=null){
			o.put("email",email);
		}
		if(trustLevel!=null){
			o.put("trustLevel", trustLevel.toString());
		}
		if(url!=null){
			o.put("url",url);
		}
    }
	
	@Override
	public void encode(JSONObject jsonObject) throws Exception {
		super.encode(jsonObject);
		encodeThis(jsonObject);
	}
	
	@Override
	public JSONObject decode(String responseString) throws Exception {
		JSONObject o = super.decode(responseString);
		return decode(o);
	}
	
	public JSONObject decode(JSONObject o) {
		setEmail(TransferObject.toStringOrNull(o.get("email")));	
		String trustLevelStr = toStringOrNull(o.get("trustLevel"));
		if(trustLevel != null){
			setTrustLevel(Integer.parseInt(trustLevelStr));
		}
		setUrl(TransferObject.toStringOrNull(o.get("url")));
		return o;
    }
}
