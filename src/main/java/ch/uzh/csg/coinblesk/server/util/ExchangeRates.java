package ch.uzh.csg.coinblesk.server.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Date;

import net.minidev.json.JSONObject;
import net.minidev.json.parser.JSONParser;
import net.minidev.json.parser.ParseException;

/**
 * Class for returning exchange rates from Bitcoin exchange platforms.
 *
 */
public class ExchangeRates {
	private final static String USER_AGENT = "Mozilla/5.0";
	private final static String MTGOX_URL = "http://data.mtgox.com/api/2/BTCCHF/money/ticker_fast";
	private final static String BITSTAMP_URL = "https://www.bitstamp.net/api/ticker/";
	private final static String USD_CHF_URL = "http://query.yahooapis.com/v1/public/yql?q=select%20*%20from%20yahoo.finance.xchange%20where%20pair%20in%20%28%22USDCHF%22%29&format=json&diagnostics=true&env=store%3A%2F%2Fdatatables.org%2Falltableswithkeys&";
	private static BigDecimal exchangeRate = BigDecimal.ZERO;
	private static Date exchangeRateTimestamp = new Date(0);
	private static BigDecimal exchangeRateUsdChf = BigDecimal.ZERO;
	
	/**
	 * Constructor for exchangerates. Initializes exchangeRate, exchangeRateUsdChf and exchangerateTimestamp.
	 */
	public ExchangeRates() {
		exchangeRateTimestamp = new Date();
		exchangeRate = BigDecimal.ZERO;
		exchangeRateUsdChf = new BigDecimal("0.9");
	}
	
	/**
	 * Returns the exchange rate for BTC/CHF based on
	 * Config.EXCHANGE_RATE_PROVIDER. Exchange rates are cached for 5seconds
	 * (can be defined in Config.java). If exchange rate is not available from
	 * one provider (e.g. MtGox.com) it automatically tries to get the exchange rate from the other one (e.g. Bitstamp.net). If both
	 * requests fail, the method throws an exception.
	 * 
	 * @return exchangeRate
	 * @throws ParseException
	 * @throws IOException
	 */
	public static BigDecimal getExchangeRate() throws ParseException, IOException{
		Date currentDate = new Date();
		long currentDateTimeCacheLimit = currentDate.getTime()- Config.EXCHANGE_RATE_CACHE_TIME;
		long exchangeRateTimestampTime = exchangeRateTimestamp.getTime();
		
		if (exchangeRate.equals(BigDecimal.ZERO)){
			exchangeRate =  getCurrentExchangeRate();
			exchangeRateTimestamp = currentDate;
			return exchangeRate;
		}
		
		if (exchangeRateTimestampTime >= currentDateTimeCacheLimit) {
			return exchangeRate;
		} else {
			exchangeRate =  getCurrentExchangeRate();
			exchangeRateTimestamp = currentDate;
			return exchangeRate;
		}
	}
	
	/**
	 * Updates the exchange rate for BTC/CHF based on
	 * Config.EXCHANGE_RATE_PROVIDER. Exchange rates are updated every 5seconds
	 * (can be defined in Config.java). If exchange rate is not available from
	 * one provider (e.g. MtGox.com) it automatically tries to get the exchange rate from the other one (e.g. Bitstamp.net). 
	 * @throws IOException 
	 * @throws ParseException 
	 */
	protected static void update() throws ParseException, IOException{
		Date currentDate = new Date();
		BigDecimal tempRate = getCurrentExchangeRate();
		exchangeRate = tempRate;
		exchangeRateTimestamp = currentDate;
	}
	
	/**
	 * Returns current exchange rate based on preferences defined in
	 * Config.java. If exchange rate of prefered supplier is not available
	 * method tries to get the exchange rate of the second exchange rate
	 * provider. Returns exception if no exchange rate can be returned.
	 * 
	 * @return
	 * @throws ParseException
	 * @throws IOException
	 */
	private static BigDecimal getCurrentExchangeRate() throws ParseException, IOException {
		switch (Config.EXCHANGE_RATE_PROVIDER) {
		case 1:
			try {
				return getExchangeRateMtGox();
			} catch (ParseException | IOException e) {
				return getExchangeRateBitstamp();
			}
		case 2:
			try {
				return getExchangeRateBitstamp();
			} catch (ParseException | IOException e) {
				return getExchangeRateMtGox();
			}
		default:
			return getExchangeRateMtGox();
		}
	}
	
