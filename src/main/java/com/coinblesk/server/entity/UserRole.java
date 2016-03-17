package com.coinblesk.server.entity;

import org.springframework.security.core.GrantedAuthority;

/**
 * 
 * @author Andreas Albrecht
 *
 */
public enum UserRole implements GrantedAuthority {
	// Note: no "ROLE_" prefix.
	USER, 
	ADMIN;

	private static final long serialVersionUID = 1L;

	@Override
	public String getAuthority() {
		// Spring convention: authority starts with "ROLE_".
		return String.format("ROLE_%s", name());
	}

	public String getRole() {
		// Spring convention: Role without "ROLE_" prefix
		return name();
	}

}
