package ch.uzh.csg.mbps.server.util;

/**
 * DO NOT COMMIT CHANGES TO THIS FILE!
 * Sensitive settings for the MBPS server.
 */
public class SecurityConfig {
	
	//TODO: test test
	
	//Email Configuration
	protected static final String FROM = "bitcoin-no-reply@ifi.uzh.ch";
	protected static final String EMAIL_USER = "mbps-notification";
	protected static final String EMAIL_PASSWORD = "***";
	protected static final String BASE_URL = "http://bitcoin-clone.csg.uzh.ch/server";

	//Bitcoin Controller Configuration
	public static final String ACCOUNT = "bitcoinrpc";
	public static final String RPC_USER = "bitcoinrpc";
	public static final String BITCOIND_PASSWORD = "***";
	public static final String HOST = "localhost";

	//BITSTAMP Controller configuration
	public static final String BITSTAMP_USERNAME = "****";
	public static final String BITSTAMP_APIKEY = "****";
	public static final String BITSTAMP_SECRETKEY = "****";
	
	public static final String PORT = "18332";//18332 for testnet, 8332 for production
	public static final String ENCRYPTION_PASSWORD = "***";
	public static final String BACKUP_DESTINATION = "/home/simon/walletBackup/walletBackup";

}
