/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.uzh.csg.coinblesk.server.controller;

import ch.uzh.csg.coinblesk.server.config.AppConfig;
import ch.uzh.csg.coinblesk.server.entity.Keys;
import ch.uzh.csg.coinblesk.server.service.KeyService;
import ch.uzh.csg.coinblesk.server.service.WalletService;
import ch.uzh.csg.coinblesk.server.utils.CoinUtils;
import ch.uzh.csg.coinblesk.server.utils.Pair;
import com.coinblesk.bitcoin.BitcoinNet;
import com.coinblesk.json.BalanceTO;
import com.coinblesk.json.KeyTO;
import com.coinblesk.json.RefundTO;
import java.math.BigInteger;
import java.util.ArrayList;

import java.util.Base64;
import java.util.List;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.crypto.TransactionSignature;
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
    private AppConfig appConfig;

    @Autowired
    private KeyService clientKeyService;
    
    @Autowired
    private WalletService walletService;
    
    

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
    public KeyTO keyExchange(@RequestBody KeyTO keyTO) {
        LOG.debug("Register clientHash for {}", keyTO.publicKey());
        final KeyTO serverKeyTO = new KeyTO();
        try {
            final String clientPublicKey = keyTO.publicKey();
            final ECKey serverEcKey = new ECKey();
            final Pair<Boolean, Keys> retVal = clientKeyService.create(clientPublicKey, serverEcKey.getPubKey(), serverEcKey.getPrivKeyBytes());
            if (retVal.element0()) {
                serverKeyTO.publicKey(Base64.getEncoder().encodeToString(serverEcKey.getPubKey()));
                final List<ECKey> keys = new ArrayList<>(2);
                keys.add(ECKey.fromPublicOnly(retVal.element1().clientPublicKey()));
                keys.add(serverEcKey);
                //2-of-2 multisig
                final Script script = ScriptBuilder.createP2SHOutputScript(2, keys);
                walletService.addWatching(script);
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
    
    @RequestMapping(value = {"/balance", "/b"}, method = RequestMethod.GET,
            consumes = "application/json; charset=UTF-8",
            produces = "application/json; charset=UTF-8")
    @ResponseBody
    public BalanceTO balance(@RequestBody KeyTO keyTO) {
        LOG.debug("Balance clientHash for {}", keyTO.publicKey());
        try {
            final List<ECKey> keys = clientKeyService.getPublicECKeysByClientPublicKey(keyTO.publicKey());
            final Script script = ScriptBuilder.createP2SHOutputScript(2, keys);
            Coin balance = walletService.balance(script);
            return new BalanceTO()
                    .balance(balance.toPlainString());
            
        } catch (Exception e) {
            LOG.error("register keys error", e);
            return new BalanceTO()
                    .reason(BalanceTO.Reason.SERVER_ERROR)
                    .message(e.getMessage());
        }
    }
    
    @RequestMapping(value = {"/refund", "/r"}, method = RequestMethod.POST,
            consumes = "application/json; charset=UTF-8",
            produces = "application/json; charset=UTF-8")
    @ResponseBody
    public RefundTO refund(@RequestBody RefundTO refundTO) {
        LOG.debug("Refund for {}", refundTO.clientPublicKey());
        try {
            final List<ECKey> keys = clientKeyService.getPublicECKeysByClientPublicKey(refundTO.clientPublicKey());
            if(keys == null || keys.size() !=2) {
                return new RefundTO().reason(RefundTO.Reason.KEYS_NOT_FOUND);
            }
            final Script script = ScriptBuilder.createP2SHOutputScript(2, keys);
            final Transaction refundTransaction = new Transaction(
                    appConfig.getNetworkParameters(), Base64.getDecoder().decode(refundTO.refundTransaction()));
            final TransactionSignature clientSignature = new TransactionSignature(
                    new BigInteger(refundTO.clientSignatureR()), new BigInteger(refundTO.clientSignatureS()));
        
            for(int i=0;i<refundTransaction.getInputs().size();i++) {
                final Sha256Hash sighash = refundTransaction.hashForSignature(i, script, Transaction.SigHash.ALL, false);
                final TransactionSignature serverSignature = new TransactionSignature(keys.get(1).sign(sighash), Transaction.SigHash.ALL, false);
                final Script refundTransactionInputScript = ScriptBuilder.createMultiSigInputScript(clientSignature, serverSignature);
                refundTransaction.getInput(i).setScriptSig(refundTransactionInputScript);
                refundTransaction.getInput(i).verify();
            }
        
            byte[] refundTx = refundTransaction.unsafeBitcoinSerialize();
            clientKeyService.addRefundTransaction(refundTO.clientPublicKey(), refundTx);
        
            return new RefundTO().setSuccess().refundTransaction(
                    Base64.getEncoder().encodeToString(refundTx));
        } catch (Exception e) {
            LOG.error("register keys error", e);
            return new RefundTO()
                    .reason(RefundTO.Reason.SERVER_ERROR)
                    .message(e.getMessage());
        }
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
