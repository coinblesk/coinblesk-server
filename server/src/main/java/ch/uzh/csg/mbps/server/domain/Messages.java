package ch.uzh.csg.mbps.server.domain;

import java.util.Date;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;

@Entity
@Table(name = "MESSAGES", indexes = {@Index(name = "SERVER_URL_MSG_INDEX",  columnList="SERVER_URL"), @Index(name = "ANSWERED_INDEX",  columnList="ANSWERED")})
public class Messages {

	@Id
	@Column(name = "ID", nullable = false)
	@SequenceGenerator(name = "pk_sequence", sequenceName = "messages_id_seq", allocationSize = 1)
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "pk_sequence")
	private long id;
	@Column(name = "SUBJECT")
	private String subject;
	@Column(name = "MESSAGE")
	private String message;
	@Column(name = "SERVER_URL")
	private String serverUrl;
	@Column(name = "TIMESTAMP", nullable = false)
	private Date timestamp;
	@Column(name = "ANSWERED", nullable = false)
	boolean answered;

	public Messages(){
		this.timestamp = new Date();
		this.answered = false;
	}
	
	public Messages(String subject, String message, String url){
		this.subject = subject;
		this.message = message;
		this.serverUrl = url;
		this.timestamp = new Date();
		this.answered = false;
	}

	public long getId() {
		return id;
	}
	
	public void setId(long id) {
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
	
	public Date getTimestamp() {
		return timestamp;
	}
	
	public void setTimestamp(Date timestamp) {
		this.timestamp = timestamp;
	}
	
	public boolean getAnswered() {
		return answered;
	}
	
	public void setAnswered(boolean answered) {
		this.answered = answered;
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
		sb.append(" timestamp: ");
		sb.append(getTimestamp());
		sb.append(" answered: ");
		sb.append(getAnswered());
		return sb.toString();
	}
}
