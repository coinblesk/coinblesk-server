package ch.uzh.csg.coinblesk.server.controller;

import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import ch.uzh.csg.coinblesk.responseobject.SetupRequestObject;
import ch.uzh.csg.coinblesk.responseobject.TransferObject;
import ch.uzh.csg.coinblesk.server.clientinterface.IBitcoinWallet;

/**
 * REST Controller for client http requests regarding Transactions between two
 * UserAccounts.
 * 
 */
@Controller
@RequestMapping("/info")
public class InfoController {
    private static Logger LOGGER = Logger.getLogger(InfoController.class);
    
    @Autowired
    private IBitcoinWallet bitcoinWalletService;


    /**
     * Returns the data that is required by clients to set up a new wallet. 
     * 
     * @return SetupRequestObject containing the server watching key and the bitcoin net.
     */
    @RequestMapping(value = "/setupInfo", method = RequestMethod.GET, produces = "application/json")
    @ResponseBody
    public TransferObject getExchangeRate() {
        SetupRequestObject transferObject = new SetupRequestObject();
        try {
            transferObject.setSuccessful(true);
            transferObject.setBitcoinNet(bitcoinWalletService.getBitcoinNet());
            transferObject.setServerWatchingKey(bitcoinWalletService.getSerializedServerWatchingKey());
        } catch (Throwable t) {
            transferObject.setSuccessful(false);
            transferObject.setMessage("Unexpected: " + t.getMessage());
            LOGGER.fatal(t.getMessage());
        }
        return transferObject;
    }


}
