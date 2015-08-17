package ch.uzh.csg.coinblesk.server.controller;

import java.math.BigDecimal;

import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import ch.uzh.csg.coinblesk.responseobject.ExchangeRateTransferObject;
import ch.uzh.csg.coinblesk.responseobject.RefundTxTransferObject;
import ch.uzh.csg.coinblesk.responseobject.ServerSignatureRequestTransferObject;
import ch.uzh.csg.coinblesk.responseobject.SetupRequestObject;
import ch.uzh.csg.coinblesk.responseobject.SignedTxTransferObject;
import ch.uzh.csg.coinblesk.responseobject.TransferObject;
import ch.uzh.csg.coinblesk.responseobject.WatchingKeyTransferObject;
import ch.uzh.csg.coinblesk.server.bitcoin.InvalidTransactionException;
import ch.uzh.csg.coinblesk.server.service.BitcoinWalletService;
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
    private BitcoinWalletService bitcoinWalletService;
    
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

    /**
     * Signs a multi-sig transaction that was partially signed  by the client
     * 
     * @return {@link CustomResponseObject} with information about whether the transaction was successful or not
     */
    @RequestMapping(value = "/signAndBroadcastTx", method = RequestMethod.POST, produces = "application/json; charset=UTF-8")
    @ResponseBody
    public ResponseEntity<SignedTxTransferObject> signAndBroadcastTx(@RequestBody ServerSignatureRequestTransferObject sigReq) {
        
        LOGGER.info("Received transaction signature request");
        
        SignedTxTransferObject response = new SignedTxTransferObject();
        
        try {
            String signedTx = bitcoinWalletService.signAndBroadcastTx(sigReq.getPartialTx(), sigReq.getIndexAndDerivationPaths());
            response.setSignedTx(signedTx);
            response.setSuccessful(true);
        } catch (InvalidTransactionException e) {
            response.setSuccessful(false);
            response.setMessage("Invalid transaction: " + e.getMessage());
        }
        
        return createResponse(response);
    }
    
    /**
     * Signs a multi-sig transaction that was partially signed  by the client
     * 
     * @return {@link CustomResponseObject} with information about whether the transaction was successful or not
     */
    @RequestMapping(value = "/signRefundTx", method = RequestMethod.POST, produces = "application/json; charset=UTF-8")
    @ResponseBody public ResponseEntity<RefundTxTransferObject> createRefundTx(@RequestBody ServerSignatureRequestTransferObject sigReq, HttpServletResponse res) {
        
        LOGGER.info("Received transaction refund transaction request");
        
        RefundTxTransferObject response = new RefundTxTransferObject();
        
        System.out.println(sigReq.getPartialTx());
        
        String refundTx = null;
        try {
            refundTx = bitcoinWalletService.signRefundTx(sigReq.getPartialTx(), sigReq.getIndexAndDerivationPaths());
            response.setRefundTx(refundTx);
            response.setSuccessful(true);
        } catch (InvalidTransactionException e) {
            response.setSuccessful(false);
            response.setMessage("Invalid transaction: " + e.getMessage());
        }
        
        return createResponse(response);
    }
    
    /**
     * Returns the data that is required by clients to set up a new wallet. 
     * 
     * @return SetupRequestObject containing the server watching key and the bitcoin net.
     */
    @RequestMapping(value = "/setupInfo", method = RequestMethod.GET, produces = "application/json; charset=UTF-8")
    @ResponseBody
    public ResponseEntity<TransferObject> getSetupInfo() {
        SetupRequestObject transferObject = new SetupRequestObject();
        try {
            transferObject.setSuccessful(true);
            transferObject.setBitcoinNet(bitcoinWalletService.getBitcoinNet());
            transferObject.setServerWatchingKey(bitcoinWalletService.getSerializedServerWatchingKey());
        } catch (Exception t) {
            transferObject.setSuccessful(false);
            transferObject.setMessage("Unexpected: " + t.getMessage());
            LOGGER.error("tranfer not successful", t);
        }
        return createResponse(transferObject);
    }

    /**
    * Returns the data that is required by clients to set up a new wallet. 
    * 
    * @return SetupRequestObject containing the server watching key and the bitcoin net.
    */
   @RequestMapping(value = "/saveWatchingKey", method = RequestMethod.POST, produces = "application/json; charset=UTF-8")
   @ResponseBody
   public ResponseEntity<TransferObject> saveWatchingKey(@RequestBody WatchingKeyTransferObject saveWatchingKeyReq) {
       TransferObject transferObject = new SetupRequestObject();
       try {
           bitcoinWalletService.addWatchingKey(saveWatchingKeyReq.getWatchingKey());
           transferObject.setSuccessful(true);
       } catch (Exception e) {
           transferObject.setSuccessful(false);
           transferObject.setMessage("Error: " + e.getMessage());
           LOGGER.error("watching key not added", e);
       }
       return createResponse(transferObject);
   }
   
   private <T extends TransferObject> ResponseEntity<T> createResponse(T response) {
       HttpStatus status = response.isSuccessful() ? HttpStatus.OK : HttpStatus.BAD_REQUEST;
       return new ResponseEntity<T>(response, status);
   }
}
