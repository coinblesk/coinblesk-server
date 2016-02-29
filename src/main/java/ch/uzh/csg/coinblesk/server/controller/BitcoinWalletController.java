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

import com.coinblesk.responseobject.ExchangeRateTransferObject;
import com.coinblesk.responseobject.TransferObject;
import ch.uzh.csg.coinblesk.server.service.ForexExchangeRateService;

/**
 * Controller for client http requests regarding Transactions between two
 * UserAccounts.
 * 
 */
@RestController
@RequestMapping("/wallet")
public class BitcoinWalletController {
    private static Logger LOGGER = LoggerFactory.getLogger(BitcoinWalletController.class);
    
    @Autowired
    private ForexExchangeRateService forexExchangeRateService;


    /**
     * Returns up to date exchangerate BTC/CHF
     * 
     * @return CustomResponseObject with exchangeRate BTC/CHF as a String
     */
    @RequestMapping(value = "/exchangeRate/{symbol}", method = RequestMethod.GET, produces = "application/json; charset=UTF-8")
    @ResponseBody
    public ResponseEntity<ExchangeRateTransferObject> getExchangeRate(@PathVariable(value="symbol") String symbol) {
        LOGGER.debug("Received exchange rate request for currency {}", symbol);
        ExchangeRateTransferObject transferObject = new ExchangeRateTransferObject();
        try {
            BigDecimal exchangeRate = forexExchangeRateService.getExchangeRate(symbol);
            transferObject.setExchangeRate(forexExchangeRateService.getCurrency(), exchangeRate.toString());
            transferObject.setSuccessful(true);
        } catch (Exception e) {
            transferObject.setSuccessful(false);
            transferObject.setMessage(e.getMessage());
        }
        return createResponse(transferObject);
    }
   
   private <T extends TransferObject> ResponseEntity<T> createResponse(T response) {
       HttpStatus status = response.isSuccessful() ? HttpStatus.OK : HttpStatus.BAD_REQUEST;
       return new ResponseEntity<T>(response, status);
   }
}
