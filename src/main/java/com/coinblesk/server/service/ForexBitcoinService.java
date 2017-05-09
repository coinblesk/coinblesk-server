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

import static com.coinblesk.enumerator.ForexCurrency.BTC;
import static com.coinblesk.enumerator.ForexCurrency.USD;
import static org.knowm.xchange.currency.CurrencyPair.BTC_USD;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.bitstamp.BitstampExchange;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.knowm.xchange.service.marketdata.MarketDataService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.coinblesk.dto.ForexDTO;
import com.coinblesk.enumerator.ForexCurrency;
import com.coinblesk.server.exceptions.BusinessException;
import com.coinblesk.server.exceptions.CoinbleskInternalError;
import com.coinblesk.server.exceptions.InvalidForexCurrencyException;
import com.coinblesk.util.DTOUtils;

/**
 * Service that provides current & historic bitcoin exchange rates.
 */
@Service
public class ForexBitcoinService {

	private final static String PLACEHOLDER = "{{PLACEHOLDER}}";
	private final static String COINDESK_CURRENT_API = "http://api.coindesk.com/v1/bpi/currentprice/" + PLACEHOLDER + ".json";
	private final static String COINDESK_HISTORIC_API = "http://api.coindesk.com/v1/bpi/historical/close.json?currency=" + PLACEHOLDER;

	private final static MarketDataService MARKET_DATA_SERVICE  = ExchangeFactory.INSTANCE.createExchange(BitstampExchange.class.getName()).getMarketDataService();

	private ForexFiatService forexFiatService;

	@Autowired
	public ForexBitcoinService(ForexFiatService forexFiatService) {
		this.forexFiatService = forexFiatService;
	}

	/**
	 * Get the exchange rate from Bitstamp. As a trader can do a tradeback, we need the specific exchange. The exchange
	 * rate for the fiat currency is then calculated in a second step.
	 * @return
	 * @throws IOException
	 */
	public ForexDTO getBitstampCurrentRateBTCUSD() {
		ForexDTO forexDTO = new ForexDTO();

		try {
			Ticker ticker = MARKET_DATA_SERVICE.getTicker(BTC_USD);
			forexDTO.setCurrencyFrom(BTC);
			forexDTO.setCurrencyTo(USD);
			forexDTO.setRate(ticker.getAsk().add(ticker.getBid()).divide(BigDecimal.valueOf(2)));
			forexDTO.setUpdatedAt(ticker.getTimestamp());

		} catch (IOException exception) {
			throw new CoinbleskInternalError("Bitstamp currency rate currently not available.");
		}

		return forexDTO;
	}

	@Cacheable("forex-bitcoin-bitstamp-current")
	public ForexDTO getBitstampCurrentRate(ForexCurrency currency) throws BusinessException {
		ForexDTO forexBTCUSD = getBitstampCurrentRateBTCUSD();

		if(BTC.equals(currency)) {
			throw new InvalidForexCurrencyException();
		}

		if(USD.equals(currency)) {
			return forexBTCUSD;

		} else {
			BigDecimal forexRateUSDOtherCurrency = forexFiatService.getExchangeRate(USD, currency);
			BigDecimal forexRate = forexBTCUSD.getRate().multiply(forexRateUSDOtherCurrency);

			ForexDTO forex = new ForexDTO();
			forex.setCurrencyFrom(BTC);
			forex.setCurrencyTo(currency);
			forex.setRate(forexRate);
			forex.setUpdatedAt(new Date());

			return forex;
		}
	}

	@Cacheable("forex-bitcoin-coindesk-current")
	public ForexDTO getCoindeskCurrentRate(ForexCurrency currency) throws BusinessException {
		String url = COINDESK_CURRENT_API.replace(PLACEHOLDER, currency.name());

		// BTC <-> BTC conversion is not supported (and not reasonable)
		if(BTC.equals(currency)) {
			throw new InvalidForexCurrencyException();
		}

		try {
			StringBuffer response = ServiceUtils.doHttpRequest(url);
			CurrentJsonStructure json = DTOUtils.fromJSON(response.toString(), CurrentJsonStructure.class);
			CurrentJsonStructure.Rate rate = json.bpi.get(currency.name());
			Date date = json.time.updatedISO;

			if(!currency.name().equals(rate.code)) {
				throw new CoinbleskInternalError("The currency response could not be parsed correctly.");
			}

			ForexDTO forexDTO = new ForexDTO();
			forexDTO.setCurrencyFrom(BTC);
			forexDTO.setCurrencyTo(currency);
			forexDTO.setRate(new BigDecimal(rate.rate_float));
			forexDTO.setUpdatedAt(date);

			return forexDTO;

		} catch(Exception ex) {
			throw new InvalidForexCurrencyException();
		}
	}

	@Cacheable(value = "forex-bitcoin-coindesk-history")
	public List<ForexDTO> getCoindeskHistoricRates(ForexCurrency currency) throws BusinessException {
		String url = COINDESK_HISTORIC_API.replace(PLACEHOLDER, currency.name());

		// BTC <-> BTC conversion is not supported (and not reasonable)
		if(BTC.equals(currency)) {
			throw new InvalidForexCurrencyException();
		}

		try {
			StringBuffer response = ServiceUtils.doHttpRequest(url);
			Map<Date, Double> map = DTOUtils.fromJSON(response.toString(), HistoricJsonStructure.class).bpi;
			List<ForexDTO> result = new ArrayList<>();

			for(Map.Entry<Date, Double> entrySet : map.entrySet()) {
				ForexDTO dto = new ForexDTO();
				dto.setCurrencyFrom(BTC);
				dto.setCurrencyTo(currency);
				dto.setRate(new BigDecimal(entrySet.getValue()));
				dto.setUpdatedAt(entrySet.getKey());
				result.add(dto);
			}

			return result;

		} catch(Exception ex) {
			throw new InvalidForexCurrencyException();
		}
	}

	private static class CurrentJsonStructure {
		private Time time;
		private Map<String, Rate> bpi;

		private static class Time {
			private Date updatedISO;
		}

		private static class Rate {
			private String code;
			private double rate_float;
		}
	}

	private static class HistoricJsonStructure {
		private Map<Date, Double> bpi;
	}

	@Scheduled(fixedRate = 60000L) // every minute, the current rate is wiped
	@CacheEvict(value = { "forex-bitcoin-coindesk-current", "forex-bitcoin-bitstamp-current" }, allEntries = true)
	public void evictCurrentCache() { }

	@Scheduled(fixedRate = 3600000L) // every hour, the history is wiped
	@CacheEvict(value = { "forex-bitcoin-coindesk-history" }, allEntries = true)
	public void evictHistoricCache() { }

}
