package ch.uzh.csg.mbps.server.web.response;

import java.math.BigDecimal;

import ch.uzh.csg.mbps.responseobject.TransferObject;
import net.minidev.json.JSONObject;

public class ServerAccountObject extends TransferObject {
	
	private BigDecimal activeBalance;
	private BigDecimal balanceLimit;
	private String email;
	private Long id;
	private Integer nOfKeys;
	private String payinAddress;
	private String payoutAddress;
	private Integer trustLevel;
	private String url;
	private BigDecimal userBalanceLimit;
	
	public ServerAccountObject(){
	}
	
	public ServerAccountObject(String url, String email){
		this.url = url;
		this.email = email;
		this.nOfKeys = 0;
		this.payinAddress="";
		this.payoutAddress="";
		this.trustLevel = 0;
		this.activeBalance= BigDecimal.ZERO;
		this.balanceLimit= BigDecimal.ZERO;
		this.userBalanceLimit = BigDecimal.ZERO;
	}
	
	public ServerAccountObject(Long id, String url,String email, Integer nOfKeys, String payinAddress, String payoutAddress, Integer trustLevel,
			BigDecimal activeBalance, BigDecimal balanceLimit, BigDecimal userBalanceLimit){
		this.id = id;
		this.url = url;
		this.email = email;
		this.nOfKeys = nOfKeys;
		this.payinAddress = payinAddress;
		this.payoutAddress = payoutAddress;
		this.trustLevel = trustLevel;
		this.activeBalance = activeBalance;
		this.balanceLimit = balanceLimit;
		this.userBalanceLimit = userBalanceLimit;
	}
	
	public ServerAccountObject(String url) {
		this.url = url;
	}

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

	public BigDecimal getActiveBalance() {
		return activeBalance;
	}
	
	public void setActiveBalance(BigDecimal activeBalance) {
		this.activeBalance = activeBalance;
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
		sb.append(", active balance: ");
		sb.append(getActiveBalance());
		sb.append(", balance limit: ");
		sb.append(getBalanceLimit());
		sb.append(", user balance limit: ");
		sb.append(getUserBalanceLimit());
		sb.append(", trust level: ");
		sb.append(getTrustLevel());
		return sb.toString();
	}

	public void encodeThis(JSONObject o) {
		if(activeBalance!=null){
			o.put("activeBalance", activeBalance+ "BTC");
		}
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
		setActiveBalance(TransferObject.toBigDecimalOrNull(o.get("activeBalance")));
		setBalanceLimit(TransferObject.toBigDecimalOrNull(o.get("balanceLimit")));
		setEmail(TransferObject.toStringOrNull(o.get("email")));
		setId(TransferObject.toLongOrNull(o.get("id")));
		setnOfKeys(TransferObject.toIntOrNull(o.get("nOfKeys")));		
		setPayinAddress(TransferObject.toStringOrNull(o.get("payinAddress")));
		setPayoutAddress(TransferObject.toStringOrNull(o.get("payoutAddress")));
		setTrustLevel(TransferObject.toIntOrNull(o.get("trustLevel")));
		setUrl(TransferObject.toStringOrNull(o.get("url")));
		setUserBalanceLimit(TransferObject.toBigDecimalOrNull(o.get("userBalanceLimit")));
		return o;
    }
}