package ch.uzh.csg.mbps.server.auth;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import ch.uzh.csg.mbps.server.domain.UserAccount;
import ch.uzh.csg.mbps.server.service.UserAccountService;
import ch.uzh.csg.mbps.server.util.UserRoles;

/**
 * This is the authentication manager, which decides if the authentication is
 * successful or not. It compares the password, loads the user account from the
 * database, etc.
 */
public class CustomAuthenticationManager implements AuthenticationManager {
	private UserAccountService userAccountService = UserAccountService.getInstance();
	private PasswordEncoder pwEncoder = new BCryptPasswordEncoder();
	
	@Override
	public Authentication authenticate(Authentication authentication) throws AuthenticationException {
		UserAccount userAccount = null;
		
		try {
			userAccount = userAccountService.getByUsername(authentication.getName());
		} catch (Exception e) {
			String errMsg = "User \""+authentication.getName()+"\" does not exist!";
			throw new BadCredentialsException(errMsg);
		}
		
		if (!userAccount.isEmailVerified()) {
			UserAccountService.getInstance().resendVerificationEmail(userAccount);
			String errMsg = "User \""+authentication.getName()+"\" has to verify his email address!";
			throw new BadCredentialsException(errMsg);
		}
		
		if (!pwEncoder.matches((String) authentication.getCredentials(), userAccount.getPassword())) {
			String errMsg = "User \""+authentication.getName()+"\" entered wrong password!";
			throw new BadCredentialsException(errMsg);
		}
		
		return new UsernamePasswordAuthenticationToken(authentication.getName(), authentication.getCredentials(), UserRoles.getAuthorities(userAccount));
	}
	
}
