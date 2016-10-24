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
@EnableGlobalMethodSecurity(prePostEnabled = true)
public class SecurityConfig extends WebSecurityConfigurerAdapter {

    @Autowired
    private UserAccountService userAccountService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    final private static String REQUEST_ATTRIBUTE_NAME = "_csrf";
    final private static String RESPONSE_HEADER_NAME = "X-CSRF-HEADER";
    final private static String[] CSRF_PREFIX = {"/web", "/w"};

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

    private static class CsfrHeaderAppendFilter implements Filter {

        @Override
        public void init(FilterConfig fc) throws ServletException {
        }

        @Override
        public void doFilter(ServletRequest request, ServletResponse response, FilterChain filterChain) throws IOException, ServletException {
            if (!(response instanceof HttpServletResponse)) {
                filterChain.doFilter(request, response);
                return;
            }
            // to not use JSP we want the token to be in the HTTP header
            CsrfToken csrfToken = (CsrfToken) request.getAttribute(REQUEST_ATTRIBUTE_NAME);
            if (csrfToken != null) {
                HttpServletResponse res = (HttpServletResponse) response;
                res.setHeader(RESPONSE_HEADER_NAME, csrfToken.getToken());
            }
            filterChain.doFilter(request, response);
        }

        @Override
        public void destroy() {
        }
    }

    private static class CsfrIgnoreRequestMatcher implements RequestMatcher {

        final private Pattern allowedMethods = Pattern.compile("^(GET|HEAD|TRACE|OPTIONS)$");

        @Override
        public boolean matches(HttpServletRequest request) {
            // No CSRF due to allowedMethod
            if (allowedMethods.matcher(request.getMethod()).matches()) {
                return false;
            }
            // http://stackoverflow.com/questions/4931323/whats-the-difference-between-getrequesturi-and-getpathinfo-methods-in-httpservl
            String url = request.getServletPath();
            for (int i = 0; i < CSRF_PREFIX.length; i++) {
                if (url.startsWith(CSRF_PREFIX[i])) {
                    return true;
                }
            }
            return false;
        }
    }

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http
                .csrf()
                .requireCsrfProtectionMatcher(new CsfrIgnoreRequestMatcher())
                .and()
                .authorizeRequests()
                    .antMatchers("/").permitAll()
                    .antMatchers(REQUIRE_ADMIN_ROLE).hasRole(UserRole.ADMIN.getRole())
                    .antMatchers(REQUIRE_USER_ROLE).hasRole(UserRole.USER.getRole())
                .and()
                .formLogin()
                .successHandler(new SimpleUrlAuthenticationSuccessHandler(){
                    @Override
                    public void onAuthenticationSuccess(HttpServletRequest request,
			HttpServletResponse response, Authentication authentication)
			throws IOException, ServletException {
                            clearAuthenticationAttributes(request);
                        }

                }) // return 200 instead 301
                .failureHandler(new AuthenticationFailureHandler(){
            @Override
            public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                    AuthenticationException exception) throws IOException, ServletException {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED,
					"Authentication Failed: " + exception.getMessage());
            }
        }) // return 401 instead 302
                .and()
                .addFilterAfter(new CsfrHeaderAppendFilter(), CsrfFilter.class)

                // Allow iframes from same origin (to enable h2-console)
                .headers().frameOptions().sameOrigin();

    }

    @Override
    @Bean
    protected AuthenticationManager authenticationManager() throws Exception {
        return (final Authentication authentication) -> {
            final String email = authentication.getPrincipal().toString();
            final String password = authentication.getCredentials().toString();

            /*
            TODO: Move this into tests
            if (databaseConfig.isTest()) {
                UserAccountTO userAccount = new UserAccountTO()
                        .email("a")
                        .password("a")
                        .balance(BitcoinUtils.ONE_BITCOIN_IN_SATOSHI);
                Pair<UserAccountStatusTO, UserAccount> res = userAccountService.createEntity(userAccount);
                userAccountService.activate("a", res.element1().getEmailToken());
            }
            */

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
