/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.uzh.csg.coinblesk.server.controller;

import ch.uzh.csg.coinblesk.server.service.KeyService;
import com.coinblesk.json.KeyTO;
import com.coinblesk.json.RefundTO;

import java.util.Base64;
import java.util.List;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

/**
 *
 * @author Alessandro Di Carli
 * @author Thomas Bocek
 *
 */
@RestController
@RequestMapping(value = {"/payment", "/p"})
public class PaymentController {

    private final static Logger LOG = LoggerFactory.getLogger(PaymentController.class);

    @Autowired
    private KeyService clientKeyService;

    /**
     * Input is the KeyTO with the client public key. The server will create for
     * this client public key its own server keypair and return the server
     * public key, or indicate an error in KeyTO (or via status code). Make sure
     * to check for isSuccess(). Internally the server will hash the client
     * public key with SHA-256 (UUID) and clients need to identify themselfs
     * with this UUID for subsequent calls.
     *
     */
    @RequestMapping(value = {"/key-exchange", "/x"}, method = RequestMethod.POST,
            consumes = "application/json; charset=UTF-8",
            produces = "application/json; charset=UTF-8")
    @ResponseBody
    public KeyTO register(@RequestBody KeyTO keyTO) {
        LOG.debug("Register clientHash for {}", keyTO.publicKey());
        final KeyTO serverKeyTO = new KeyTO();
        try {
            final String clientPublicKey = keyTO.publicKey();
            final ECKey serverEcKey = new ECKey();
            final boolean retVal = clientKeyService.create(clientPublicKey, serverEcKey.getPubKey(), serverEcKey.getPrivKeyBytes());
            if (retVal) {
                serverKeyTO.publicKey(Base64.getEncoder().encodeToString(serverEcKey.getPubKey()));
                return serverKeyTO.setSuccess();
            } else {
                return serverKeyTO.reason(KeyTO.Reason.KEY_ALREADY_EXISTS);
            }
        } catch (Exception e) {
            LOG.error("register keys error", e);
            serverKeyTO.reason(KeyTO.Reason.SERVER_ERROR);
            serverKeyTO.message(e.getMessage());
            return serverKeyTO;
        }
    }
    
    @RequestMapping(value = {"/refund", "/r"}, method = RequestMethod.POST,
            consumes = "application/json; charset=UTF-8",
            produces = "application/json; charset=UTF-8")
    @ResponseBody
    public RefundTO refund(@RequestBody RefundTO refundTO) {
        final List<ECKey> keys = clientKeyService.getPublicECKeysByHash(refundTO.clientPublicKeyHash());
        if(keys == null || keys.size() !=2) {
            return new RefundTO().reason(RefundTO.Reason.KEYS_NOT_FOUND);
        }
        //2-of-2 multisig
        final Script script = ScriptBuilder.createMultiSigOutputScript(2, keys);
        
        

        //add refund tx to database
        Transaction tx = null;
        clientKeyService.addRefundTransaction(tx);
        
        return null;
    }
    
    @RequestMapping(value = {"/multi-sig", "/m"}, method = RequestMethod.POST,
            consumes = "application/json; charset=UTF-8",
            produces = "application/json; charset=UTF-8")
    @ResponseBody
    public void multiSig() {
        
    }
    
    @RequestMapping(value = {"/tx", "/t"}, method = RequestMethod.POST,
            consumes = "application/json; charset=UTF-8",
            produces = "application/json; charset=UTF-8")
    @ResponseBody
    public void transaction() {
        
    }
}
