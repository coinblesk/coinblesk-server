package ch.uzh.csg.coinblesk.server.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.gson.Gson;

import ch.uzh.csg.coinblesk.customserialization.Currency;
import ch.uzh.csg.coinblesk.server.config.AppConfig;

/**
 * 
 * Service that provides bitcoin and forex exchange rates. This class also
 * caches exchange rates.
 * 
 * @author rvoellmy
 * @author Thomas Bocek
 *
 */

@Service
final public class ForexExchangeRateService {

	@Autowired
	private AppConfig appConfig;
	
	//15min
	private final static int cachingTimeMillis = 900 * 1000; 

	private final static String USER_AGENT = "Mozilla/5.0";
	private final static String PLACEHOLDER = "{{PLACEHOLDER}}";
	private final static String YAHOO_API = "http://query.yahooapis.com/v1/public/yql?q=select%20*%20from%20yahoo.finance.xchange%20where%20pair%20in%20(\""
			+ PLACEHOLDER + "\")&format=json&env=store://datatables.org/alltableswithkeys";

	private final Cache<String, BigDecimal> exchangeRatesCache = CacheBuilder.newBuilder().
			expireAfterWrite(cachingTimeMillis, TimeUnit.MILLISECONDS).build();

	/**
	 * Returns the exchange rate of 1 USD against the specified currency (by
	 * default CHF)
	 * 
	 * @return the forex exchange rate
	 * @throws Exception
	 */
	public BigDecimal getExchangeRate(final String symbol) throws Exception {
		final String pair = symbol + appConfig.getCurrency().toString();
		return getExchangeRatePair(pair);
	}
	private BigDecimal getExchangeRatePair(final String pair) throws Exception {

		BigDecimal exchangeRate = exchangeRatesCache.getIfPresent(pair);

		if (exchangeRate == null) {
			final String url = YAHOO_API.replace(PLACEHOLDER, pair);
			final StringBuffer response = doHttpRequest(url);
			// gets actual exchange rate out of Json Object and saves it to
			// last.

			final Gson gson = new Gson();
			final Query query = gson.fromJson(response.toString(), Query.class);
			exchangeRate = new BigDecimal(query.results.rate.Rate);
			exchangeRatesCache.put(pair, exchangeRate);
		}

		return exchangeRate;
	}
	
	@Scheduled(fixedRate=cachingTimeMillis/2)
	public void refreshExchangeRates() throws Exception {
		for(String pair:exchangeRatesCache.asMap().keySet()) {
			getExchangeRatePair(pair);
		}
	
	}

	public Currency getCurrency() {
		return appConfig.getCurrency();
	}

	/**
	 * Executes JSON HTTP Request and returns result.
	 * 
	 * @param url
	 * @return response of defined by url request
	 * @throws IOException
	 */
	private static StringBuffer doHttpRequest(String url) throws IOException {
		final URL requestURL = new URL(url);
		final HttpURLConnection con = (HttpURLConnection) requestURL.openConnection();

		// optional default is GET
		con.setRequestMethod("GET");

		// add request header
		con.setRequestProperty("User-Agent", USER_AGENT);

		final StringBuffer response = new StringBuffer();
		try (final BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()))) {
			String inputLine = null;
			while ((inputLine = in.readLine()) != null) {
				response.append(inputLine);
			}
		}
		return response;
	}

	/*-
	 * minimized JSON representation. Query result looks like:
	 *	{
	 *	   "query":{
	 *        "count":1,
	 *	      "created":"2015-08-13T10:10:05Z",
	 *	      "lang":"en-US",
	 *	      "results":{
	 *	         "rate":{
	 *	            "id":"CHFUSD",
	 *	            "Name":"CHF/USD",
	 *	            "Rate":"1.0222",
	 *	            "Date":"8/13/2015",
	 *	            "Time":"11:10am",
	 *	            "Ask":"1.0224",
	 *	            "Bid":"1.0221"
	 *	         }
	 *	      }
	 *	   }
	 *	}
	 *	</code> 
	 */
	private static class Query {
		private Results results;

		private static class Results {
			private Rate rate;

			private static class Rate {
				private String Rate;
			}
		}
	}
}
