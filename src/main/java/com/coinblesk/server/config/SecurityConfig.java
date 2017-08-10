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

import static com.coinblesk.server.config.Constants.PROFILE_PROD;
import static com.coinblesk.server.config.Constants.PROFILE_TEST;
import static java.util.Arrays.asList;
import static org.springframework.http.HttpMethod.OPTIONS;

import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import com.coinblesk.server.auth.Http401UnauthorizedEntryPoint;
import com.coinblesk.server.auth.JWTConfigurer;
import com.coinblesk.server.auth.TokenProvider;

/**
 * @author Thomas Bocek
 * @author Andreas Albrecht
 * @author Sebastian Stephan
 */
@EnableWebSecurity
@EnableGlobalMethodSecurity(securedEnabled = true)
public class SecurityConfig extends WebSecurityConfigurerAdapter {

	private final Http401UnauthorizedEntryPoint http401UnauthorizedEntryPoint;
	private final TokenProvider tokenProvider;
	private final UserDetailsService userDetailsService;
	private final PasswordEncoder passwordEncoder;
	private final Environment env;

	@Autowired
	public SecurityConfig(Http401UnauthorizedEntryPoint http401UnauthorizedEntryPoint, TokenProvider tokenProvider,
						  UserDetailsService userDetailsService, PasswordEncoder passwordEncoder, Environment env) {
		this.http401UnauthorizedEntryPoint = http401UnauthorizedEntryPoint;
		this.tokenProvider = tokenProvider;
		this.userDetailsService = userDetailsService;
		this.passwordEncoder = passwordEncoder;
		this.env = env;
	}

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
			.configurationSource(corsConfiguration())
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
		.and()
			.authorizeRequests()
			.antMatchers(OPTIONS, "/**").permitAll()
		.and()
			.apply(securityConfigurerAdapter());
	}

	private JWTConfigurer securityConfigurerAdapter() {
		return new JWTConfigurer(tokenProvider);
	}

	private CorsConfigurationSource corsConfiguration() {
		return new CorsConfigurationSource() {
			@Override
			public CorsConfiguration getCorsConfiguration(HttpServletRequest request) {
				CorsConfiguration corsConfig = new CorsConfiguration();

				if(!asList(env.getActiveProfiles()).contains(PROFILE_PROD) && !asList(env.getActiveProfiles()).contains(PROFILE_TEST)) {
					corsConfig.setAllowedOrigins(asList("*"));
					corsConfig.setAllowedMethods(asList("GET", "PUT", "POST", "DELETE", "OPTIONS"));
					corsConfig.setAllowedHeaders(asList("*"));
					corsConfig.setMaxAge(1800L);
				}
				return corsConfig;
			}
		};
	}
}
