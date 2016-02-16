/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.uzh.csg.coinblesk.server.config;

import ch.uzh.csg.coinblesk.server.entity.UserAccount;
import ch.uzh.csg.coinblesk.server.service.UserAccountService;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
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
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 *
 * @author Thomas Bocek
 */
@EnableWebSecurity
@EnableGlobalMethodSecurity(prePostEnabled = true)
public class SecurityConfig extends WebSecurityConfigurerAdapter {

    final private static List<GrantedAuthority> LIST = new ArrayList<>(1);
    final private static String REQUEST_ATTRIBUTE_NAME = "_csrf";
    final private static String RESPONSE_HEADER_NAME = "X-CSRF-HEADER";
    final private static String[] REQUIRE_USER_ROLE = {"/user/a/**", "user/auth/**", "u/auth/**", "/u/a/**"};
               
    static {
         LIST.add(new UserRole());
    }
    
    private static class CsfrHeaderAppendFilter implements Filter {
        @Override public void init(FilterConfig fc) throws ServletException {}

                @Override
                public void doFilter(ServletRequest request, ServletResponse response, FilterChain filterChain) throws IOException, ServletException {
                    if(!(response instanceof HttpServletResponse)) {
                        filterChain.doFilter(request, response);
			return;
                    }
                    //to not use JSP we want the token to be in the HTTP header
                    CsrfToken csrfToken = (CsrfToken) request.getAttribute(REQUEST_ATTRIBUTE_NAME);
                    if(csrfToken != null) {
                        HttpServletResponse res = (HttpServletResponse) response;
                        res.setHeader(RESPONSE_HEADER_NAME, csrfToken.getToken());
                    }
                    filterChain.doFilter(request, response);
                }

                @Override public void destroy() {}
    }
    
    private static class CsfrIgnoreRequestMatcher implements RequestMatcher {
        final private Pattern allowedMethods = Pattern.compile("^(GET|HEAD|TRACE|OPTIONS)$");

        @Override
        public boolean matches(HttpServletRequest request) {
            // No CSRF due to allowedMethod
            if(allowedMethods.matcher(request.getMethod()).matches()) {
                return false;
            }
            //http://stackoverflow.com/questions/4931323/whats-the-difference-between-getrequesturi-and-getpathinfo-methods-in-httpservl
            String url = request.getServletPath();
            if(startsWith(url, REQUIRE_USER_ROLE)) {
                return true;
            }
            return false;
        }
        
    }
    
    private static boolean startsWith(String text, String... needles) {
        for(String needle:needles) {
            //remove trailing * as we handle startswith
            while(needle.endsWith("*") && !needle.isEmpty()) {
                needle = needle.substring(0, needle.length() - 1);
            }
            if(needle.isEmpty()) {
                return true;
            }
            if(needle.contains("*")) {
                throw new RuntimeException("only trailing * can be handled");
            }
            if(text.startsWith(needle)) {
                return true;
            }
        }
        return false;
    }
    
    final private static class UserRole implements GrantedAuthority {
        @Override
        public String getAuthority() {
            return "ROLE_USER";
        }
    }
        
    @Autowired
    private UserAccountService userAccountService;
    
    @Autowired
    private  PasswordEncoder passwordEncoder;

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http
            .csrf()
                .requireCsrfProtectionMatcher(new CsfrIgnoreRequestMatcher())
                .and()
            .authorizeRequests()
                .antMatchers(REQUIRE_USER_ROLE).hasRole("USER")
            //TODO: this below requires JSP, find a way to have HTML only (if possible)
                .and()
            .formLogin()
                .loginPage("/login")
                .defaultSuccessUrl("/login?success")
                .and()
            .addFilterAfter(new CsfrHeaderAppendFilter(), CsrfFilter.class);
        
    }

    @Override
    @Bean
    protected AuthenticationManager authenticationManager() throws Exception {
        return (final Authentication authentication) -> {
            final String email = authentication.getPrincipal().toString();
            final String password = authentication.getCredentials().toString();
            
            /*if(true) {
                return new UsernamePasswordAuthenticationToken(email, password, LIST);
            }*/
            
            final UserAccount userAccount = userAccountService.getByEmail(email);
            if (userAccount == null) {
                throw new BadCredentialsException("Wrong username/password");
            }
            if (userAccount.getEmailToken()!=null) {
                throw new AuthenticationServiceException("Email is not verified yet");
            }
            if (!passwordEncoder.matches(password, userAccount.getPassword())) {
                throw new BadCredentialsException("Wrong username/password");
            }
            return new UsernamePasswordAuthenticationToken(email, password, LIST);
        };
    }
}
