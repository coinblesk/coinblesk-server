package ch.uzh.csg.coinblesk.server.util;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * A helper class to return the principal user name.
 */
public class AuthenticationInfo {
	
	/**
	 * Returns the principal username
	 * 
	 * @throws UserIsNotAuthenticatedException
	 *             if the security context holder does not hold an authentication
	 */
	public static String getPrincipalUsername() {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth != null)
			return (String) auth.getPrincipal();
		else
			return "";
	}
	
}
