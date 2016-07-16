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

import com.coinblesk.server.controller.UserController;
import com.coinblesk.util.Pair;
import java.util.LinkedList;
import java.util.Properties;
import java.util.Queue;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/**
 *
 * @author Thomas Bocek
 */
@Configuration
public class MailConfig {
    
    private final static Logger LOG = LoggerFactory.getLogger(MailConfig.class);
    
    private final static String UNIT_TEST = "unittest";
    private final Queue<Pair<String,String>> emailQueue = new LinkedList<>();
    
    @Value("${email.host:"+UNIT_TEST+"}")
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
    
    @Autowired
    private JavaMailSender javaMailService;
    
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
    
    @Bean 
    public UserEmail userEmail() {
        return new UserEmail() {
            @Override
            public void send(String recipient, String subject, String text) {
                if(getHost().equals(UNIT_TEST)) {
                    LOG.debug("not sending user email, we are unit testing");
                    emailQueue.add(new Pair<>(subject,text));
                } else {
                    SimpleMailMessage smm = new SimpleMailMessage();
                    smm.setFrom("bitcoin@csg.uzh.ch");
                    smm.setTo(recipient);
                    smm.setSubject(subject);
                    smm.setText(text);
                    try {
                        LOG.debug("send user mail {}",smm);
                        javaMailService.send(smm);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                
            }

            @Override
            public int sentEmails() {
                return emailQueue.size();
            }
        };
    }
    
    @Bean
    public AdminEmail adminEmail() {
        return new AdminEmail() {   
            @Override
            public void send(String subject, String text) {
                if(getHost().equals(UNIT_TEST)) {
                    LOG.debug("not sending admin email, we are unit testing");
                    emailQueue.add(new Pair<>(subject,text));
                } else {
                    SimpleMailMessage smm = new SimpleMailMessage();
                    smm.setFrom("bitcoin@csg.uzh.ch");
                    smm.setTo(getAdmin());
                    smm.setSubject(subject);
                    smm.setText(text);
                    try {
                        LOG.debug("send admin mail {}",smm);
                        javaMailService.send(smm);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }

            @Override
            public int sentEmails() {
                return emailQueue.size();
            }
        };   
    }
    
    //as seen in: http://stackoverflow.com/questions/22483407/send-emails-with-spring-by-using-java-annotations
    @Bean
    public JavaMailSender javaMailService() { 
        JavaMailSenderImpl javaMailSender = new JavaMailSenderImpl();
        if(isAuth()) {
            Session session = Session.getInstance(getMailProperties(), new javax.mail.Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(getUsername(), getPassword());
                }
            });
            javaMailSender.setSession(session);
        } else {
            javaMailSender.setJavaMailProperties(getMailProperties());
        }
        return javaMailSender;
    }
    
    private Properties getMailProperties() {
        Properties properties = new Properties();
        properties.setProperty("mail.transport.protocol", "smtp");
        properties.setProperty("mail.smtp.auth", Boolean.toString(isAuth()));
        properties.setProperty("mail.smtp.starttls.enable", Boolean.toString(isStartTLS()));
        properties.setProperty("mail.debug", Boolean.toString(isDebug()));
        properties.setProperty("mail.smtp.host", getHost());
	properties.setProperty("mail.smtp.port", Integer.toString(getPort()));
        if(getTrust() != null) {
            properties.setProperty("mail.smtp.ssl.trust", getTrust() );
        }
        return properties;
    }
}
