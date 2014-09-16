package ch.uzh.csg.mbps.server.web.response;

import java.math.BigDecimal;

import net.minidev.json.JSONObject;
import ch.uzh.csg.mbps.keys.CustomPublicKey;
import ch.uzh.csg.mbps.responseobject.TransferObject;

public class CreateSAObject extends TransferObject {

	private BigDecimal balanceLimit;
	private CustomPublicKey customPublicKey;
	private String email;
	private Long id;
	private Integer nOfKeys;
	private String payinAddress;
	private String payoutAddress;
	private Integer trustLevel;
	private String url;
	private BigDecimal userBalanceLimit;
	
	public Long getId() {
		return id;
	}

	public void setId(Long id) {
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

	public Integer getNOfKeys() {
		return nOfKeys;
	}
	
	public void setnOfKeys(Integer nOfKeys) {
		this.nOfKeys = nOfKeys;
	}
	
	public String getPayinAddress() {
		return payinAddress;
	}

	public void setPayinAddress(String btcAddress) {
		this.payinAddress = btcAddress;
	}

	public String getPayoutAddress() {
		return payoutAddress;
	}
	
	public void setPayoutAddress(String btcAddress) {
		this.payoutAddress = btcAddress;
	}

	public Integer getTrustLevel() {
		return trustLevel;
	}

	public void setTrustLevel(Integer trustLevel) {
		this.trustLevel = trustLevel;
	}

	public BigDecimal getBalanceLimit() {
		return balanceLimit;
	}

	public void setBalanceLimit(BigDecimal balanceLimit) {
		this.balanceLimit = balanceLimit;
	}

	public BigDecimal getUserBalanceLimit() {
		return balanceLimit;
	}

	public void setUserBalanceLimit(BigDecimal userBalanceLimit) {
		this.userBalanceLimit = userBalanceLimit;
	}
	
	public CustomPublicKey getCustomPublicKey() {
	    return customPublicKey;
    }

	public void setCustomPublicKey(CustomPublicKey customPublicKey) {
	    this.customPublicKey = customPublicKey;
    }
	
	public void encodeThis(JSONObject o) {
		if(balanceLimit!=null){
			o.put("balanceLimit", balanceLimit+ "BTC");
		}
		if(email!=null){
			o.put("email",email);
		}
		if(id!=null) {
			o.put("id",id);
		}
		if(nOfKeys!=null){
			o.put("publicKey",nOfKeys);
		}
		if(payinAddress!=null){
			o.put("payinAddress",payinAddress);
		}
		if(payoutAddress!=null){
			o.put("payoutAddress",payoutAddress);
		}
		if(trustLevel!=null){
			o.put("trustLevel", trustLevel);
		}
		if(url!=null){
			o.put("url",url);
		}
		if(userBalanceLimit!=null){
			o.put("userBalanceLimit", userBalanceLimit+ "BTC");
		}
		if(customPublicKey != null) {
			o.put("keyNumber", customPublicKey.getKeyNumber());
			o.put("pkiAlgorithm", customPublicKey.getPkiAlgorithm());
			o.put("publicKey", customPublicKey.getPublicKey());
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
		setBalanceLimit(TransferObject.toBigDecimalOrNull(o.get("balanceLimit")));
		setEmail(TransferObject.toStringOrNull(o.get("email")));
		setId(TransferObject.toLongOrNull(o.get("id")));
		setnOfKeys(TransferObject.toIntOrNull(o.get("nOfKeys")));		
		setPayinAddress(TransferObject.toStringOrNull(o.get("payinAddress")));
		setPayoutAddress(TransferObject.toStringOrNull(o.get("payoutAddress")));
		setTrustLevel(TransferObject.toIntOrNull(o.get("trustLevel")));
		setUrl(TransferObject.toStringOrNull(o.get("url")));
		setUserBalanceLimit(TransferObject.toBigDecimalOrNull(o.get("userBalanceLimit")));
		
		String keyNumber = toStringOrNull(o.get("keyNumber"));
		String pkiAlgorithm = toStringOrNull(o.get("pkiAlgorithm"));
		String publicKey = toStringOrNull(o.get("publicKey"));
		
		CustomPublicKey customPublicKey = new CustomPublicKey();
		if(keyNumber!=null) {
			try {
				customPublicKey.setKeyNumber(Byte.parseByte(keyNumber));
			} catch (NumberFormatException nfe) {
				customPublicKey.setKeyNumber((byte)-1);
			}
		} else {
			customPublicKey.setKeyNumber((byte)-2);
		}
		
		if(pkiAlgorithm!=null) {
			try {
				customPublicKey.setPkiAlgorithm(Byte.parseByte(pkiAlgorithm));
			} catch (NumberFormatException nfe) {
				customPublicKey.setPkiAlgorithm((byte)-1);
			}
		} else {
			customPublicKey.setPkiAlgorithm((byte)-2);
		}
		
		customPublicKey.setPublicKey(publicKey);
		setCustomPublicKey(customPublicKey);
		return o;
	}
	
	@Override
	public String toString(){
		StringBuilder sb = new StringBuilder();
		sb.append("id: ");
		sb.append(getId());
		sb.append(", url: ");
		sb.append(getUrl());
		sb.append(", email: ");
		sb.append(getEmail());
		sb.append(", number of keys: ");
		sb.append(getNOfKeys());
		sb.append(", balance limit: ");
		sb.append(getBalanceLimit());
		sb.append(", user balance limit: ");
		sb.append(getUserBalanceLimit());
		sb.append(", trust level: ");
		sb.append(getTrustLevel());
		sb.append("CPK: ");
		if(customPublicKey != null){
			sb.append("key number: ");			
			sb.append(customPublicKey.getKeyNumber());
			sb.append(", algorithm: ");
			sb.append(customPublicKey.getPkiAlgorithm());
			sb.append(", public key: ");
			sb.append(customPublicKey.getPublicKey());
		}
		return sb.toString();
	}
}
