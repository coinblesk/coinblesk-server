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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.coinblesk.dto.ForexDTO;
import com.coinblesk.server.exceptions.BusinessException;
import com.coinblesk.server.exceptions.CoinbleskInternalError;
import com.coinblesk.server.exceptions.InvalidCurrencyPatternException;
import com.coinblesk.server.utils.DTOUtils;

/**
 * Service that provides current & historic bitcoin exchange rates.
 */
@Service
public class ForexBitcoinService {

	private final static String PLACEHOLDER = "{{PLACEHOLDER}}";
	private final static String COINDESK_CURRENT_API = "http://api.coindesk.com/v1/bpi/currentprice/" + PLACEHOLDER + ".json";
	private final static String COINDESK_HISTORIC_API = "http://api.coindesk.com/v1/bpi/historical/close.json?currency=" + PLACEHOLDER;

	@Cacheable("forex-bitcoin-current-rates")
	public ForexDTO getCurrentRate(String currencySymbol) throws BusinessException {
		String url = COINDESK_CURRENT_API.replace(PLACEHOLDER, currencySymbol);
		try {
			StringBuffer response = ServiceUtils.doHttpRequest(url);
			CurrentJsonStructure json = DTOUtils.fromJSON(response.toString(), CurrentJsonStructure.class);
			CurrentJsonStructure.Rate rate = json.bpi.get(currencySymbol);
			Date date = json.time.updatedISO;

			if(!currencySymbol.equals(rate.code)) {
				throw new CoinbleskInternalError("The currency response could not be parsed correctly.");
			}

			ForexDTO forexDTO = new ForexDTO();
			forexDTO.setCurrencyFrom("BTC");
			forexDTO.setCurrencyTo(currencySymbol);
			forexDTO.setRate(new BigDecimal(rate.rate_float));
			forexDTO.setUpdatedAt(date);

			return forexDTO;

		} catch(Exception ex) {
			throw new InvalidCurrencyPatternException();
		}
	}

	@Cacheable("forex-bitcoin-historic-rates")
	public List<ForexDTO> getHistoricRates(String currencySymbol) throws BusinessException {
		String url = COINDESK_HISTORIC_API.replace(PLACEHOLDER, currencySymbol);
		try {
			StringBuffer response = ServiceUtils.doHttpRequest(url);
			Map<Date, Double> map = DTOUtils.fromJSON(response.toString(), HistoricJsonStructure.class).bpi;
			List<ForexDTO> result = new ArrayList<>();

			for(Map.Entry<Date, Double> entrySet : map.entrySet()) {
				ForexDTO dto = new ForexDTO();
				dto.setCurrencyFrom("BTC");
				dto.setCurrencyTo(currencySymbol);
				dto.setRate(new BigDecimal(entrySet.getValue()));
				dto.setUpdatedAt(entrySet.getKey());
				result.add(dto);
			}

			return result;

		} catch(Exception ex) {
			throw new InvalidCurrencyPatternException();
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
	@CacheEvict(value = { "forex-bitcoin-current-rates" }, allEntries = true)
	public void evictCurrentCache() { }

	@Scheduled(fixedRate = 3600000L) // every hour, the history is wiped
	@CacheEvict(value = { "forex-bitcoin-historic-rates" }, allEntries = true)
	public void evictHistoricCache() { }

}