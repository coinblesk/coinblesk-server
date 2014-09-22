package ch.uzh.csg.mbps.server.web.response;

import net.minidev.json.JSONObject;

import org.apache.commons.codec.binary.Base64;

import ch.uzh.csg.mbps.responseobject.TransferObject;

public class TransferPOAObject extends TransferObject {

	private String email = null;
	private byte[] payOutAddress = null;
	private String url = null;
	
	public String getEmail() {
		return email;
	}
	public void setEmail(String email) {
		this.email = email;
	}
	public String getUrl() {
		return url;
	}
	public void setUrl(String url) {
		this.url = url;
	}
	public byte[] getPayOutAddress() {
		return payOutAddress;
	}
	public void setPayOutAddress(byte[] payOutAddress) {
		this.payOutAddress = payOutAddress;
	}
	private String getPayOutAddressBase64() {
		return new String(Base64.encodeBase64(payOutAddress));
	}
	
	private void setPayOutAddressBase64(String payOutAddressBase64) {
		setPayOutAddress(Base64.decodeBase64(payOutAddressBase64.getBytes()));
	}
	@Override
	public void encode(JSONObject jsonObject) throws Exception {
		super.encode(jsonObject);
		if(payOutAddress != null){
			jsonObject.put("payloadAddressBase64", getPayOutAddressBase64());
		}
		if(email !=null) {
			jsonObject.put("email", email);
		}
		if(url !=null) {
			jsonObject.put("url", url);
		}
	}

	@Override
	public JSONObject decode(String responseString) throws Exception {
		JSONObject o = super.decode(responseString);

		String payOutAdrress = toStringOrNull(o.get("payloadAddressBase64"));
		if(payOutAdrress != null){
			setPayOutAddressBase64(payOutAdrress);
		}
		setEmail(toStringOrNull(o.get("email")));
		setUrl(toStringOrNull(o.get("url")));

		return o;
	}
}
