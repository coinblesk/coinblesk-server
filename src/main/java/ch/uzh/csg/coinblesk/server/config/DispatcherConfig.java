package ch.uzh.csg.coinblesk.server.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import ch.uzh.csg.coinblesk.server.service.ForexExchangeRateService;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan("ch.uzh.csg.coinblesk.server")
@EnableScheduling
public class DispatcherConfig
{	
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
}
