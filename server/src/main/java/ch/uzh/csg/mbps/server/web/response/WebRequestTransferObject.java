package ch.uzh.csg.mbps.server.web.response;

import java.util.Date;

import net.minidev.json.JSONObject;
import ch.uzh.csg.mbps.responseobject.TransferObject;

public class WebRequestTransferObject extends TransferObject {

	private Long id;
	private String url;
	private String username;
	private String email;
	private String password;
	private String subject;
	private String message;
	private Integer trustLevel;
	private Date date;
	
	public Date getDate() {
		return date;
	}

	public void setDate(Date date) {
		this.date = date;
	}

	public Integer getTrustLevel() {
		return trustLevel;
	}

	public void setTrustLevel(Integer trustLevel) {
		this.trustLevel = trustLevel;
	}

	public Long getId(){
		return id;
	}
	
	public void setId(Long id){
		this.id = id;
	}
	
	public String getUrl() {
		return url;
	}
	public void setUrl(String url) {
		this.url = url;
	}
	public String getUsername() {
		return username;
	}
	public void setUsername(String username) {
		this.username = username;
	}
	public String getEmail() {
		return email;
	}
	public void setEmail(String email) {
		this.email = email;
	}
	public String getPassword() {
		return password;
	}
	public void setPassword(String password) {
		this.password = password;
	}
	public String getSubject() {
		return subject;
	}
	public void setSubject(String subject) {
		this.subject = subject;
	}
	public String getMessage() {
		return message;
	}
	public void setMessage(String message) {
		this.message = message;
	}
	
	public void encodeThis(JSONObject jsonObject) throws Exception {
		if (id != null) {
			jsonObject.put("id", id);
		}
		if (url != null) {
			jsonObject.put("url", url);
		}
		if (username != null) {
			jsonObject.put("username", username);
		}
		if (email != null) {
			jsonObject.put("email", email);
		}
		if (password != null) {
			jsonObject.put("password", password);
		}
		if (subject != null) {
			jsonObject.put("subject", subject);
		}
		if (message != null) {
			jsonObject.put("message", message);
		}
		if (trustLevel != null) {
			jsonObject.put("trustLevel", trustLevel);
		}
		if (date!=null){
			jsonObject.put("date",date);
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
		setId(toLongOrNull(o.get("id")));
		setUrl(toStringOrNull(o.get("url")));
		setUsername(toStringOrNull(o.get("username")));
		setEmail(toStringOrNull(o.get("email")));
		setPassword(toStringOrNull(o.get("password")));
		setSubject(toStringOrNull(o.get("subject")));
		setMessage(toStringOrNull(o.get("message")));
		setTrustLevel(toIntOrNull(o.get("trustLevel")));
		setDate(toDateOrNull(o.get("date")));
		return o;
	}
	
}
