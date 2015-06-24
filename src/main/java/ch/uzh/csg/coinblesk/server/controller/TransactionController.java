package ch.uzh.csg.coinblesk.server.controller;

import java.io.IOException;

import net.minidev.json.parser.ParseException;

import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import ch.uzh.csg.coinblesk.responseobject.ServerSignatureRequestTransferObject;
import ch.uzh.csg.coinblesk.responseobject.TransferObject;
import ch.uzh.csg.coinblesk.server.bitcoin.InvalidTransactionException;
import ch.uzh.csg.coinblesk.server.clientinterface.IBitcoinWallet;
import ch.uzh.csg.coinblesk.server.clientinterface.IUserAccount;
import ch.uzh.csg.coinblesk.server.util.ExchangeRates;

/**
 * REST Controller for client http requests regarding Transactions between two
 * UserAccounts.
 * 
 */
@Controller
@RequestMapping("/transaction")
public class TransactionController {
    private static Logger LOGGER = Logger.getLogger(TransactionController.class);
    
    @Autowired
    private IBitcoinWallet bitcoinWalletService;

    @Autowired
    private IUserAccount userAccountService;


    /**
     * Returns up to date exchangerate BTC/CHF
     * 
     * @return CustomResponseObject with exchangeRate BTC/CHF as a String
     */
    @RequestMapping(value = "/exchange-rate", method = RequestMethod.GET, produces = "application/json")
    @ResponseBody
    public TransferObject getExchangeRate() {
        TransferObject transferObject = new TransferObject();
        try {
            transferObject.setSuccessful(true);
            transferObject.setMessage(ExchangeRates.getExchangeRate().toString());
        } catch (ParseException | IOException e) {
            LOGGER.error("Couldn't get exchange rate. Response: " + e.getMessage());
            transferObject.setSuccessful(false);
            transferObject.setMessage(e.getMessage());
        } catch (Throwable t) {
            transferObject.setSuccessful(false);
            transferObject.setMessage("Unexpected: " + t.getMessage());
        }
        return transferObject;
    }

    /**
     * Signs a multi-sig transaction that was partially signed  by the client
     * 
     * @return {@link CustomResponseObject} with information about whether the transaction was successful or not
     */
    @RequestMapping(value = "/signAndBroadcastTx", method = RequestMethod.POST, produces = "application/json")
    @ResponseBody
    public TransferObject sign(@RequestBody ServerSignatureRequestTransferObject sigReq) {
        
        LOGGER.info("Received transaction signature request");
        
        TransferObject response = new TransferObject();
        
        System.out.println(sigReq.getPartialTx());
        
        boolean success = false;
        try {
            success = bitcoinWalletService.signTxAndBroadcast(sigReq.getPartialTx(), sigReq.getIndexAndDerivationPaths());
        } catch (InvalidTransactionException e) {
            response.setSuccessful(false);
            response.setMessage("Invalid transaction: " + e.getMessage());
        }
        
        if(success) {
            response.setSuccessful(true);
        } else {
            response.setSuccessful(false);
            response.setMessage("Invalid transaction or ");
        }
        
        return response;
    }

}
