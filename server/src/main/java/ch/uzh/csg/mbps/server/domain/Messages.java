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
@Table(name = "MESSAGES", indexes = {
		@Index(name = "SERVER_URL_MSG_INDEX",  columnList="SERVER_URL"), 
		@Index(name = "ANSWERED_INDEX",  columnList="ANSWERED")})
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
	@Column(name = "CREATION_DATE", nullable = false)
	private Date creationDate;
	@Column(name = "ANSWERED_DATE")
	private Date answeredDate;
	@Column(name = "ANSWERED", nullable = false)
	private boolean answered;
	@Column(name="TRUST_LEVEL")
	private int trustLevel;
	

	public Messages(){
		this.creationDate = new Date();
		this.answered = false;
		this.trustLevel = -1;
	}
	
	public Messages(String subject, String message, String url){
		this.creationDate = new Date();
		this.answered = false;
		this.trustLevel = -1;
		this.subject = subject;
		this.message = message;
		this.serverUrl = url;
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
	
	public int getTrustLevel() {
		return trustLevel;
	}

	public void setTrustLevel(int trustLevel) {
		this.trustLevel = trustLevel;
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
		sb.append(" creation date: ");
		sb.append(getCreationDate());
		sb.append(" answered date: ");
		sb.append(getAnsweredDate());
		sb.append(" answered: ");
		sb.append(getAnswered());
		return sb.toString();
	}

}
