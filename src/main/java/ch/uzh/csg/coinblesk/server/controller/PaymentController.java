/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.uzh.csg.coinblesk.server.controller;

import ch.uzh.csg.coinblesk.server.config.AppConfig;
import ch.uzh.csg.coinblesk.server.entity.Keys;
import ch.uzh.csg.coinblesk.server.service.KeyService;
import ch.uzh.csg.coinblesk.server.service.TransactionService;
import ch.uzh.csg.coinblesk.server.service.WalletService;
import ch.uzh.csg.coinblesk.server.utils.CoinUtils;
import ch.uzh.csg.coinblesk.server.utils.Pair;
import com.coinblesk.bitcoin.BitcoinNet;
import com.coinblesk.json.BalanceTO;
import com.coinblesk.json.CompleteSign;
import com.coinblesk.json.KeyTO;
import com.coinblesk.json.PrepareHalfSignTO;
import com.coinblesk.json.RefundP2shTO;
import com.coinblesk.json.RefundTO;
import com.coinblesk.json.SignTO;
import java.math.BigInteger;
import java.util.ArrayList;

import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutPoint;
import org.bitcoinj.core.TransactionOutput;
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
    
    public final static long UNIX_TIME_MONTH = 60*60*24*30;
    public final static int LOCK_TIME_MONTHS = 3;

    private final static Logger LOG = LoggerFactory.getLogger(PaymentController.class);
    
    @Autowired
    private AppConfig appConfig;

    @Autowired
    private KeyService clientKeyService;
    
    @Autowired
    private WalletService walletService;
    
    @Autowired
    private TransactionService transactionService;
    
    

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
            final byte[] clientPublicKeyRaw = Base64.getDecoder().decode(clientPublicKey);
            final ECKey serverEcKey = new ECKey();
            final List<ECKey> keys = new ArrayList<>(2);
            keys.add(ECKey.fromPublicOnly(clientPublicKeyRaw));
            keys.add(serverEcKey);
            
            //TODO: if key already exists, return public key from server only
            final Script script = ScriptBuilder.createP2SHOutputScript(2, keys);
            final Pair<Boolean, Keys> retVal = clientKeyService.create(clientPublicKey, 
                    script.getToAddress(appConfig.getNetworkParameters()).getHash160(), serverEcKey.getPubKey(), serverEcKey.getPrivKeyBytes());
            if (retVal.element0()) {
                
                serverKeyTO.publicKey(Base64.getEncoder().encodeToString(serverEcKey.getPubKey()));
                
                keys.add(ECKey.fromPublicOnly(retVal.element1().clientPublicKey()));
                keys.add(serverEcKey);
                //2-of-2 multisig
                
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
            final List<ECKey> keys = clientKeyService.getECKeysByClientPublicKey(refundTO.clientPublicKey());
            if(keys == null || keys.size() !=2) {
                return new RefundTO().reason(RefundTO.Reason.KEYS_NOT_FOUND);
            }
            final Script redeemScript = ScriptBuilder.createP2SHOutputScript(2, keys);
            final Transaction refundTransaction = new Transaction(
                    appConfig.getNetworkParameters(), Base64.getDecoder().decode(refundTO.refundTransaction()));
            
        
            for(int i=0;i<refundTransaction.getInputs().size();i++) {
                final Sha256Hash sighash = refundTransaction.hashForSignature(i, redeemScript, Transaction.SigHash.ALL, false);
                final TransactionSignature serverSignature = new TransactionSignature(keys.get(1).sign(sighash), Transaction.SigHash.ALL, false);
                List<TransactionSignature> l = new ArrayList<>();
                
                final TransactionSignature clientSignature = new TransactionSignature(
                    new BigInteger(refundTO.clientSignatures().get(i).clientSignatureR()), 
                        new BigInteger(refundTO.clientSignatures().get(i).clientSignatureS()));
                
                l.add(clientSignature);
                l.add(serverSignature);
                final Script refundTransactionInputScript = ScriptBuilder.createP2SHMultiSigInputScript(l, redeemScript);
                refundTransaction.getInput(i).setScriptSig(refundTransactionInputScript);
                //refundTransaction.getInput(i).verify();
            }
            //refundTransaction.verify();
        
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
    
    private static Map<ECKey, Transaction> storedHalfTx = new HashMap<>();
    
    @RequestMapping(value = {"/prepare", "/p"}, method = RequestMethod.POST,
            consumes = "application/json; charset=UTF-8",
            produces = "application/json; charset=UTF-8")
    @ResponseBody
    public PrepareHalfSignTO prepareHalfSign(@RequestBody PrepareHalfSignTO prepareSignTO) {
        LOG.debug("Prepare half signed {}", prepareSignTO.clientPublicKey());
        try {
            final List<ECKey> keys = clientKeyService.getECKeysByClientPublicKey(prepareSignTO.clientPublicKey());
            
            if(keys == null || keys.size() !=2) {
                return new PrepareHalfSignTO().reason(PrepareHalfSignTO.Reason.KEYS_NOT_FOUND);
            }
            final ECKey clientKey = keys.get(0);
            final ECKey serverKey = keys.get(1);
            final Script redeemScript = ScriptBuilder.createP2SHOutputScript(2, keys);
            final Address p2shAddressFrom = redeemScript.getToAddress(appConfig.getNetworkParameters());
            
            final Coin amountToSpend = Coin.valueOf(prepareSignTO.amountToSpend());
            if(prepareSignTO.p2shAddress() == null || prepareSignTO.p2shAddress().length == 0) {
                return new PrepareHalfSignTO().reason(PrepareHalfSignTO.Reason.ADDRESS_EMPTY);
            }
            final Address p2shAddressTo = new Address(appConfig.getNetworkParameters(), prepareSignTO.p2shAddress());
            if(!clientKeyService.containsP2SH(p2shAddressFrom)) {
                return new PrepareHalfSignTO().reason(PrepareHalfSignTO.Reason.ADDRESS_UNKNOWN);
            }
            
            //get all outputs from the BT network
            List<TransactionOutput> outputs = walletService.getOutputs(p2shAddressFrom);
            //in addition, get the outputs from previous TX, possibly not yet published in the BT network
            outputs.addAll(transactionService.pendingOutputs());
            //TODO: check if the outputs does not exceed the lock time of the refund tx
            //TODO: check if outputs not already spent (double spending)
            //now we have all valid outputs and we know its before the refund lock time gets valid. So we can sign this tx
            
            Pair<PrepareHalfSignTO, Pair<Transaction,List<TransactionSignature>>> halfSignedTx = createHalfSignedTx(appConfig.getNetworkParameters(), outputs, p2shAddressFrom, 
                    p2shAddressTo, amountToSpend, redeemScript, serverKey, null);
            
            if(halfSignedTx.element0().isSuccess()) {
                halfSignedTx.element0().halfSignedTransaction(halfSignedTx.element1().element0().unsafeBitcoinSerialize());
                //store halfSignedTx for client
                //TODO: make this a proper storage
                storedHalfTx.put(clientKey, halfSignedTx.element1().element0());
            }
            halfSignedTx.element0().signatures(serialize(halfSignedTx.element1().element1()));
            return halfSignedTx.element0();
        
        
        //create half sign tx, with output to payment address, change to p2sh from client
        //store half signed tx with id-hash
        //return half signed tx
        } catch (Exception e) {
            LOG.error("register keys error", e);
            return new PrepareHalfSignTO()
                    .reason(PrepareHalfSignTO.Reason.SERVER_ERROR)
                    .message(e.getMessage());
        }
    }
    
    @RequestMapping(value = {"/refund-p2sh", "/f"}, method = RequestMethod.POST,
            consumes = "application/json; charset=UTF-8",
            produces = "application/json; charset=UTF-8")
    @ResponseBody
    public RefundP2shTO refundToP2SH(@RequestBody RefundP2shTO refundP2shTO) {
        LOG.debug("Prepare half signed {}", refundP2shTO.clientPublicKey());
        try {
            //get client public key (identifier)
            final List<ECKey> keys = clientKeyService.getECKeysByClientPublicKey(refundP2shTO.clientPublicKey());
            final ECKey clientKey = keys.get(0);
            final ECKey serverKey = keys.get(1);
            final Script redeemScript = ScriptBuilder.createP2SHOutputScript(2, keys);
            final Address p2shAddressFrom = redeemScript.getToAddress(appConfig.getNetworkParameters());
            //get referenced tx with id-hash
            Transaction halfSignedTx = storedHalfTx.get(clientKey);
            
            List<TransactionOutPoint> points = deserialize(refundP2shTO.transactionOutputs());
            List<TransactionSignature> clientSigs = deserialize2(refundP2shTO.refundSignatures());
            
            List<TransactionOutput> outs = find(halfSignedTx.getOutputs(), p2shAddressFrom);
            Pair<Transaction,List<TransactionSignature>> pair = generateRefundTransaction2(
                    outs, 
                    clientKey.toAddress(appConfig.getNetworkParameters()), 
                    redeemScript, 
                    serverKey, 
                    points, 
                    clientSigs);
            
            RefundP2shTO retVal = new RefundP2shTO();
            retVal.setSuccess().fullRefundTransaction(pair.element0().unsafeBitcoinSerialize());
            retVal.refundSignatures(serialize(pair.element1()));
            
            return retVal;
        
        } catch (Exception e) {
            LOG.error("register keys error", e);
            return new RefundP2shTO()
                    .reason(RefundP2shTO.Reason.SERVER_ERROR)
                    .message(e.getMessage());
        }
    }
    
    private List<TransactionOutput> find(List<TransactionOutput> input, Address to) {
        List<TransactionOutput> retVal = new ArrayList<>();
        for(TransactionOutput tout:input) {
            Address a = tout.getAddressFromP2SH(appConfig.getNetworkParameters());
            if(a != null && a.equals(to)) {
                retVal.add(tout);
            }
        }
        return retVal;
    }
    
    private Pair<Transaction,List<TransactionSignature>> generateRefundTransaction2(
            List<TransactionOutput> outputs, Address refund, Script redeemScript, ECKey ecKeyServer, 
            List<TransactionOutPoint> points,List<TransactionSignature> clientSigs) {
        final Transaction refundTransaction = new Transaction(appConfig.getNetworkParameters());
        final long unixTime = ((System.currentTimeMillis() / 1000L) / (10 * 60)) * (10 * 60);
        //TODO: send ower network as well,
        final long lockTime = unixTime + (LOCK_TIME_MONTHS * UNIX_TIME_MONTH);
        Coin remainingAmount = Coin.ZERO;
        for(int i=0;i<outputs.size();i++) {
            TransactionOutput output = outputs.get(i);
            TransactionOutPoint outPoint = points.get(i);
            TransactionInput ti = new TransactionInput(appConfig.getNetworkParameters(), null, redeemScript.getProgram(), outPoint, output.getValue());
            refundTransaction.addInput(ti);
            remainingAmount = remainingAmount.add(output.getValue());
        }
        
        remainingAmount = remainingAmount.subtract(Transaction.REFERENCE_DEFAULT_MIN_TX_FEE);
        refundTransaction.addOutput(remainingAmount, refund);
        refundTransaction.setLockTime(lockTime);
        
        List<TransactionSignature> serverSigs = new ArrayList<>();
        for(int i=0;i<refundTransaction.getInputs().size();i++) {
            final Sha256Hash sighash = refundTransaction.hashForSignature(i, redeemScript, Transaction.SigHash.ALL, false);
            final TransactionSignature serverSignature = new TransactionSignature(ecKeyServer.sign(sighash), Transaction.SigHash.ALL, false);
            List<TransactionSignature> l = new ArrayList<>();
            final TransactionSignature clientSignature = clientSigs.get(i);
            l.add(clientSignature);
            l.add(serverSignature);
            serverSigs.add(serverSignature);
            final Script refundTransactionInputScript = ScriptBuilder.createP2SHMultiSigInputScript(l, redeemScript);
            refundTransaction.getInput(i).setScriptSig(refundTransactionInputScript);
        }
        return new Pair<>(refundTransaction, serverSigs);
    }
    
    @RequestMapping(value = {"/complete-sign", "/s"}, method = RequestMethod.POST,
            consumes = "application/json; charset=UTF-8",
            produces = "application/json; charset=UTF-8")
    @ResponseBody
    public CompleteSign sign(@RequestBody CompleteSign signTO) {
        
        final List<ECKey> keys = clientKeyService.getECKeysByClientPublicKey(signTO.clientPublicKey());
        if(keys == null || keys.size() !=2) {
                return new CompleteSign().reason(CompleteSign.Reason.KEYS_NOT_FOUND);
        }
        
        Transaction fullTx = new Transaction(appConfig.getNetworkParameters(), signTO.fullSignedTransaction());
        //TODO: check fullTx
        
        //broadcast to network
        
        return new CompleteSign().setSuccess();
    }
    
    
    /*public SignTO signXX(@RequestBody SignTO signTO) {
        final List<ECKey> keys = clientKeyService.getECKeysByClientPublicKey(signTO.clientPublicKey());
        if(keys == null || keys.size() !=2) {
                return new SignTO().reason(SignTO.Reason.KEYS_NOT_FOUND);
        }
        final Script redeemScript = ScriptBuilder.createP2SHOutputScript(2, keys);
        Address p2shAddressFrom = redeemScript.getToAddress(appConfig.getNetworkParameters());
        if(signTO.amountToSpend() == null) {
            return new SignTO().reason(SignTO.Reason.AMOUNT_EMPTY);
        }
        Coin amountToSpend = Coin.valueOf(signTO.amountToSpend());
        if(signTO.p2shAddress() == null || signTO.p2shAddress().isEmpty()) {
            return new SignTO().reason(SignTO.Reason.ADDRESS_EMPTY);
        }
        Address p2shAddressTo = new Address(appConfig.getNetworkParameters(), Base64.getDecoder().decode(signTO.p2shAddress()));
        if(!clientKeyService.containsP2SH(p2shAddressFrom)) {
            return new SignTO().reason(SignTO.Reason.ADDRESS_UNKNOWN);
        }
        
        //get all outputs from the BT network
        List<TransactionOutput> outputs = walletService.getOutputs(p2shAddressFrom);
        //in addition, get the outputs from previous TX, possibly not yet published in the BT network
        outputs.addAll(transactionService.pendingOutputs());
        //TODO: check if the outputs does not exceed the lock time of the refund tx
        //TODO: check if outputs not already spent (double spending)
        //now we have all valid outputs and we know its before the refund lock time gets valid. So we can sign this tx
        
        final Transaction tx = new Transaction(appConfig.getNetworkParameters());
        Coin remainingAmount = Coin.ZERO;
        for(TransactionOutput output:outputs) {
            tx.addInput(output);
            remainingAmount = remainingAmount.add(output.getValue());
        }
        remainingAmount = remainingAmount.subtract(Transaction.REFERENCE_DEFAULT_MIN_TX_FEE);
        if(amountToSpend.isGreaterThan(remainingAmount)) {
            return new SignTO().reason(SignTO.Reason.NOT_ENOUGH_COINS);
        }
        
        tx.addOutput(amountToSpend, p2shAddressTo); //to recipient
        tx.addOutput(remainingAmount, p2shAddressFrom); //back to sender
        //rest is tx fee
        //TODO: add outputs to DB to pendingOutputs that will become valid, make sure these p2sh address came from us!
        if(clientKeyService.containsP2SH(p2shAddressTo)) {
           //add to pending output 
        }
        //TODO: update spentoutputs -> all inputs of this tx are now unspendable (double spending)
        //TODO: this and the above double spending must be in a transaction!!
        
        //sign
        for(int i=0;i<tx.getInputs().size();i++) {
            final Sha256Hash sighash = tx.hashForSignature(i, redeemScript, Transaction.SigHash.ALL, false);
            final TransactionSignature serverSignature = new TransactionSignature(keys.get(1).sign(sighash), Transaction.SigHash.ALL, false);
            
            List<TransactionSignature> l = new ArrayList<>();
                
            final TransactionSignature clientSignature = new TransactionSignature(
                new BigInteger(signTO.clientSignatures().get(i).clientSignatureR()), 
                    new BigInteger(signTO.clientSignatures().get(i).clientSignatureS()));
                
                l.add(clientSignature);
                l.add(serverSignature);
                final Script refundTransactionInputScript = ScriptBuilder.createP2SHMultiSigInputScript(l, redeemScript);
                tx.getInput(i).setScriptSig(refundTransactionInputScript);
                //refundTransaction.getInput(i).verify();
            }
        
        //TODO: create two new refunds with the new values
        
        return null; //return tx, two refunds!
    }*/

    public static Pair<PrepareHalfSignTO, Pair<Transaction,List<TransactionSignature>>> createHalfSignedTx(
            NetworkParameters params, List<TransactionOutput> outputs, Address p2shAddressFrom, 
            Address p2shAddressTo, Coin amountToSpend, Script redeemScript, ECKey serverKey, 
            List<TransactionSignature> clientSigs) {
        
        
        final Transaction tx = new Transaction(params);
        Coin remainingAmount = Coin.ZERO;
        for(TransactionOutput output:outputs) {
            Address a = output.getAddressFromP2SH(params);
            if(a!=null && a.equals(redeemScript.getToAddress(params))) {
                tx.addInput(output);
                remainingAmount = remainingAmount.add(output.getValue());
            }
        }
        remainingAmount = remainingAmount.subtract(Transaction.REFERENCE_DEFAULT_MIN_TX_FEE);
        if(amountToSpend.isGreaterThan(remainingAmount)) {
            return new Pair<>(new PrepareHalfSignTO().reason(PrepareHalfSignTO.Reason.NOT_ENOUGH_COINS), null);
        }
        remainingAmount = remainingAmount.subtract(amountToSpend);
        tx.addOutput(amountToSpend, p2shAddressTo); //to recipient
        tx.addOutput(remainingAmount, p2shAddressFrom); //back to sender
        //rest is tx fee
        //TODO: add outputs to DB to pendingOutputs that will become valid, make sure these p2sh address came from us!
        //if(clientKeyService.containsP2SH(p2shAddressTo)) {
           //add to pending output 
        //}
        //TODO: update spentoutputs -> all inputs of this tx are now unspendable (double spending)
        //TODO: this and the above double spending must be in a transaction!!
        
        //sign
        List<TransactionSignature> sigs = new ArrayList<>();
        for(int i=0;i<tx.getInputs().size();i++) {
            final Sha256Hash sighash = tx.hashForSignature(i, redeemScript, Transaction.SigHash.ALL, false);
            final TransactionSignature serverSignature = new TransactionSignature(serverKey.sign(sighash), Transaction.SigHash.ALL, false);
            
            List<TransactionSignature> l = new ArrayList<>();
            
            //not known yet, fill with zero
            final TransactionSignature clientSignature;
            if(clientSigs == null) {
                clientSignature = new TransactionSignature(BigInteger.ZERO, BigInteger.ZERO);
            } else {
                clientSignature = clientSigs.get(i);
            }
            
            //TODO: does order matter?
            
            l.add(serverSignature);
            l.add(clientSignature);
            sigs.add(serverSignature);
            final Script refundTransactionInputScript = ScriptBuilder.createP2SHMultiSigInputScript(l, redeemScript);
            tx.getInput(i).setScriptSig(refundTransactionInputScript);
        }
        return new Pair<>(new PrepareHalfSignTO().setSuccess(), new Pair<>(tx, sigs));
    }

    

    private List<TransactionOutPoint> deserialize(List<byte[]> transactionOutputs) {
        List<TransactionOutPoint> retVal = new ArrayList<>();
        for(byte[] b:transactionOutputs) {
            retVal.add(new TransactionOutPoint(appConfig.getNetworkParameters(), b, 0));
        }
        return retVal;
    }
    
    private List<TransactionSignature> deserialize2(List<RefundP2shTO.TxSig> refundSignatures) {
        List<TransactionSignature> retVal = new ArrayList<>();
        for(RefundP2shTO.TxSig b:refundSignatures) {
            retVal.add(new TransactionSignature(new BigInteger(b.clientSignatureR()), new BigInteger(b.clientSignatureS())));
        }
        return retVal;
    }

    private List<RefundP2shTO.TxSig> serialize(List<TransactionSignature> element1) {
        List<RefundP2shTO.TxSig> retVal = new ArrayList<>();
        for(TransactionSignature sig:element1) {
            retVal.add(new RefundP2shTO.TxSig().clientSignatureR(sig.r.toString()).clientSignatureS(sig.s.toString()));
        }
        return retVal;
    }
}
