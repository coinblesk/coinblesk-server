package ch.uzh.csg.mbps.server.web.model;

import java.util.Date;

import net.minidev.json.JSONObject;
import ch.uzh.csg.mbps.responseobject.TransferObject;

public class UserModelObject extends TransferObject {
		
	private Date creationDate;
	private String email;
	private Long id;
	private String password;
	private String paymentAddress;
	public Byte role;
	private String username;
		
	public UserModelObject() {}
	
	public UserModelObject(Long id, String username, Date creationDate, String email, String password, String paymentAddress, Byte role){
		this.id = id;
		this.username = username;
		this.creationDate = creationDate;
		this.email = email;
		this.password = password;
		this.paymentAddress = paymentAddress;
		this.role = role;
	}
	
	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public Date getCreationDate() {
		return creationDate;
	}

	public void setCreationDate(Date creationDate) {
		this.creationDate = creationDate;
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
	
	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}
	
	public void getPassword(String password) {
		this.password = password;
	}

	public void setPaymentAddress(String paymentAddress) {
		this.paymentAddress = paymentAddress;
	}

	public String getPaymentAddress() {
		return paymentAddress;
	}

	public Byte getRole() {
		return role;
	}
	
	public void setRole(Byte role) {
		this.role = role;
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
	
	public void encodeThis(JSONObject jsonObject) throws Exception {
		if (creationDate != null) {
			jsonObject.put("creationDate", creationDate);
		}
		if (email != null) {
			jsonObject.put("email", email);
		}
		if (id != null) {
			jsonObject.put("id", id);
		}
		if (password != null) {
			jsonObject.put("password", password);
		}
		if (paymentAddress != null) {
			jsonObject.put("paymentAddress", paymentAddress);
		}
		if (role != null){
			jsonObject.put("role", role);			
		}
		if (username != null) {
			jsonObject.put("username", username);
		}
	}
	@Override
	public void encode(JSONObject jsonObject) throws Exception {
		super.encode(jsonObject);
		encodeThis(jsonObject);
	}

	@Override
	public JSONObject decode(String responseString) throws Exception {
		JSONObject o = super.decode(responseString);
		return decode(o);
	}

	public JSONObject decode(JSONObject o) {
		setCreationDate(toDateOrNull(o.get("creationDate")));
		setEmail(toStringOrNull(o.get("email")));
		setId(toLongOrNull(o.get("id")));
		setPassword(toStringOrNull(o.get("password")));
		setPaymentAddress(toStringOrNull(o.get("paymentAddress")));
		setRole(toByteOrNull(o.get("role")));
		setUsername(toStringOrNull(o.get("username")));
		return o;
	}
}
