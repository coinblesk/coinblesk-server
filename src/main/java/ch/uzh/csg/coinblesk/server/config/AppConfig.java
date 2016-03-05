package ch.uzh.csg.coinblesk.server.config;

import ch.uzh.csg.coinblesk.server.utils.CoinUtils;
import com.coinblesk.bitcoin.BitcoinNet;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;

import com.coinblesk.customserialization.Currency;
import org.bitcoinj.core.NetworkParameters;


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
    
    @Value("${bitcoin.minconf:0}")
    private int minConf;
    
    @Value("${bitcoin.locktime:3}")
    private int lockTime;
    
    @Value("${bitcoin.lockPrecision:10}")
    private int lockPrecision;
    
    public FileSystemResource getConfigDir() {
        //improvement: this check needs to be done only at startup
        if(configDir != null && !configDir.exists()) {
            throw new RuntimeException("The directory "+configDir+" does not exist");
        }
	return configDir;
    }

    public String getBitcoinNet() {
	return bitcoinNet;
    }
    
    public NetworkParameters getNetworkParameters() {
        return CoinUtils.getNetworkParams(BitcoinNet.of(bitcoinNet));
    }
    
    public Currency getCurrency() {
    	return currency;
    }
    
    public int getMinConf() {
        return minConf;
    }
    
    public int lockTime() {
        return lockTime;
    }
    
    public int lockPrecision() {
        return lockPrecision;
    }
}
