package com.coinblesk.server.service;

import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;

@Service
public class MailService {
	private final static Logger LOG = LoggerFactory.getLogger(MailService.class);

	@Value("${email.enabled}")
	private boolean enabled;

	@Value("${email.host}")
	private String host;

	@Value("${email.protocol}")
	private String protocol;

	@Value("${email.port}")
	private int port;

	@Value("${email.auth}")
	private boolean auth;

	@Value("${email.starttls}")
	private boolean starttls;

	@Value("${email.debug}")
	private boolean debug;

	@Value("${email.trust}")
	private String trust;

	@Value("${email.username}")
	private String username;

	@Value("${email.password}")
	private String password;

	@Value("${email.admin}")
	private String admin;

	@Value("${email.sendfrom}")
	private String sendfrom;

	@Autowired
	private JavaMailSender javaMailService;

	@Bean
	public JavaMailSender javaMailService() {
		JavaMailSenderImpl javaMailSender = new JavaMailSenderImpl();

		if (this.auth) {
			javaMailSender.setUsername(this.username);
			javaMailSender.setPassword(this.password);
		}

		Properties properties = new Properties();
		properties.setProperty("mail.transport.protocol", this.protocol);
		properties.setProperty("mail.smtp.auth", Boolean.toString(this.auth));
		properties.setProperty("mail.smtp.starttls.enable", Boolean.toString(this.starttls));
		properties.setProperty("mail.debug", Boolean.toString(this.debug));
		properties.setProperty("mail.smtp.host", this.host);
		properties.setProperty("mail.smtp.port", Integer.toString(this.port));
		properties.setProperty("mail.smtp.ssl.trust", this.trust);
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
		if (!this.enabled) {
			LOG.info("Skipping sending mail to {} with body: \"{}\"", recipient, text);
			return;
		}

		SimpleMailMessage smm = new SimpleMailMessage();
		smm.setFrom(this.sendfrom);
		smm.setTo(recipient);
		smm.setSubject(subject);
		smm.setText(text);
		try {
			LOG.info("send mail to {}: {}", recipient, smm);
			javaMailService.send(smm);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
