package ch.uzh.csg.mbps.server.web.model;

import java.util.Date;

import net.minidev.json.JSONObject;
import ch.uzh.csg.mbps.responseobject.TransferObject;

public class UserModelObject extends TransferObject {
		
	private Long id;
	private Date creationDate;
	private String username;
	private String email;
	private String password;
	private String paymentAddress;
	public Byte role;
		
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
		if (username != null) {
			jsonObject.put("username", username);
		}
		if (email != null) {
			jsonObject.put("email", email);
		}
		if (password != null) {
			jsonObject.put("password", password);
		}
		if (paymentAddress != null) {
			jsonObject.put("paymentAddress", paymentAddress);
		}
		if (id != null) {
			jsonObject.put("id", id);
		}
		if (creationDate != null) {
			jsonObject.put("creationDate", creationDate);
		}
		if (role != null)
			jsonObject.put("role", role);
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
		setUsername(toStringOrNull(o.get("username")));
		setEmail(toStringOrNull(o.get("email")));
		setPassword(toStringOrNull(o.get("password")));
		setPaymentAddress(toStringOrNull(o.get("paymentAddress")));
		setId(toLongOrNull(o.get("id")));
		setCreationDate(toDateOrNull(o.get("creationDate")));
		setRole(toByteOrNull(o.get("role")));
		return o;
	}
}
