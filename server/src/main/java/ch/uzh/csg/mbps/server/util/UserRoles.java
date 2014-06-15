package ch.uzh.csg.mbps.server.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import ch.uzh.csg.mbps.server.domain.UserAccount;

/**
 * Represents the roles a MBPS user can have. Each user must have exactly one
 * role.
 * 
 * @author Jeton Memeti
 * 
 */
public class UserRoles {

	public enum Role {
		USER((byte) 0x01),
		ADMIN((byte) 0x02),
		BOTH((byte) 0x03);
		
		private byte code;
		
		private Role(byte code) {
			this.code = code;
		}
		
		public byte getCode() {
			return code;
		}
	}
	
	/**
	 * Returns the {@link GrantedAuthority}s a user account has.
	 * 
	 * @param userAccount
	 *            the {@link UserAccount} to retrieve the GrantedAuthorities
	 * @return a list of GrantedAuthorities or an empty list
	 */
	public static Collection<GrantedAuthority> getAuthorities(UserAccount userAccount) {
		return getAuthorities(userAccount.getRoles());
	}
	
	private static Collection<GrantedAuthority> getAuthorities(byte code) {
		List<GrantedAuthority> auths;
		switch (code) {
		case 0x01:
			auths = new ArrayList<GrantedAuthority>(1);
			auths.add(new SimpleGrantedAuthority("ROLE_USER"));
			break;
		case 0x02:
			auths = new ArrayList<GrantedAuthority>(1);
			auths.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
			break;
		case 0x03:
			auths = new ArrayList<GrantedAuthority>(2);
			auths.add(new SimpleGrantedAuthority("ROLE_USER"));
			auths.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
			break;
		default:
			auths = null;
			break;
		}
		return auths;
	}
	
}
