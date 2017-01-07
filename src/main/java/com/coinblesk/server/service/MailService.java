package com.coinblesk.server.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;

import java.util.Properties;

@Service
public class MailService {
    private final static Logger LOG = LoggerFactory.getLogger(MailService.class);

    @Value("${email.host:localhost}")
    public String host;

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

    @Value("${email.sendfrom:bitcoin@csg.uzh.ch}")
    private String sendfrom;

    @Autowired
    private JavaMailSender javaMailService;

    @Bean
    public JavaMailSender javaMailService() {
        JavaMailSenderImpl javaMailSender = new JavaMailSenderImpl();

        if(this.auth) {
            javaMailSender.setUsername(this.username);
            javaMailSender.setPassword(this.password);
        }

        Properties properties = new Properties();
        properties.setProperty("mail.transport.protocol", "smtp");
        properties.setProperty("mail.smtp.auth", Boolean.toString(this.auth));
        properties.setProperty("mail.smtp.starttls.enable", Boolean.toString(this.starttls));
        properties.setProperty("mail.debug", Boolean.toString(this.debug));
        properties.setProperty("mail.smtp.host", this.host);
        properties.setProperty("mail.smtp.port", Integer.toString(this.port));
        if(this.trust != null) {
            properties.setProperty("mail.smtp.ssl.trust", this.trust );
        }
        javaMailSender.setJavaMailProperties(properties);

        return javaMailSender;
    }

    public void sendUserMail(String recipient, String subject, String text) {
        sendMail(recipient, subject, text);
    }

    public void sendAdminMail(String subject, String text) {
        sendMail(this.admin, subject, text);
    }

    private void sendMail(String recipient, String subject, String text) {
        SimpleMailMessage smm = new SimpleMailMessage();
        smm.setFrom(this.sendfrom);
        smm.setTo(recipient);
        smm.setSubject(subject);
        smm.setText(text);
        try {
            LOG.debug("send mail to {}: {}", recipient, smm);
            javaMailService.send(smm);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
