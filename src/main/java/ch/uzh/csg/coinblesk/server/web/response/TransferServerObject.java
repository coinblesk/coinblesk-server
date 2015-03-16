package ch.uzh.csg.coinblesk.server.web.response;

import org.apache.commons.codec.binary.Base64;

import net.minidev.json.JSONObject;
import ch.uzh.csg.coinblesk.responseobject.TransferObject;

public class TransferServerObject extends TransferObject {

	private byte[] customServerPaymentResponse = null;
	
	public byte[] getCustomServerPaymentResponse() {
		return customServerPaymentResponse;
	}
	public void setCustomServerPaymentResponse(byte[] customServerPaymentResponse) {
		this.customServerPaymentResponse = customServerPaymentResponse;
	}
	
	private String getCustomServerPaymentResponseBase64() {
		return new String(Base64.encodeBase64(customServerPaymentResponse));
	}
	
	private void setCustomServerPaymentResponseBase64(String customServerPaymentResponseBase64) {
		setCustomServerPaymentResponse(Base64.decodeBase64(customServerPaymentResponseBase64.getBytes()));
	}

	@Override
	public void encode(JSONObject jsonObject) throws Exception {
		super.encode(jsonObject);
		if(customServerPaymentResponse != null){
			jsonObject.put("payloadBase64", getCustomServerPaymentResponseBase64());
		}
	}

	@Override
	public JSONObject decode(String responseString) throws Exception {
		JSONObject o = super.decode(responseString);
		
		String customServerPaymentResponse = toStringOrNull(o.get("payloadBase64"));
		if(customServerPaymentResponse != null){
			setCustomServerPaymentResponseBase64(customServerPaymentResponse);
		}

		return o;
	}
}