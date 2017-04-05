/*
 * Copyright 2016 The Coinblesk team and the CSG Group at University of Zurich
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.coinblesk.server.config;

import static org.springframework.http.HttpMethod.OPTIONS;

import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.coinblesk.server.auth.Http401UnauthorizedEntryPoint;
import com.coinblesk.server.auth.JWTConfigurer;
import com.coinblesk.server.auth.TokenProvider;

/**
 *
 * @author Thomas Bocek
 * @author Andreas Albrecht
 * @author Sebastian Stephan
 */
@EnableWebSecurity
public class SecurityConfig extends WebSecurityConfigurerAdapter {

	@Autowired
	private Http401UnauthorizedEntryPoint http401UnauthorizedEntryPoint;

	@Autowired
	private TokenProvider tokenProvider;

	@Autowired
	private UserDetailsService userDetailsService;

	@Autowired
	private PasswordEncoder passwordEncoder;

	final private static String[] REQUIRE_USER_ROLE = { "/user/auth/**", "/v?/user/auth/**"};
	final private static String[] REQUIRE_ADMIN_ROLE = { "/admin/**", "/v?/admin/**" };

	@Autowired
	public void configureGlobal(AuthenticationManagerBuilder auth) {
		try {
			auth.userDetailsService(userDetailsService).passwordEncoder(passwordEncoder);
		} catch (Exception e) {
			throw new BeanInitializationException("Security configuration failed", e);
		}
	}

	@Override
	protected void configure(HttpSecurity http) throws Exception {
		http
			.cors()
		.and()
			.exceptionHandling()
			.authenticationEntryPoint(http401UnauthorizedEntryPoint)
		.and()
			.csrf()
			.disable()
			.headers()
			.frameOptions()
			.sameOrigin() // To allow h2 console
		.and()
			.sessionManagement()
			.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
		.and() // allow CORS's OPTIONS preflight
			.authorizeRequests()
			.antMatchers(OPTIONS, "/**").permitAll()
		.and()
			.authorizeRequests()
			// .antMatchers("/").permitAll()
			.antMatchers(REQUIRE_USER_ROLE).hasAuthority(UserRole.USER.getAuthority())
			.antMatchers(REQUIRE_ADMIN_ROLE).hasAuthority(UserRole.ADMIN.getAuthority())
		.and()
			.apply(securityConfigurerAdapter());
	}

	private JWTConfigurer securityConfigurerAdapter() {
		return new JWTConfigurer(tokenProvider);
	}

}
