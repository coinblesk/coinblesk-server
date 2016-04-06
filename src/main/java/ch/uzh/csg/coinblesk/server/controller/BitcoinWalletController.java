package ch.uzh.csg.coinblesk.server.controller;

import java.math.BigDecimal;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;


import ch.uzh.csg.coinblesk.server.service.ForexExchangeRateService;
import ch.uzh.csg.coinblesk.server.utils.ApiVersion;
import com.coinblesk.json.ExchangeRateTO;
import com.coinblesk.json.Type;

/**
 * Controller for client http requests regarding Transactions between two
 * UserAccounts.
 * 
 */
@RestController
@RequestMapping({"/wallet", "/w"})
@ApiVersion({"v1", ""})
public class BitcoinWalletController {
    private static Logger LOGGER = LoggerFactory.getLogger(BitcoinWalletController.class);
    
    @Autowired
    private ForexExchangeRateService forexExchangeRateService;


    /**
     * Returns up to date exchangerate BTC/CHF
     * 
     * @return CustomResponseObject with exchangeRate BTC/CHF as a String
     */
    @RequestMapping(value = {"/exchangeRate/{symbol}" , "/x/{symbol}"}, 
            method = RequestMethod.GET, produces = "application/json; charset=UTF-8")
    @ResponseBody
    public ResponseEntity<ExchangeRateTO> getExchangeRate(@PathVariable(value="symbol") String symbol) {
        LOGGER.debug("Received exchange rate request for currency {}", symbol);
        ExchangeRateTO transferObject = new ExchangeRateTO();
        try {
            BigDecimal exchangeRate = forexExchangeRateService.getExchangeRate(symbol);
            transferObject.setExchangeRate(forexExchangeRateService.getCurrency(), exchangeRate.toString());
            transferObject.setSuccess();
            return new ResponseEntity<>(transferObject, HttpStatus.OK);
        } catch (Exception e) {
            transferObject.type(Type.SERVER_ERROR);
            transferObject.message(e.getMessage());
            return new ResponseEntity<>(transferObject, HttpStatus.BAD_REQUEST);
        }
    }
}
