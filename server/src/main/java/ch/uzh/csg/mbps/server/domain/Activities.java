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
@Table(name = "ACTIVITIES", indexes = {@Index(name = "USERNAME_INDEX",  columnList="USERNAME")})
public class Activities {

	@Id
	@Column(name = "ID")
	@SequenceGenerator(name = "pk_sequence", sequenceName = "activities_id_seq", allocationSize = 1)
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "pk_sequence")
	private long id;
	@Column(name = "USERNAME")
	private String username;
	@Column(name = "TITLE")
	private String title;
	@Column(name = "MESSAGE")
	private String message;
	@Column(name = "CREATIONDATE", nullable = false)
	private Date creationDate;

	public Activities() {
		this.creationDate = new Date();
	}

	public Activities(String Username, String title, String message) {
		this.username = Username;
		this.title = title;
		this.message = message;
		this.creationDate = new Date();
	}

	public String getUsername() {
		return username;
	}

	public void setUsername(String username) {
		this.username = username;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}
	
	public String getMessage() {
		return message;
	}

	public void setMessage(String message) {
		this.message = message;
	}

	public Date getCreationDate() {
		return creationDate;
	}
}
