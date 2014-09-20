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

import net.minidev.json.JSONObject;
import ch.uzh.csg.mbps.responseobject.TransferObject;

@Entity
@Table(name = "ACTIVITIES", indexes = {
		@Index(name = "USERNAME_INDEX",  columnList="USERNAME")})
public class Activities {

	@Id
	@Column(name = "ID", nullable = false)
	@SequenceGenerator(name = "pk_sequence", sequenceName = "activities_id_seq", allocationSize = 1)
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "pk_sequence")
	private Long id;
	@Column(name = "USERNAME")
	private String username;
	@Column(name = "SUBJECT")
	private String subject;
	@Column(name = "MESSAGE")
	private String message;
	@Column(name = "CREATION_DATE", nullable = false)
	private Date creationDate;

	public Activities() {
		this.creationDate = new Date();
	}

	public Activities(String Username, String subject, String message) {
		this.username = Username;
		this.subject = subject;
		this.message = message;
		this.creationDate = new Date();
	}

	public Long getId(){
		return this.id;
	}
	
	public void setId(Long id){
		this.id = id;
	}
	
	public String getUsername() {
		return username;
	}

	public void setUsername(String username) {
		this.username = username;
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

	public void setCreationDate(Date date) {
		this.creationDate = date;
	}

	public Date getCreationDate() {
		return creationDate;
	}
	
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("id: ");
		sb.append(getId());
		sb.append(" username: ");
		sb.append(getUsername());
		sb.append(" subject: ");
		sb.append(getSubject());
		sb.append(" Message: ");
		sb.append(getMessage());
		sb.append(" creation date: ");
		sb.append(getCreationDate());
		return sb.toString();
	}
	
	public void encode(JSONObject o) {
		if(username!=null) {
			o.put("username", username);
		}
		if(subject!=null) {
			o.put("subject", subject);
		}
		if(message!=null) {
			o.put("message", message);
		}
		if(id!=null){
			o.put("id", id);
		}
		if(creationDate!=null){
			o.put("creationDate", creationDate);
		}
    }

	public void decode(JSONObject o) {
		setUsername(TransferObject.toStringOrNull(o.get("username")));
		setSubject(TransferObject.toStringOrNull(o.get("subject")));
		setMessage(TransferObject.toStringOrNull(o.get("message")));
		setId(TransferObject.toLongOrNull((o).get("id")));
		setCreationDate(TransferObject.toDateOrNull(o.get("creationDate")));
    }
}