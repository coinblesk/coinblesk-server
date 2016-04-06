package ch.uzh.csg.coinblesk.server.config;

import ch.uzh.csg.coinblesk.server.utils.ApiVersionRequestMappingHandlerMapping;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import ch.uzh.csg.coinblesk.server.service.ForexExchangeRateService;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import javax.mail.PasswordAuthentication;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.JavaMailSender;
import javax.mail.Session;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

@Configuration
@ComponentScan("ch.uzh.csg.coinblesk.server")
@EnableScheduling

public class BeanConfig
{	
    @Autowired 
    private MailConfig mailConfig;    

    //used to resolve @Value
    @Bean
    public static PropertySourcesPlaceholderConfigurer propertySourcesPlaceholderConfigurer() {
       return new PropertySourcesPlaceholderConfigurer();
    }
    
    @Bean
    public static ForexExchangeRateService.ForexTask forexTask() {
    	return new ForexExchangeRateService.ForexTask();
    }
    
    @Bean
    public static TaskScheduler taskScheduler() {
    	return new ThreadPoolTaskScheduler();
    }
    
    @Bean
    public static PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
    
    @Bean
    public RequestMappingHandlerMapping requestMappingHandlerMapping() {
        return new ApiVersionRequestMappingHandlerMapping();
    }
    
    //as seen in: http://stackoverflow.com/questions/22483407/send-emails-with-spring-by-using-java-annotations
    @Bean
    public JavaMailSender javaMailService() { 
        JavaMailSenderImpl javaMailSender = new JavaMailSenderImpl();
        if(mailConfig.isAuth()) {
            Session session = Session.getInstance(getMailProperties(), new javax.mail.Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(mailConfig.getUsername(), mailConfig.getPassword());
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
        properties.setProperty("mail.smtp.auth", Boolean.toString(mailConfig.isAuth()));
        properties.setProperty("mail.smtp.starttls.enable", Boolean.toString(mailConfig.isStartTLS()));
        properties.setProperty("mail.debug", Boolean.toString(mailConfig.isDebug()));
        properties.setProperty("mail.smtp.host", mailConfig.getHost());
	properties.setProperty("mail.smtp.port", Integer.toString(mailConfig.getPort()));
        if(mailConfig.getTrust() != null) {
            properties.setProperty("mail.smtp.ssl.trust", mailConfig.getTrust() );
        }
        return properties;
    }
    
    @Bean
    public AdminEmail adminEmail() {
        final AtomicInteger sent = new AtomicInteger(0);
        return new AdminEmail() {
            @Autowired 
            JavaMailSender javaMailService;
            
            @Override
            public void send(String subject, String text) {
                SimpleMailMessage smm = new SimpleMailMessage();
                smm.setFrom("bitcoin@csg.uzh.ch");
                smm.setTo(mailConfig.getAdmin());
                smm.setSubject(subject);
                smm.setText(text);
                sent.incrementAndGet();
                try {
                    javaMailService.send(smm);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            @Override
            public int sentEmails() {
                return sent.get();
            }
        };   
    }
}
