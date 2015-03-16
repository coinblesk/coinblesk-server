package ch.uzh.csg.coinblesk.server.web.response;

import java.math.BigDecimal;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.minidev.json.JSONObject;
import ch.uzh.csg.coinblesk.responseobject.TransferObject;

public class WebRequestTransferObject extends TransferObject {

	private BigDecimal activeBalance;
	private BigDecimal balanceLimit;
	private Long id;
	private String url;
	private String username;
	private String email;
	private String password;
	private String subject;
	private String message;
	private Integer messageId;
	private Integer trustLevel;
	private Integer trustLevelOld;
	private BigDecimal userBalanceLimit;
	
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
		return userBalanceLimit;
	}
	public void setUserBalanceLimit(BigDecimal userBalanceLimit) {
		this.userBalanceLimit = userBalanceLimit;
	}
	public Integer getTrustLevelOld() {
		return trustLevelOld;
	}
	public void setTrustLevelOld(Integer trustLevelOld) {
		this.trustLevelOld = trustLevelOld;
	}
	private Date date;
	
	public Date getDate() {
		return date;
	}
	public void setDate(Date date) {
		this.date = date;
	}
	public Integer getTrustLevel() {
		return trustLevel;
	}
	public void setTrustLevel(Integer trustLevel) {
		this.trustLevel = trustLevel;
	}
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
	public String getSubject() {
		return subject;
	}
	public void setSubject(String subject) {
		this.subject = subject;
	}
	public String getMessage() {
		return message;
	}
	public void setMessage(String message) {
		this.message = message;
	}
	public Integer getMessageId() {
		return messageId;
	}
	public void setMessageId(Integer messageId) {
		this.messageId = messageId;
	}
	
	public void encodeThis(JSONObject jsonObject) throws Exception {
		if (activeBalance != null) {
			jsonObject.put("activeBalance",activeBalance + "BTC");
		}
		if (balanceLimit != null) {
			jsonObject.put("balanceLimit",balanceLimit + "BTC");
		}
		if (date!=null){
			jsonObject.put("date",date);
		}
		if (email != null) {
			jsonObject.put("email", email);
		}
		if (id != null) {
			jsonObject.put("id", id);
		}
		if (message != null) {
			jsonObject.put("message", message);
		}
		if (messageId != null) {
			jsonObject.put("messageId", messageId);
		}
		if (password != null) {
			jsonObject.put("password", password);
		}
		if (subject != null) {
			jsonObject.put("subject", subject);
		}
		if (trustLevel != null) {
			jsonObject.put("trustLevel", trustLevel);
		}
		if (trustLevelOld != null) {
			jsonObject.put("trustLevelOld", trustLevelOld);
		}
		if (url != null) {
			jsonObject.put("url", url);
		}
		if (username != null) {
			jsonObject.put("username", username);
		}
		if (userBalanceLimit != null) {
			jsonObject.put("userBalanceLimit",userBalanceLimit + "BTC");
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
		String activeBalance = toStringOrNull(o.get("activeBalance"));
		if (balanceLimit != null) {
			
			Pattern pattern = Pattern.compile("[A-Za-z]");
			Matcher matcher = pattern.matcher(activeBalance);
			if (matcher.find()) {
				int start = matcher.start();
				if (start >= 0) {
					String number = activeBalance.substring(0, start);
					try {
						setBalanceLimit(new BigDecimal(number));
					} catch (NumberFormatException nfe) {
						setBalanceLimit(BigDecimal.ZERO);
					}
				}
			}
			
		}

	String balanceLimit = toStringOrNull(o.get("balanceLimit"));
		if (balanceLimit != null) {

			Pattern pattern = Pattern.compile("[A-Za-z]");
			Matcher matcher = pattern.matcher(balanceLimit);
			if (matcher.find()) {
				int start = matcher.start();
				if (start >= 0) {
					String number = balanceLimit.substring(0, start);
					try {
						setBalanceLimit(new BigDecimal(number));
					} catch (NumberFormatException nfe) {
						setBalanceLimit(BigDecimal.ZERO);
					}
				}
			}

		}
		
		setDate(toDateOrNull(o.get("date")));
		setEmail(toStringOrNull(o.get("email")));
		setId(toLongOrNull(o.get("id")));
		setMessage(toStringOrNull(o.get("message")));
		setMessageId(toIntOrNull(o.get("messageId")));
		setPassword(toStringOrNull(o.get("password")));
		setSubject(toStringOrNull(o.get("subject")));
		setTrustLevel(toIntOrNull(o.get("trustLevel")));
		setTrustLevel(toIntOrNull(o.get("trustLevelOld")));
		setUrl(toStringOrNull(o.get("url")));
		setUsername(toStringOrNull(o.get("username")));
		
		String userBalanceLimit = toStringOrNull(o.get("userBalanceLimit"));
		if (userBalanceLimit != null) {

			Pattern pattern = Pattern.compile("[A-Za-z]");
			Matcher matcher = pattern.matcher(userBalanceLimit);
			if (matcher.find()) {
				int start = matcher.start();
				if (start >= 0) {
					String number = userBalanceLimit.substring(0, start);
					try {
						setBalanceLimit(new BigDecimal(number));
					} catch (NumberFormatException nfe) {
						setBalanceLimit(BigDecimal.ZERO);
					}
				}
			}

		}
		return o;
	}
	
}