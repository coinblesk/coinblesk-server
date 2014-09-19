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
@Table(name = "ADMINROLE", indexes = {@Index(name = "TOKEN_INDEX",  columnList="TOKEN")})
public class AdminRole {

	@Id
	@Column(name = "ID", nullable = false)
	@SequenceGenerator(name = "pk_sequence", sequenceName = "adminRole_id_seq", allocationSize = 1)
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "pk_sequence")
	private long id;
	@Column(name = "ADMIN_EMAIL")
	private String adminEmail;
	@Column(name = "TOKEN")
	private String token;
	@Column(name = "CREATIONDATE", nullable = false)
	private Date creationDate;

	public AdminRole() {
		this.creationDate = new Date();
	}

	public AdminRole(String adminEmail, String token) {
		this.adminEmail = adminEmail;
		this.token = token;
		this.creationDate = new Date();
	}

	public String getAdminId() {
		return adminEmail;
	}

	public void setAdminId(String adminEmail) {
		this.adminEmail = adminEmail;
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