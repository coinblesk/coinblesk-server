package ch.uzh.csg.mbps.server.util;

import java.math.BigDecimal;

/**
 * Config Class with multiple static settings for configuring MBPS Server.
 *
 */
public class Config {
	
	//default Transaction Fee Setting 
	public static final BigDecimal TRANSACTION_FEE = new BigDecimal("0.0001");
	
	//Email Configuration
	protected static final String FROM = "****";
	protected static final String EMAIL_USER = "****";
	protected static final String EMAIL_PASSWORD = "****";
	protected static final String BASE_URL = "****";

	//Bitcoin Controller Configuration
	
	public static final String ACCOUNT = "****";
	public static final String RPC_USER = "****";
	public static final String BITCOIND_PASSWORD = "****";
	public static final String HOST = "localhost";

	//BITSTAMP Controller configuration
	public static final String BITSTAMP_USERNAME = "****";
	public static final String BITSTAMP_APIKEY = "****";
	public static final String BITSTAMP_SECRETKEY = "****";
	
	public static final String PORT = "8332";//18332 for testnet, 8332 for production
	public static final String ENCRYPTION_PASSWORD = "****";
	public static final String BACKUP_DESTINATION = "walletBackup";
	
	public static final int MIN_CONFIRMATIONS_SMALL_TRANSACTIONS = 6; // recommended: 6
	public static final int MIN_CONFIRMATIONS_BIG_TRANSACTIONS = 12; //recommended: 12
	protected static final double SMALL_TRANSACTION_LIMIT = 0.5;
	
	public static final int TRANSACTIONS_MAX_RESULTS = 50;
	public static final int PAY_INS_MAX_RESULTS = 50;
	public static final int PAY_OUTS_MAX_RESULTS = 50;
	
	
	//Reset Password Settings
	public static final int DELETE_TOKEN_LIMIT = 86400000; //limit when to delete old tokens in seconds (1 day)
	public static final int VALID_TOKEN_LIMIT = 10 * 60 * 1000; //limit how long token is valid in ms (1 hour)

	//Limit for Session Timeout
	public static final int SESSION_TIMEOUT = 10*60; //10 minutes
	
	
	//Valid Username/Password Settings
	public static final String USERNAME_REGEX = "^[A-Za-z0-9_-]{4,25}$";
	public static final String EMAIL_REGEX = "^[_A-Za-z0-9-\\+]+(\\.[_A-Za-z0-9-]+)*@"
			+ "[A-Za-z0-9-]+(\\.[A-Za-z0-9]+)*(\\.[A-Za-z]{2,})$";
	public static final int MIN_PASSWORD_LENGTH = 4;
	
	//Exchange Rate Provicer Settings
	// 1 for MtGox, 2 for Bitstamp
	public static final int EXCHANGE_RATE_PROVIDER = 2;
	public static final int EXCHANGE_RATE_CACHE_TIME = 10000; //time for which exchange rate is cached in ms
	public static final int EXCHANGE_RATE_UPDATE_TIME = 5; //update time in s
	
	//Multiplier for Bitstamp Trading. Define lower/upper limits for exchange of BTC/USD
	public static final BigDecimal BITSTAMP_SELL_EXCHANGE_RATE_LIMIT = new BigDecimal("0.95");
	public static final BigDecimal BITSTAMP_BUY_EXCHANGE_RATE_LIMIT = new BigDecimal("1.05");

}
