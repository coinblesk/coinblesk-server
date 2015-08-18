package ch.uzh.csg.coinblesk.server.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;

import ch.uzh.csg.coinblesk.Currency;


/**
 * This is the default configuration for testcases. If you want to change these
 * settings e.g. when using tomcat, add these values to context.xml:
 * 
 * <pre>
 *  ...
 *  <Parameter name="bitcoin.net" value="testnet" />
 *  ...
 * </pre>
 * 
 * @author Raphael Voellmy
 * @author Thomas Bocek
 */
@Configuration
public class AppConfig {
	
	@Value("${coinblesk.config.dir:/var/lib/coinblesk}")
    private FileSystemResource configDir;
	
	@Value("${bitcoin.net:unittest}")
    private String bitcoinNet;

    @Value("${exchangerates.currency:CHF}")
    private Currency currency;
    
    @Value("${bitcoin.minconf:4}")
    private int minConf;
    
    public FileSystemResource getConfigDir() {
		return configDir;
	}

	public String getBitcoinNet() {
		return bitcoinNet;
	}
    
    public Currency getCurrency() {
    	return currency;
    }
    
    public int getMinConf() {
        return minConf;
    }
    
    
}
