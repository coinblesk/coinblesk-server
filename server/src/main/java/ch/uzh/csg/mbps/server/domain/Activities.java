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
@Table(name = "ACTIVITIES", indexes = {
		@Index(name = "USERNAME_INDEX",  columnList="USERNAME")})
public class Activities {

	@Id
	@Column(name = "ID", nullable = false)
	@SequenceGenerator(name = "pk_sequence", sequenceName = "activities_id_seq", allocationSize = 1)
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "pk_sequence")
	private long id;
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

	public long getId(){
		return this.id;
	}
	
	public void setId(long id){
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

	public void setTitle(String subject) {
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
}