package ch.uzh.csg.coinblesk.server.controller;

import java.io.IOException;

import javax.servlet.http.HttpServletResponse;

import net.minidev.json.parser.ParseException;

import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import ch.uzh.csg.coinblesk.customserialization.Currency;
import ch.uzh.csg.coinblesk.responseobject.ExchangeRateTransferObject;
import ch.uzh.csg.coinblesk.responseobject.RefundTxTransferObject;
import ch.uzh.csg.coinblesk.responseobject.ServerSignatureRequestTransferObject;
import ch.uzh.csg.coinblesk.responseobject.SetupRequestObject;
import ch.uzh.csg.coinblesk.responseobject.TransferObject;
import ch.uzh.csg.coinblesk.responseobject.WatchingKeyTransferObject;
import ch.uzh.csg.coinblesk.server.bitcoin.InvalidTransactionException;
import ch.uzh.csg.coinblesk.server.clientinterface.IBitcoinWallet;
import ch.uzh.csg.coinblesk.server.util.ExchangeRates;

/**
 * Controller for client http requests regarding Transactions between two
 * UserAccounts.
 * 
 */
@RestController
@RequestMapping("/wallet")
public class BitcoinWalletController {
    private static Logger LOGGER = Logger.getLogger(BitcoinWalletController.class);
    
    @Autowired
    private IBitcoinWallet bitcoinWalletService;


    /**
     * Returns up to date exchangerate BTC/CHF
     * 
     * @return CustomResponseObject with exchangeRate BTC/CHF as a String
     */
    @RequestMapping(value = "/exchangeRate", method = RequestMethod.GET, produces = "application/json; charset=UTF-8")
    @ResponseBody
    public ResponseEntity<ExchangeRateTransferObject> getExchangeRate() {
        ExchangeRateTransferObject transferObject = new ExchangeRateTransferObject();
        try {
            transferObject.setSuccessful(true);
            transferObject.setExchangeRate(Currency.CHF, ExchangeRates.getExchangeRate().toString());
        } catch (ParseException | IOException e) {
            LOGGER.error("Couldn't get exchange rate. Response: " + e.getMessage());
            transferObject.setSuccessful(false);
            transferObject.setMessage(e.getMessage());
        } catch (Exception t) {
            transferObject.setSuccessful(false);
            transferObject.setMessage("Unexpected: " + t.getMessage());
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
    public ResponseEntity<TransferObject> signAndBroadcastTx(@RequestBody ServerSignatureRequestTransferObject sigReq) {
        
        LOGGER.info("Received transaction signature request");
        
        TransferObject response = new TransferObject();
        
        try {
            boolean success = bitcoinWalletService.signTxAndBroadcast(sigReq.getPartialTx(), sigReq.getIndexAndDerivationPaths());
            
            if(success) {
                response.setSuccessful(true);
            } else {
                response.setSuccessful(false);
                response.setMessage("Invalid transaction");
            }
            
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
    public TransferObject getSetupInfo() {
        SetupRequestObject transferObject = new SetupRequestObject();
        try {
            transferObject.setSuccessful(true);
            transferObject.setBitcoinNet(bitcoinWalletService.getBitcoinNet());
            transferObject.setServerWatchingKey(bitcoinWalletService.getSerializedServerWatchingKey());
        } catch (Exception t) {
            transferObject.setSuccessful(false);
            transferObject.setMessage("Unexpected: " + t.getMessage());
            LOGGER.fatal(t.getMessage());
        }
        return transferObject;
    }

    /**
    * Returns the data that is required by clients to set up a new wallet. 
    * 
    * @return SetupRequestObject containing the server watching key and the bitcoin net.
    */
   @RequestMapping(value = "/saveWatchingkey", method = RequestMethod.POST, produces = "application/json; charset=UTF-8")
   @ResponseBody
   public TransferObject saveWatchingKey(@RequestBody WatchingKeyTransferObject saveWatchingKeyReq) {
       SetupRequestObject transferObject = new SetupRequestObject();
       try {
           bitcoinWalletService.addWatchingKey(saveWatchingKeyReq.getWatchingKey());
       } catch (Exception e) {
           transferObject.setSuccessful(false);
           transferObject.setMessage("Unexpected: " + e.getMessage());
           LOGGER.error(e);
       }
       return transferObject;
   }
   
   private <T extends TransferObject> ResponseEntity<T> createResponse(T response) {
       HttpStatus status = response.isSuccessful() ? HttpStatus.OK : HttpStatus.BAD_REQUEST;
       return new ResponseEntity<T>(response, status);
   }
}
