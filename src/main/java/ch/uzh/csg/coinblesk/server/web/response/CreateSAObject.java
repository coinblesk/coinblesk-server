package ch.uzh.csg.coinblesk.server.web.response;

import net.minidev.json.JSONObject;
import ch.uzh.csg.coinblesk.responseobject.TransferObject;

public class CreateSAObject extends TransferObject {

	private String email;
	private Long id;
	private Byte keyNumber;
	private Byte pkiAlgorithm;
	private String publicKey;
	private String url;
	
	
	public Long getId(){
		return id;
	}
	
	public void setId(Long id){
		this.id = id;
	}
	public String getUrl() {
		return url;
	}
	public void setUrl(String url) {
		this.url = url;
	}
	public String getEmail() {
		return email;
	}
	public void setEmail(String email) {
		this.email = email;
	}
	public Byte getKeyNumber() {
		return keyNumber;
	}
	public void setKeyNumber(Byte keyNumber) {
		this.keyNumber = keyNumber;
	}
	public Byte getPkiAlgorithm() {
		return pkiAlgorithm;
	}
	public void setPkiAlgorithm(Byte pkiAlgorithm) {
		this.pkiAlgorithm = pkiAlgorithm;
	}
	public String getPublicKey() {
		return publicKey;
	}
	public void setPublicKey(String publicKey) {
		this.publicKey = publicKey;
	}

	public void encodeThis(JSONObject jsonObject) throws Exception {
		if(email!=null){
			jsonObject.put("email",email);
		}
		if(id!=null){
			jsonObject.put("id", id);
		}
		if(keyNumber!=null){			
			jsonObject.put("keyNumber", getKeyNumber());
		}
		if(pkiAlgorithm!=null){			
			jsonObject.put("pkiAlgorithm", getPkiAlgorithm());
		}
		if(publicKey != null){			
			jsonObject.put("publicKey", getPublicKey());
		}
		if(url!=null){
			jsonObject.put("url",url);
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
		
		setEmail(TransferObject.toStringOrNull(o.get("email")));
		setId(TransferObject.toLongOrNull(o.get("id")));
		setKeyNumber(TransferObject.toByteOrNull(o.get("keyNumber")));
		setPkiAlgorithm(TransferObject.toByteOrNull(o.get("pkiAlgorithm")));
		setPublicKey(TransferObject.toStringOrNull(o.get("publicKey")));
		setUrl(TransferObject.toStringOrNull(o.get("url")));
		return o;
	}
}
