package ch.uzh.csg.mbps.server.domain;

import java.io.Serializable;
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
@Table(name = "SERVER_ACCOUNT_TASKS", indexes = {
		@Index(name = "TOKEN_TASKS_INDEX",  columnList="TOKEN"),
		@Index(name = "URL_TASKS_INDEX",  columnList="URL"),		
		@Index(name = "TYPE_TASKS_INDEX", columnList="TYPE"),
		@Index(name = "PROCEED_TASKS_INDEX" , columnList="PROCEED")})
public class ServerAccountTasks implements Serializable{
	private static final long serialVersionUID = 5881836235554219228L;

	@Id
	@Column(name = "ID", nullable = false)
	@SequenceGenerator(name = "pk_sequence", sequenceName = "server_account_tasks_id_seq", allocationSize = 1)
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "pk_sequence")
	long id;
	@Column(name="TOKEN", nullable = false)
	String token;
	@Column(name="TIMESTAMP", nullable = false)
	Date timestamp;
	@Column(name="TYPE", nullable = false)
	int type;
	@Column(name="URL", nullable = false)
	String url;
	@Column(name="EMAIL")
	String email;
	@Column(name="USERNAME")
	String username;
	@Column(name="PAYOUT_ADDRESS")
	String payoutAddress;
	@Column(name="TRUST_LEVEL")
	int trustLevel;
	@Column(name="PROCEED", nullable = false)
	boolean proceed;
	
	public ServerAccountTasks(){
		this.timestamp = new Date();
		this.proceed = false;
	}
	
	public long getId() {
		return id;
	}
	
	public void setId(long id) {
		this.id = id;
	}
	
	public String getToken() {
		return token;
	}
	
	public void setToken(String token) {
		this.token = token;
	}
	
	public Date getTimestamp() {
		return timestamp;
	}
	
	public void setTimestamp(Date timestamp) {
		this.timestamp = timestamp;
	}
	
	public int getType() {
		return type;
	}
	
	public void setType(int type) {
		this.type = type;
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
	
	public String getPayoutAddress() {
		return payoutAddress;
	}
	
	public void setPayoutAddress(String payoutAddress) {
		this.payoutAddress = payoutAddress;
	}
	
	public int getTrustLevel() {
		return trustLevel;
	}
	
	public void setTrustLevel(int trustLevel) {
		this.trustLevel = trustLevel;
	}
	
	public boolean getProceed() {
		return proceed;
	}
	
	public void setProceed(boolean proceed) {
		this.proceed = proceed;
	}
	
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("token: ");
		sb.append(getToken());
		sb.append(", timestamp: ");
		sb.append(getTimestamp());
		sb.append(", type: ");
		sb.append(getType());
		sb.append(", url: ");
		sb.append(getUrl());
		sb.append(", username: ");
		sb.append(getUsername());
		sb.append(", email:");
		sb.append(getEmail());
		sb.append(", payout address:");
		sb.append(getPayoutAddress());
		sb.append(", trust level: ");
		sb.append(getTrustLevel());
		sb.append(", proceed: ");
		sb.append(getProceed());
		
		return sb.toString();
	}
}
