package ch.uzh.csg.mbps.server.web.model;

import java.util.Date;

import net.minidev.json.JSONObject;
import ch.uzh.csg.mbps.responseobject.TransferObject;

public class MessagesObject extends TransferObject {
	
	private Boolean answered;
	private Date answeredDate;
	private Date creationDate;
	private Long id;
	private String message;
	private String serverUrl;
	private String subject;
	private Integer trustLevel;

	public MessagesObject(){
		this.creationDate = new Date();
		this.answered = false;
	}
	
	public MessagesObject(String subject, String message, String url){
		super();
		this.subject = subject;
		this.message = message;
		this.serverUrl = url;
	}

	public Long getId() {
		return id;
	}
	
	public void setId(Long id) {
		this.id = id;
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

	public String getServerUrl() {
		return serverUrl;
	}
	
	public void setServerUrl(String url) {
		this.serverUrl = url;
	}
	
	public Date getCreationDate() {
		return creationDate;
	}
	
	public void setCreationDate(Date creationDate) {
		this.creationDate = creationDate;
	}

	public Date getAnsweredDate() {
		return answeredDate;
	}
	
	public void setAnsweredDate(Date answeredDate) {
		this.answeredDate = answeredDate;
	}
	
	public Boolean getAnswered() {
		return answered;
	}
	
	public void setAnswered(Boolean answered) {
		this.answered = answered;
	}

	public Integer getTrustLevel(){
		return trustLevel;
	}
	
	public void setTrustLevel(Integer trustLevel) {
		this.trustLevel = trustLevel;
	}
	
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("id: ");
		sb.append(getId());
		sb.append(" subject: ");
		sb.append(getSubject());
		sb.append(" Message: ");
		sb.append(getMessage());
		sb.append(" Server URL: ");
		sb.append(getServerUrl());
		sb.append(" creation date: ");
		sb.append(getCreationDate());
		sb.append(" answered date: ");
		sb.append(getAnsweredDate());
		sb.append(" answered: ");
		sb.append(getAnswered());
		return sb.toString();
	}
	
	public void encode(JSONObject o) {
		if(answered!=null){
			o.put("answered", answered);
		}
		if(answeredDate!=null){
			o.put("answeredDate", answeredDate);
		}
		if(creationDate!=null){
			o.put("creationDate", creationDate);
		}
		if(message!=null) {
			o.put("message", message);
		}
		if(id!=null){
			o.put("id", id);
		}
		if(serverUrl!=null) {
			o.put("serverUrl", serverUrl);
		}
		if(subject!=null) {
			o.put("subject", subject);
		}
		if(trustLevel!=null){
			o.put("trustLevel", trustLevel);
		}
    }

	public void decode(JSONObject o) {
		setAnswered(TransferObject.toBooleanOrNull(o.get("answered")));
		setAnsweredDate(TransferObject.toDateOrNull(o.get("answeredDate")));
		setCreationDate(TransferObject.toDateOrNull(o.get("creationDate")));
		setMessage(TransferObject.toStringOrNull(o.get("message")));
		setId(TransferObject.toLongOrNull((o).get("id")));
		setServerUrl(TransferObject.toStringOrNull(o.get("serverUrl")));
		setSubject(TransferObject.toStringOrNull(o.get("subject")));
		setTrustLevel(TransferObject.toIntOrNull(o.get("trustLevel")));
    }

}
