package ch.uzh.csg.mbps.server.util;

import java.io.Serializable;
import java.util.Date;

public class UserModel implements Serializable {

	private static final long serialVersionUID = -5262826359262757384L;	
		
	private long id;
	private Date creationDate;
	private String username;
	private String email;
	private String password;
	private String paymentAddress;
	public byte role;
		
	public UserModel() {}
	
	public UserModel(long id, String username, Date creationDate, String email, String password, String paymentAddress, byte role){
		this.id = id;
		this.username = username;
		this.creationDate = creationDate;
		this.email = email;
		this.password = password;
		this.paymentAddress = paymentAddress;
		this.role = role;
	}
	
	public long getId() {
		return id;
	}

	public Date getCreationDate() {
		return creationDate;
	}

	public String getUsername() {
		return username;
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
	
	public void getPassword(String password) {
		this.password = password;
	}

	public String getPaymentAddress() {
		return paymentAddress;
	}

	public byte getRole() {
		return role;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("id: ");
		sb.append(getId());
		sb.append(", creationDate: ");
		sb.append(getCreationDate());
		sb.append(", username: ");
		sb.append(getUsername());
		sb.append(", email: ");
		sb.append(getEmail());
		sb.append(", roles: ");
		sb.append(getRole());
		return sb.toString();
	}	
}
