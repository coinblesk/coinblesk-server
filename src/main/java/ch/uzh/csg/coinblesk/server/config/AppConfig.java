package ch.uzh.csg.coinblesk.server.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;

import ch.uzh.csg.coinblesk.Currency;


@Configuration
public class AppConfig {
	
	@Value("${coinblesk.config.dir:/var/lib/coinblesk}")
    private FileSystemResource configDir;
	
	@Value("${bitcoin.net:unittest}")
    private String bitcoinNet;

    @Value("${exchangerates.currency:CHF}")
    private Currency currency;
    
    public FileSystemResource getConfigDir() {
		return configDir;
	}

	public String getBitcoinNet() {
		return bitcoinNet;
	}
    
    public Currency getCurrency() {
    	return currency;
    }
}
