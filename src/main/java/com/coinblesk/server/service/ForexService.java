/*
 * Copyright 2016 The Coinblesk team and the CSG Group at University of Zurich
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.coinblesk.server.service;

import static java.lang.Boolean.TRUE;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import com.coinblesk.server.exceptions.BusinessException;
import com.coinblesk.server.exceptions.CoinbleskInternalError;
import com.coinblesk.server.exceptions.InvalidCurrencyPatternException;
import com.coinblesk.server.utils.DTOUtils;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

/**
 * Service that provides bitcoin and forex exchange rates. This class also
 * caches exchange rates.
 *
 * @author rvoellmy
 * @author Thomas Bocek
 */
@Service
final public class ForexService {

	// 18min
	private final static int CACHING_TIME_RATE_MILLIS = 18 * 60 * 1000;
	// 2 days
	private final static int CACHING_TIME_SYMBOL_MILLIS = 2 * 24 * 60 * 60 * 1000;


	private final static String PLACEHOLDER = "{{PLACEHOLDER}}";
	private final static String YAHOO_API = "http://query.yahooapis.com/v1/public/yql?q=select%20*%20from%20yahoo" +
		".finance.xchange%20where%20pair%20in%20(" + PLACEHOLDER + ")&format=json&env=store://datatables" +
		".org/alltableswithkeys";

	private final Cache<String, BigDecimal> exchangeRatesCache = CacheBuilder.newBuilder().expireAfterWrite
		(CACHING_TIME_RATE_MILLIS, TimeUnit.MILLISECONDS).build();

	private final Cache<String, Boolean> exchangeRatesSymbolCache = CacheBuilder.newBuilder().expireAfterWrite
		(CACHING_TIME_SYMBOL_MILLIS, TimeUnit.MILLISECONDS).build();

	/**
	 * Returns the exchange rate of 1 USD against the specified currency (by
	 * default CHF)
	 *
	 * @return the forex exchange rate
	 * @throws Exception
	 */
	public BigDecimal getExchangeRate(final String symbolFrom, final String symbolTo) throws BusinessException {
		final String pair = symbolFrom + symbolTo;
		return getExchangeRates(pair).get(pair);
	}

	public Map<String, BigDecimal> getExchangeRates(final String... pairs) throws BusinessException {
		// mark as used
		Map<String, BigDecimal> exchangeRates = new HashMap<>(pairs.length);
		// this empty most of the times:
		List<String> unknowRates = new ArrayList<>(1);
		for (String pair : pairs) {
			exchangeRatesSymbolCache.put(pair, TRUE);
			BigDecimal exchangeRate = exchangeRatesCache.getIfPresent(pair);

			if (exchangeRate == null) {
				unknowRates.add(pair);
			} else {
				exchangeRates.put(pair, exchangeRate);
			}
		}

		if (!unknowRates.isEmpty()) {
			String values = String.join("\",\"", unknowRates);
			String rates = '"' + values + '"';
			final String url = YAHOO_API.replace(PLACEHOLDER, rates);
			try {
				final StringBuffer response = ServiceUtils.doHttpRequest(url);

				// gets actual exchange rate out of Json Object and saves it to last.
				if (unknowRates.size() > 1) {
					final RootMulti root = DTOUtils.fromJSON(response.toString(), RootMulti.class);
					for (RootMulti.Query.Results.Rate rate : root.query.results.rate) {
						if(rate.Rate.equals("N/A")) {
							throw new InvalidCurrencyPatternException();
						}
						BigDecimal exchangeRate = new BigDecimal(rate.Rate);
						exchangeRatesCache.put(rate.id, exchangeRate);
						exchangeRates.put(rate.id, exchangeRate);
					}
				} else {
					final RootSingle root = DTOUtils.fromJSON(response.toString(), RootSingle.class);
					if(root.query.results.rate.Rate.equals("N/A")) {
						throw new InvalidCurrencyPatternException();
					}
					BigDecimal exchangeRate = new BigDecimal(root.query.results.rate.Rate);
					exchangeRatesCache.put(root.query.results.rate.id, exchangeRate);
					exchangeRates.put(root.query.results.rate.id, exchangeRate);
				}
			} catch(IOException ex) {
				throw new CoinbleskInternalError("Could not fetch the forex rates");
			}
		}

		return exchangeRates;
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
	private static class RootSingle {
		private Query query;

		private static class Query {
			private Results results;

			private static class Results {
				private Rate rate;

				private static class Rate {
					private String Rate;
					private String id;
				}
			}
		}
	}

	private static class RootMulti {
		private Query query;

		private static class Query {
			private Results results;

			private static class Results {
				private List<Rate> rate = new ArrayList<>();

				private static class Rate {
					private String Rate;
					private String id;
				}
			}
		}
	}

	@Component
	final static public class ForexTask {

		private final static Logger Log = LoggerFactory.getLogger(ForexTask.class);

		private final ForexService service;

		@Autowired
		public ForexTask(ForexService service) {
			this.service = service;
		}

		// call every 6 minutes
		@Scheduled(fixedRate = ForexService.CACHING_TIME_RATE_MILLIS / 3)
		public void doTask() throws Exception {
			Log.debug("Scheduled: Getting new rates...");
			Set<String> set = service.exchangeRatesSymbolCache.asMap().keySet();
			service.getExchangeRates(set.toArray(new String[set.size()]));
		}
	}
}
