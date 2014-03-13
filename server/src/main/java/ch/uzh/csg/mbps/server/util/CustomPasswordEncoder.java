package ch.uzh.csg.mbps.server.util;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Class for encoding passwords.
 *
 */
public class CustomPasswordEncoder {
	private static PasswordEncoder encoder = new BCryptPasswordEncoder();
	
	/**
	 * Encodes plaintext password and returns encoded pw.
	 * 
	 * @param password
	 * @return String with encoded password
	 */
	public static String getEncodedPassword(String password) {
		return encoder.encode(password);
	}
	
	/**
	 * Checks if rawPassword and hash matches.
	 * 
	 * @param rawPassword
	 * @param hash
	 * @return boolean if raw password and hashed password match
	 */
	public static boolean matches(String rawPassword, String hash) {
		return encoder.matches(rawPassword, hash);
	}
	
}
