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

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 *
 * @author Thomas Bocek
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
