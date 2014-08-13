package ch.uzh.csg.mbps.server.domain;

import java.util.Date;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.SequenceGenerator;

import org.hibernate.annotations.Index;

@Entity(name = "ADMINROLE")
public class AdminRole {

	@Id
	@Column(name = "ID")
	@SequenceGenerator(name = "pk_sequence", sequenceName = "adminRole_id_seq", allocationSize = 1)
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "pk_sequence")
	private long id;
	@Column(name = "USER_ID")
	private long userID;
	@Column(name = "TOKEN")
	@Index(name = "TOKEN_INDEX")
	private String token;
	@Column(name = "CREATIONDATE", nullable = false)
	private Date creationDate;

	public AdminRole() {
		this.creationDate = new Date();
	}

	public AdminRole(long UserID, String token) {
		this.userID = UserID;
		this.token = token;
		this.creationDate = new Date();
	}

	public long getUserID() {
		return userID;
	}

	public void setUserID(long userID) {
		this.userID = userID;
	}

	public String getToken() {
		return token;
	}

	public void setToken(String token) {
		this.token = token;
	}

	public Date getCreationDate() {
		return creationDate;
	}
}