	/**
	 * Returns exchangeRate from Bitstamp.net in BTC/CHF
	 * 
	 * @return exchangeRate BTC/CHF
	 * @throws ParseException
	 * @throws IOException
	 */
	private static BigDecimal getExchangeRateBitstamp() throws ParseException, IOException {
		StringBuffer response = doHttpRequest(BITSTAMP_URL);

		// gets actual exchange rate out of Json Object and saves it to last.
		//JSONParser.MODE_JSON_SIMPLE
		@SuppressWarnings("deprecation")
		JSONParser parser = new JSONParser();
		BigDecimal exchangeRate = BigDecimal.ZERO;
		JSONObject httpAnswerJson;
		httpAnswerJson = (JSONObject) (parser.parse(response.toString()));
		String last_String = (String) httpAnswerJson.get("last");
		exchangeRate = new BigDecimal(last_String);

		return convertBtcChf(exchangeRate);
	}

	/**
	 * Returns ExchangeRate (passed in USD Dollar) converted in Swiss Francs.
	 * 
	 * @param btcUsd
	 * @return BigDecimal with bitcoin exchangerate converted from us dollars to
	 *         swiss francs
	 * @throws IOException
	 * @throws ParseException
	 */
	private static BigDecimal convertBtcChf(BigDecimal btcUsd) throws IOException, ParseException{
		return btcUsd.multiply(exchangeRateUsdChf);
	}

	/**
	 * Returns up to date exchangeRate from MtGox.com int BTC/CHF
	 * 
	 * @return double exchangeRate BTC/CHF
	 * @throws ParseException 
	 * @throws IOException 
	 */
	private static BigDecimal getExchangeRateMtGox() throws ParseException, IOException {
		StringBuffer response = doHttpRequest(MTGOX_URL);
		//JSONParser.MODE_JSON_SIMPLE
		@SuppressWarnings("deprecation")
		JSONParser parser = new JSONParser();
		BigDecimal exchangeRate = BigDecimal.ZERO;
		JSONObject httpAnswerJson;
		httpAnswerJson = (JSONObject) (parser.parse(response.toString()));
		JSONObject dataJson = (JSONObject) httpAnswerJson.get("data");
		JSONObject lastJson = (JSONObject) dataJson.get("last");
		String last_String = (String) lastJson.get("value");
		exchangeRate = new BigDecimal(last_String);

		return exchangeRate;
	}

	/**
	 * Updates ExchangeRate USD/CHF
	 * 
	 * @throws ParseException
	 * @throws IOException
	 */
	public static void updateExchangeRateUsdChf() throws ParseException, IOException {
		StringBuffer response = doHttpRequest(USD_CHF_URL);
		// gets actual exchange rate out of Json Object and saves it to last.
		//JSONParser.MODE_JSON_SIMPLE
		@SuppressWarnings("deprecation")
		JSONParser parser = new JSONParser();
		BigDecimal exchangeRate = BigDecimal.ZERO;

		JSONObject httpAnswerJson;
		httpAnswerJson = (JSONObject) (parser.parse(response.toString()));
		JSONObject query = (JSONObject) httpAnswerJson.get("query");
		JSONObject results = (JSONObject) query.get("results");
		JSONObject rate = (JSONObject) results.get("rate");
		String rateString = (String) rate.get("Rate");
		exchangeRate = new BigDecimal(rateString);
		
		exchangeRateUsdChf = exchangeRate;
	}
	
	/**
	 * Executes JSON HTTP Request and returns result.
	 * 
	 * @param url
	 * @return response of defined by url request
	 * @throws IOException
	 */
	private static StringBuffer doHttpRequest(String url) throws IOException{
		URL obj = new URL(url);
		HttpURLConnection con = (HttpURLConnection) obj.openConnection();
		
		// optional default is GET
		con.setRequestMethod("GET");
		
		// add request header
		con.setRequestProperty("User-Agent", USER_AGENT);
		
		BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
		String inputLine;
		StringBuffer response = new StringBuffer();
		
		while ((inputLine = in.readLine()) != null) {
			response.append(inputLine);
		}
		in.close();
		return response;
	}
}
