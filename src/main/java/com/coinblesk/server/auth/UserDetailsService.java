package com.coinblesk.server.auth;

import com.coinblesk.server.dao.UserAccountRepository;
import com.coinblesk.server.entity.UserAccount;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

import javax.transaction.Transactional;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Component
public class UserDetailsService implements org.springframework.security.core.userdetails.UserDetailsService {

	private final Logger log = LoggerFactory.getLogger(UserDetailsService.class);

	private final UserAccountRepository userAccountRepository;

	@Autowired
	public UserDetailsService(UserAccountRepository userAccountRepository) {
		this.userAccountRepository = userAccountRepository;
	}

	@Override
	@Transactional
	public UserDetails loadUserByUsername(final String login) {
		log.debug("Authenticating {}", login);
		String lowercaseLogin = login.toLowerCase(Locale.ENGLISH);
		Optional<UserAccount> userFromDatabase = userAccountRepository.findOptionalByEmail(lowercaseLogin);
		return userFromDatabase.map(user -> {

			// Check for deletion
			if (user.isDeleted()) {
				throw new AuthenticationServiceException("Account not active");
			}

			// Check for email verification
			if (!user.isEmailVerified()) {
				throw new AuthenticationServiceException("Email for user " + lowercaseLogin + " not verified yet");
			}

			// Populate authority list
			List<GrantedAuthority> grantedAuthorities = Collections.singletonList(new SimpleGrantedAuthority(user
				.getUserRole()));

			return new org.springframework.security.core.userdetails.User(lowercaseLogin, user.getPassword(),
				grantedAuthorities);
		}).orElseThrow(() -> new UsernameNotFoundException("User " + lowercaseLogin + " was not found in the " +
			"database"));
	}

}
