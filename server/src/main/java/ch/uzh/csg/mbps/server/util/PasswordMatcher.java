package ch.uzh.csg.mbps.server.util;

import ch.uzh.csg.mbps.model.UserAccount;

/**
 * Handling of password when user resets his {@link UserAccount} password.
 * @author Simon
 *
 */
public class PasswordMatcher {

	private String pw1;
	private String pw2;
	private String token;
	
	public PasswordMatcher() {
	}

	/**
	 * Compares the two saved passwords and returns boolean if they match or not.
	 * 
	 * @return boolean if passwords are equal.
	 */
	public boolean compare() {
		if (pw1.equals(pw2) && pw1.length() >= Config.MIN_PASSWORD_LENGTH)
			return true;
		else
			return false;
	}
	
	public String getPw1() {
		return pw1;
	}

	public void setPw1(String pw1) {
		this.pw1 = pw1;
	}

	public String getPw2() {
		return pw2;
	}

	public void setPw2(String pw2) {
		this.pw2 = pw2;
	}

	public String getToken() {
		return token;
	}

	public void setToken(String token) {
		this.token = token;
	}
	
}
