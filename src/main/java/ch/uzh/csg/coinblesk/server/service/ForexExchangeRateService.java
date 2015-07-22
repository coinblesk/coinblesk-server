package ch.uzh.csg.coinblesk.server.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.TimeUnit;

import net.minidev.json.JSONObject;
import net.minidev.json.parser.JSONParser;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import ch.uzh.csg.coinblesk.customserialization.Currency;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

/**
 * 
 * Service that provides bitcoin and forex exchange rates. This class also
 * caches exchange rates.
 * 
 * @author rvoellmy
 *
 */
@Service
public class ForexExchangeRateService {

    @Value("${exchangerates.cachingtime:900}")
    long cachingTime;

    @Value("${exchangerates.currency:CHF}")
    private Currency currency;

    private final static String USER_AGENT = "Mozilla/5.0";
    private final static String PLACEHOLDER = "{{PLACEHOLDER}}";
    private final static String YAHOO_API = "http://query.yahooapis.com/v1/public/yql?q=select%20*%20from%20yahoo.finance.xchange%20where%20pair%20in%20(\"" + PLACEHOLDER + "\")&format=json&env=store://datatables.org/alltableswithkeys";
    
    private Cache<String, BigDecimal> exchangeRatesCache;
    

    public ForexExchangeRateService() {
         exchangeRatesCache = CacheBuilder.newBuilder().expireAfterWrite(cachingTime, TimeUnit.SECONDS).build();
    }

    /**
     * Returns the exchange rate of 1 USD against the specified currency (by
     * default CHF)
     * 
     * @return the forex exchange rate
     * @throws Exception
     */
    public BigDecimal getExchangeRate(String symbol) throws Exception {
        
        String pair = symbol + currency.toString();
        
        BigDecimal exchangeRate = exchangeRatesCache.getIfPresent(pair);
        
        if(exchangeRate == null) {
            String url = YAHOO_API.replace(PLACEHOLDER, pair);
            StringBuffer response = doHttpRequest(url);
            // gets actual exchange rate out of Json Object and saves it to last.
            //JSONParser.MODE_JSON_SIMPLE
            @SuppressWarnings("deprecation")
            JSONParser parser = new JSONParser();

            JSONObject httpAnswerJson;
            httpAnswerJson = (JSONObject) (parser.parse(response.toString()));
            JSONObject query = (JSONObject) httpAnswerJson.get("query");
            JSONObject results = (JSONObject) query.get("results");
            JSONObject rate = (JSONObject) results.get("rate");
            String rateString = (String) rate.get("Rate");
            exchangeRate = new BigDecimal(rateString);
            
            exchangeRatesCache.put(pair, exchangeRate);
        }

        return exchangeRate;
    }

   
    public Currency getCurrency() {
        return currency;
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
