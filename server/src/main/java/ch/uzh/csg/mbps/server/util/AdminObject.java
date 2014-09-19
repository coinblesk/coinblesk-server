package ch.uzh.csg.mbps.server.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AdminObject {
	private String username;
	private String email;
	private String pw1;
	private String pw2;
	private String token;

	public AdminObject() {
	}

	public String getToken() {
		return token;
	}

	public void setToken(String token) {
		this.token = token;
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

	/**
	 * Compares the two saved passwords and returns boolean if they match or
	 * not.
	 * 
	 * @return boolean if passwords are equal.
	 */
	public boolean compare() {
		if (pw1.equals(pw2) && pw1.length() >= Config.MIN_PASSWORD_LENGTH)
			return true;
		else
			return false;
	}

	/**
	 * Check if username has the right format.
	 * 
	 * @return boolean
	 */
	public boolean checkUsername() {
		Matcher matcher = Pattern.compile(Config.USERNAME_REGEX).matcher(
				username);
		return matcher.matches();
	}

	/**
	 * Check if email has the right format.
	 * 
	 * @return boolean
	 */
	public boolean checkEmail() {
		Matcher matcher = Pattern.compile(Config.EMAIL_REGEX).matcher(email);
		return matcher.matches();
	}
}
