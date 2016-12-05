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

import com.coinblesk.json.v1.UserAccountStatusTO;
import com.coinblesk.json.v1.UserAccountTO;
import com.coinblesk.server.entity.UserAccount;
import com.coinblesk.server.service.UserAccountService;
import com.coinblesk.util.BitcoinUtils;
import com.coinblesk.util.Pair;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.regex.Pattern;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.header.writers.frameoptions.XFrameOptionsHeaderWriter;
import org.springframework.security.web.util.matcher.RequestMatcher;
/**
 *
 * @author Thomas Bocek
 * @author Andreas Albrecht
 */
@EnableWebSecurity
public class SecurityConfig extends WebSecurityConfigurerAdapter {

    @Autowired
    private UserAccountService userAccountService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    final private static String[] REQUIRE_USER_ROLE = {
        "/user/a/**",
        "/user/auth/**",
        "/u/auth/**",
        "/u/a/**",
        "/v?/user/a/**",
        "/v?/user/auth/**",
        "/v?/u/auth/**",
        "/v?/u/a/**" };
    final private static String[] REQUIRE_ADMIN_ROLE = {
        "/admin/**",
        "/a/**",
        "/v?/admin/**",
        "/v?/a/**"};

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http
                // Cross-site request forgery protection not needed when using JWT
                .csrf().disable()

                // Protected URLs
                .authorizeRequests()
                    .antMatchers("/").permitAll()
                    .antMatchers(REQUIRE_ADMIN_ROLE).hasRole(UserRole.ADMIN.getRole())
                    .antMatchers(REQUIRE_USER_ROLE).hasRole(UserRole.USER.getRole())

                // Add default spring login-page at /login
                .and()
                .formLogin()

                // Return 200 instead of the default 301 for a successful login
                .successHandler(new SimpleUrlAuthenticationSuccessHandler(){
                    @Override
                    public void onAuthenticationSuccess(HttpServletRequest request,
                                                        HttpServletResponse response, Authentication authentication)
                            throws IOException, ServletException {
                        clearAuthenticationAttributes(request);
                    }
                })

                // Return 401 instead of the default 302 for a failed login
                .failureHandler((request, response, exception) -> response.sendError(HttpServletResponse.SC_UNAUTHORIZED,
                        "Authentication Failed: " + exception.getMessage())) // return 401 instead 302


                // Allow iframes from same origin (to enable h2-console)
                .and()
                .headers().frameOptions().sameOrigin();

    }

    @Override
    @Bean
    protected AuthenticationManager authenticationManager() throws Exception {
        return (final Authentication authentication) -> {
            final String email = authentication.getPrincipal().toString();
            final String password = authentication.getCredentials().toString();

            final UserAccount userAccount = userAccountService.getByEmail(email);
            if (userAccount == null) {
                throw new BadCredentialsException("Wrong username/password");
            }
            if (userAccount.getEmailToken() != null) {
                throw new AuthenticationServiceException("Email is not verified yet");
            }
            if (userAccount.isDeleted()) {
                throw new AuthenticationServiceException("Account not active");
            }
            if (!passwordEncoder.matches(password, userAccount.getPassword())) {
                throw new BadCredentialsException("Wrong username/password");
            }
            Collection<UserRole> roles = new ArrayList<UserRole>(1);
            roles.add(userAccount.getUserRole());
            return new UsernamePasswordAuthenticationToken(email, password, roles);
        };
    }
}
