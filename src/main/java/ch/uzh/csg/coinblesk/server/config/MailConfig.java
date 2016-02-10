/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.uzh.csg.coinblesk.server.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 *
 * @author draft
 */
@Configuration
public class MailConfig {
    
    @Value("${email.host:localhost}")
    private String host;

    @Value("${email.port:25}")
    private int port;
    
    @Value("${email.auth:false}")
    private boolean auth;
    
    @Value("${email.starttls:false}")
    private boolean starttls;
    
    @Value("${email.debug:false}")
    private boolean debug;
    
    @Value("${email.trust:}")
    private String trust;
    
    @Value("${email.username:}")
    private String username;
    
    @Value("${email.password:}")
    private String password;
    
    @Value("${email.admin:bocek@ifi.uzh.ch}")
    private String admin;
    
    public String getHost() {
	return host;
    }
    
    public int getPort() {
        return port;
    }
    
    public boolean isAuth() {
        return auth;
    }
    
    public boolean isStartTLS() {
        return starttls;
    }
    
    public boolean isDebug() {
        return debug;
    }
    
    public String getTrust() {
	return trust;
    }
    
    public String getUsername() {
	return username;
    }
    
    public String getPassword() {
	return password;
    }
    
    public String getAdmin() {
	return admin;
    }
}
