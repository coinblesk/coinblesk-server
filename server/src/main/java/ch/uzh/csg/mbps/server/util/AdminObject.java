package ch.uzh.csg.mbps.server.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AdminObject {
	private String username;
	private String email;
	private String pw1;
	private String pw2;

	public AdminObject() {
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
