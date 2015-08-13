package ch.uzh.csg.coinblesk.server.service;

import java.math.BigDecimal;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.AnnotationConfigWebContextLoader;
import org.springframework.test.context.web.WebAppConfiguration;

import ch.uzh.csg.coinblesk.server.config.DispatcherConfig;
import ch.uzh.csg.coinblesk.server.config.WebAppInitializer;



@RunWith(SpringJUnit4ClassRunner.class)
@WebAppConfiguration
@ContextConfiguration(classes={DispatcherConfig.class})
public class ForexExchangeRateServiceTest {
	
	static {
		System.getProperties().setProperty("db.url", "test");
		System.getProperties().setProperty("db.username", "test");
		System.getProperties().setProperty("db.password", "test");
	}
	
	@Autowired
    private ForexExchangeRateService forexExchangeRateService;
	
	@Test
	public void testForex() throws Exception {
		BigDecimal d = forexExchangeRateService.getExchangeRate("USD");
		Assert.assertNotNull(d);
		System.out.println("rate is: "+d);
	}
	
}
