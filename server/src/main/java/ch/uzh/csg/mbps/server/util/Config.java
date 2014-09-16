package ch.uzh.csg.mbps.server.util;

import java.math.BigDecimal;

/**
 * Settings for configuring the MBPS Server.
 */
public class Config {
	
	//default Transaction Fee Setting 
	public static final BigDecimal TRANSACTION_FEE = new BigDecimal("0.0001");
	
	//BITSTAMP Controller configuration
	public static final int MIN_CONFIRMATIONS_SMALL_TRANSACTIONS = 6; // recommended: 6
	public static final int MIN_CONFIRMATIONS_BIG_TRANSACTIONS = 12; //recommended: 12
	protected static final double SMALL_TRANSACTION_LIMIT = 0.5;
	
	public static final int TRANSACTIONS_MAX_RESULTS = 50;
	public static final int PAY_INS_MAX_RESULTS = 50;
	public static final int PAY_OUTS_MAX_RESULTS = 50;
	public static final int SEREVER_TRANSACTION_MAX_RESULTS = 50;
	public static final int ACTIVITIES_MAX_RESULTS = 50;
	public static final int MESSAGES_MAX_RESULTS = 50;
	
	//Reset Password Settings
	public static final int DELETE_TOKEN_LIMIT = 86400000; //limit when to delete old tokens in seconds (1 day)
	public static final int VALID_TOKEN_LIMIT = 10 * 60 * 1000; //limit how long token is valid in ms (1 hour)
	
	public static final String REST_CLIENT_ERROR = "Could not establish connection to server! Try again later.";
	public static final String NO_COOKIE_STORED = "No Cookie stored!";

	//Limit for Session Timeout
	public static final int SESSION_TIMEOUT = 10*60; //10 minutes
	
	//Valid Username/Password Settings
	public static final String USERNAME_REGEX = "^[A-Za-z0-9_-]{4,25}$";
	public static final String EMAIL_REGEX = "^[_A-Za-z0-9-\\+]+(\\.[_A-Za-z0-9-]+)*@"
			+ "[A-Za-z0-9-]+(\\.[A-Za-z0-9]+)*(\\.[A-Za-z]{2,})$";
	public static final int MIN_PASSWORD_LENGTH = 4;
	public static final String SPLIT_USERNAME = "@";
	//Valid Url
	public static final String URL_REGEX = "(@)?(href=')?(HREF=')?(HREF=\")?(href=\")?(https?://)?[a-zA-Z_0-9\\-]+(\\.\\w[a-zA-Z_0-9\\-]+)+(/[#&\\n\\-=?\\+\\%/\\.\\w]+)?";
	public static final String URL_NAME_REGEX = "^(http(s)?://)[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|]$";
//	public static final String URL_NAME_REGEX = "/^(https?:\\/\\/)?[a-zA-Z_0-9\\-]+(\\.\\w[a-zA-Z_0-9\\-]+)+(/[#&\\n\\-=?\\+\\%/\\.\\w]+)?$";
	//	public static final String URL_REGEX = "/^(https?:\\/\\/)?([\\da-z\\.-]+)\\.([a-z\\.]{2,6})([\\/\\w \\.-]*)*\\/?$";
//	public static final String IP_REGEX ="/^(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$/";
	// Exchange Rate Provicer Settings
	// 1 for MtGox, 2 for Bitstamp
	public static final int EXCHANGE_RATE_PROVIDER = 1;
	public static final int EXCHANGE_RATE_CACHE_TIME = 10000; //time for which exchange rate is cached in ms
	public static final int EXCHANGE_RATE_UPDATE_TIME = 5; //update time in s
	
	// Multiplier for Bitstamp Trading. Define lower/upper limits for exchange of BTC/USD
	public static final BigDecimal BITSTAMP_SELL_EXCHANGE_RATE_LIMIT = new BigDecimal("0.95");
	public static final BigDecimal BITSTAMP_BUY_EXCHANGE_RATE_LIMIT = new BigDecimal("1.05");
	
	//Limits for the HttpRequestHandler
	public static final int HTTP_CONNECTION_TIMEOUT = 7 * 1000;
	public static final int HTTP_SOCKET_TIMEOUT = 7 * 1000;
	public static final int DEFAULT_MAX_TOTAL_CONNECTIONS = 100;
	public static final int DEFAULT_MAX_CONNECTIONS_PER_ROUTE = 10;
	
	public static final String CANCELLED = "cancelled";
	public static final String FAILED = "failed";

	//Server Account creation
	public static final String ACCOUNT_DELETED = "Account was deleted";
	public static final String ACCOUNT_EXISTS = "Account already exists";
	public static final String ACCOUNT_SUCCESS = "Creation succeeded";
	public static final String ACCOUNT_FAILED = "Account failed";
	public static final String ACCOUNT_UPDATED = "Updated succeeded";
	public static final String SUCCESS = "success";
	public static final Object DOWNGRADE_SUCCEEDED = "Downgrade succeeded";
	
	//url for server communication 
	public static final String DOWNGRADE_TRUST = "/server/communication/downgradeTrustLevel";
	public static final String UPGRADE_TRUST = "/server/communication/upgradeTrustLevel";
	public static final String DELETE_ACCOUNT ="/server/communication/deletedAccount";
	public static final String CREATE_NEW_SERVER = "/server/communication/createNewAccount";
	public static final String CREATE_NEW_SERVER_PUBLIC_KEY = "/server/communication/createNewAccountData";
	public static final String ACCEPT_UPGRADE_TRUST_LEVEL = "/server/communication/upgradeAccepted";
	public static final String DECLINE_UPGRADE_TRUST_LEVEL = "/server/communication/upgradeDeclined";
	
	public static final String NOT_AVAILABLE = "n.A.";

}
