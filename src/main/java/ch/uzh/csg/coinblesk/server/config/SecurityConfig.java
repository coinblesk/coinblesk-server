/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.uzh.csg.coinblesk.server.config;

import ch.uzh.csg.coinblesk.server.entity.UserAccount;
import ch.uzh.csg.coinblesk.server.service.UserAccountService;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 *
 * @author Thomas Bocek
 */
@EnableWebSecurity
@EnableGlobalMethodSecurity(prePostEnabled = true)
public class SecurityConfig extends WebSecurityConfigurerAdapter {

    final private static List<GrantedAuthority> LIST = new ArrayList<GrantedAuthority>(1);
               
    static {
         LIST.add(new UserRole());
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
        http.authorizeRequests().antMatchers("/user/**", "/u/**").hasRole("USER")
                .and().formLogin().loginPage("/login");
    }

    @Autowired
    public void configureGlobal(AuthenticationManagerBuilder auth) throws Exception {
        auth
                .inMemoryAuthentication()
                .withUser("user").password("password").roles("USER");
    }

    @Override
    protected AuthenticationManager authenticationManager() throws Exception {
        return (final Authentication authentication) -> {
            final String username = authentication.getPrincipal().toString();
            final String password = authentication.getCredentials().toString();
            
            final UserAccount userAccount = userAccountService.getByUsername(username);
            if (userAccount.getEmailToken()!=null) {
                throw new AuthenticationServiceException("Email is not verified yet");
            }
            if (!passwordEncoder.matches(password, userAccount.getPassword())) {
                throw new BadCredentialsException("Wrong username/password");
            }
            return new UsernamePasswordAuthenticationToken(username, password, LIST);
        };
    }
}
