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
import ch.uzh.csg.coinblesk.server.utils.ApiVersion;
import com.coinblesk.json.BalanceTO;
import com.coinblesk.json.BaseTO;
import com.coinblesk.json.KeyTO;
import com.coinblesk.json.RefundTO;
import com.coinblesk.json.SignTO;
import com.coinblesk.json.Type;
import com.coinblesk.json.VerifyTO;
import com.coinblesk.util.BitcoinUtils;
import com.coinblesk.util.CoinbleskException;
import com.coinblesk.util.InsuffientFunds;
import com.coinblesk.util.Pair;
import com.coinblesk.util.SerializeUtils;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;

import java.util.List;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.AddressFormatException;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionBroadcast;
import org.bitcoinj.core.TransactionConfidence;
import org.bitcoinj.core.TransactionOutPoint;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.crypto.TransactionSignature;
import org.bitcoinj.script.Script;
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
@ApiVersion({"v1", ""})
public class PaymentController {

    private final static Logger LOG = LoggerFactory.getLogger(PaymentController.class);

    @Autowired
    private AppConfig appConfig;

    @Autowired
    private WalletService walletService;
    
    @Autowired
    private KeyService keyService;
  
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
        LOG.debug("Register clientHash for {}", SerializeUtils.bytesToHex(keyTO.publicKey()));
        try {
            //no input checking as input may not be signed
            final NetworkParameters params = appConfig.getNetworkParameters();
            final byte[] clientPublicKey = keyTO.publicKey();
            final ECKey serverEcKey = new ECKey();
            final List<ECKey> keys = new ArrayList<>(2);
            keys.add(ECKey.fromPublicOnly(clientPublicKey));
            keys.add(serverEcKey);
            //2-of-2 multisig
            final Script script = BitcoinUtils.createP2SHOutputScript(2, keys);
            final Address p2shAddressClient = script.getToAddress(params);

            final Pair<Boolean, Keys> retVal = keyService.storeKeysAndAddress(clientPublicKey,
                    p2shAddressClient, serverEcKey.getPubKey(),
                    serverEcKey.getPrivKeyBytes());
            final KeyTO serverKeyTO = new KeyTO();
            if (retVal.element0()) {
                serverKeyTO.publicKey(serverEcKey.getPubKey());
                walletService.addWatching(script);
                return serverKeyTO.setSuccess();
            } else {
                serverKeyTO.publicKey(retVal.element1().serverPublicKey());
                return serverKeyTO.type(Type.KEY_ALREADY_EXISTS);
            }
        } catch (Exception e) {
            LOG.error("register keys error", e);
            return new KeyTO()
                    .type(Type.SERVER_ERROR)
                    .message(e.getMessage());
        }
    }
    
    @RequestMapping(value = {"/balance", "/b"}, method = RequestMethod.GET,
            consumes = "application/json; charset=UTF-8",
            produces = "application/json; charset=UTF-8")
    @ResponseBody
    public BalanceTO balance(@RequestBody BalanceTO keyTO) {
        LOG.debug("Balance clientHash for {}", SerializeUtils.bytesToHex(keyTO.publicKey()));
        try {
            final BalanceTO error = checkInput(keyTO); 
            if(error != null) {
                return error;
            }
            final NetworkParameters params = appConfig.getNetworkParameters();
            final List<ECKey> keys = keyService.getPublicECKeysByClientPublicKey(keyTO.publicKey());
            final Script script = BitcoinUtils.createP2SHOutputScript(2, keys);
            final Address p2shAddressFrom = script.getToAddress(params);
            List<TransactionOutput> outputs = walletService.verifiedOutputs(params, p2shAddressFrom);
            LOG.debug("{Prepare} nr. of outputs from network {} for {}. Full list: {}", outputs.size(), "tdb", outputs);
            long total = 0;
            for(TransactionOutput transactionOutput:outputs) {
                total += transactionOutput.getValue().value;
            }
            return new BalanceTO().balance(total);

        } catch (Exception e) {
            LOG.error("register keys error", e);
            return new BalanceTO()
                    .type(Type.SERVER_ERROR)
                    .message(e.getMessage());
        }
    }

    @RequestMapping(value = {"/refund", "/r"}, method = RequestMethod.POST,
            consumes = "application/json; charset=UTF-8",
            produces = "application/json; charset=UTF-8")
    @ResponseBody
    public RefundTO refund(@RequestBody RefundTO refundTO) {
        LOG.debug("Refund for {}", SerializeUtils.bytesToHex(refundTO.clientPublicKey()));
        try {
            final RefundTO error = checkInput(refundTO); 
            if(error != null) {
                return error;
            }
            final List<ECKey> keys = keyService.getECKeysByClientPublicKey(refundTO.clientPublicKey());
            if (keys == null || keys.size() != 2) {
                return new RefundTO().type(Type.KEYS_NOT_FOUND);
            }
            final NetworkParameters params = appConfig.getNetworkParameters();
            final ECKey serverKey = keys.get(1);
            final ECKey clientKey = keys.get(0);
            final Script redeemScript = BitcoinUtils.createRedeemScript(2, keys);
            //this is how the client sees the tx
            final Transaction refundTransaction = new Transaction(params, refundTO.refundTransaction());
            //TODO: check client setting for locktime
            List<TransactionSignature> clientSigs = SerializeUtils.deserializeSignatures(refundTO.clientSignatures());
            
            //now we can check the client sigs
            if(!SerializeUtils.verifyTxSignatures(refundTransaction, clientSigs, redeemScript, clientKey)) {
                LOG.debug("{Refund} signature mismatch for tx {} with sigs {}", refundTransaction, clientSigs);
                return new RefundTO().type(Type.SIGNATURE_ERROR);
            } else {
                LOG.debug("{Refund} signature good! for tx {} with sigs", refundTransaction, clientSigs);
            }
            
            Collections.sort(keys,ECKey.PUBKEY_COMPARATOR);
            
            List<TransactionSignature> serverSigs = BitcoinUtils.partiallySign(refundTransaction, redeemScript, serverKey);
            boolean clientFirst = BitcoinUtils.clientFirst(keys, clientKey);
            BitcoinUtils.applySignatures(refundTransaction, redeemScript, clientSigs, serverSigs, clientFirst);
            refundTO.serverSignatures(SerializeUtils.serializeSignatures(serverSigs));
            //TODO: enable
            //refundTransaction.verify(); make sure those inputs are from the known p2sh address (min conf)
            byte[] refundTx = refundTransaction.unsafeBitcoinSerialize();
            keyService.addRefundTransaction(refundTO.clientPublicKey(), refundTx);
            return new RefundTO()
                    .setSuccess()
                    .refundTransaction(refundTx)
                    .serverSignatures(SerializeUtils.serializeSignatures(serverSigs));
        } catch (Exception e) {
            LOG.error("register keys error", e);
            return new RefundTO()
                    .type(Type.SERVER_ERROR)
                    .message(e.getMessage());
        }
    }
    
    @RequestMapping(value = {"/sign", "/s"}, method = RequestMethod.POST,
            consumes = "application/json; charset=UTF-8",
            produces = "application/json; charset=UTF-8")
    @ApiVersion("v2")
    @ResponseBody
    public SignTO sign(@RequestBody SignTO input) {
        LOG.debug("Sign for {}", SerializeUtils.bytesToHex(input.clientPublicKey()));
        try {
            final SignTO error = checkInput(input); 
            if(error != null) {
                return error;
            }
            final NetworkParameters params = appConfig.getNetworkParameters();
            final List<ECKey> keys = keyService.getECKeysByClientPublicKey(input.clientPublicKey());
            if (keys == null || keys.size() != 2) {
                return new SignTO().type(Type.KEYS_NOT_FOUND);
            }

            final ECKey serverKey = keys.get(1);
            final Script redeemScript = BitcoinUtils.createRedeemScript(2, keys);
            final Script p2SHOutputScript = BitcoinUtils.createP2SHOutputScript(2, keys);
            Collections.sort(keys,ECKey.PUBKEY_COMPARATOR);
            final Address p2shAddressFrom = p2SHOutputScript.getToAddress(params);
            
            final Transaction transaction;
            //choice 1 - full tx, without sigs
            if (input.transaction() != null) {
                LOG.debug("{sign} got transaction from input");
                transaction = new Transaction(appConfig.getNetworkParameters(), input.transaction());
            } 

            //choice 2 - send outpoints, coins, where to send btc to, and amount
            else if (input.outpointsCoinPair() != null && input.p2shAddressTo()!= null &&
                    input.amountToSpend() != 0) {
                //having the coins and the output allows us to create the tx without looking into our wallet
                final Address p2shAddressTo;
                try {
                    p2shAddressTo = new Address(params, input.p2shAddressTo());
                } catch (AddressFormatException e) {
                    LOG.debug("{sign} empty address for");
                    return new SignTO().type(Type.ADDRESS_EMPTY).message(e.getMessage());
                }
                
                //we now get from the client the outpoints for the refund tx (including hash)
                final List<Pair<TransactionOutPoint, Coin>> refundClientPoints = SerializeUtils
                        .deserializeOutPointsCoin(
                                params, input.outpointsCoinPair());
               
                try {
                    transaction = BitcoinUtils.createTx(params, refundClientPoints, redeemScript, p2shAddressFrom,
                        p2shAddressTo, input.amountToSpend());
                } catch (CoinbleskException e) {
                    LOG.warn("{sign} could not create tx", e);
                    return new SignTO().type(Type.SERVER_ERROR).message(e.getMessage());
                } catch (InsuffientFunds e) {
                    LOG.debug("{sign} not enough coins or amount too small");
                    return new SignTO().type(Type.NOT_ENOUGH_COINS);
                }
            } 
            
            //wrong choice
            else {
                return new SignTO().type(Type.INPUT_MISMATCH);
            }
            
            final List<TransactionSignature> serverSigs = BitcoinUtils
                    .partiallySign(transaction, redeemScript, serverKey);
            
            final byte[] serializedTransaction = transaction.unsafeBitcoinSerialize();
            keyService.addTransaction(input.clientPublicKey(), serializedTransaction);

            return new SignTO()
                    .setSuccess()
                    .serverSignatures(SerializeUtils.serializeSignatures(serverSigs));

        } catch (Exception e) {
            LOG.error("Sign keys error", e);
            return new SignTO()
                    .type(Type.SERVER_ERROR)
                    .message(e.getMessage());
        }
    }

    @RequestMapping(value = {"/verify", "/v"}, method = RequestMethod.POST,
            consumes = "application/json; charset=UTF-8",
            produces = "application/json; charset=UTF-8")
    @ApiVersion("v2")
    @ResponseBody
    public VerifyTO verify(@RequestBody VerifyTO input) {
        final long start = System.currentTimeMillis();
        LOG.debug("{verify} start");
        try {
            final VerifyTO error = checkInput(input); 
            if(error != null) {
                return error;
            }
            final String clientId = SerializeUtils.bytesToHex(input.clientPublicKey());
            final List<ECKey> keys = keyService.getECKeysByClientPublicKey(input.clientPublicKey());
            if (keys == null || keys.size() != 2) {
                return new VerifyTO().type(Type.KEYS_NOT_FOUND);
            }
            final Script redeemScript = BitcoinUtils.createRedeemScript(2, keys);

            final Transaction fullTx = new Transaction(appConfig.getNetworkParameters(), input
                    .fullSignedTransaction());
            LOG.debug("{verify:{}} client {} received {}", (System.currentTimeMillis() - start), clientId, fullTx);

            
            //ok, refunds are locked or no refund found
            fullTx.getConfidence().setSource(TransactionConfidence.Source.SELF);
            final Transaction connectedFullTx = walletService.receivePending(fullTx);
            broadcast(fullTx);
            
            
            LOG.debug("{verify:{}} broadcast done {}", (System.currentTimeMillis() - start), clientId);
            
            if (keyService.isTransactionInstant(input.clientPublicKey(), redeemScript, connectedFullTx)) {
                LOG.debug("{verify:{}} instant payment OK for {}", (System.currentTimeMillis() - start), clientId);
                VerifyTO output = new VerifyTO().setSuccess();
                return output;
            } else {
                LOG.debug("{verify:{}} instant payment NOT OK for {}", (System.currentTimeMillis() - start), clientId);
                VerifyTO output = new VerifyTO().type(Type.NO_INSTANT_PAYMENT);
                return output;
            }

        } catch (Exception e) {
            LOG.error("{verify} register keys error: ", e);
            return new VerifyTO()
                    .type(Type.SERVER_ERROR)
                    .message(e.getMessage());
        }
    }

    private void broadcast(final Transaction fullTx) {
        //broadcast immediately
        final TransactionBroadcast broadcast = walletService.peerGroup().broadcastTransaction(fullTx);
        Futures.addCallback(broadcast.future(), new FutureCallback<Transaction>() {
            @Override
            public void onSuccess(Transaction transaction) {
                LOG.debug("success, transaction is out {}", fullTx.getHash());
            }

            @Override
            public void onFailure(Throwable throwable) {
                LOG.error("failed, transaction is out " + fullTx.getHash(), throwable);
                try {
                    Thread.sleep(60 *1000);
                    broadcast(fullTx);
                } catch (InterruptedException ex) {
                    LOG.debug("don't wait for tx {}", fullTx.getHash());
                }
                
            }
        });
        
    }
    
    private static <K extends BaseTO> K newInstance(K k, Type returnType) {
        try {
            BaseTO b = k.getClass().newInstance();
            b.type(returnType);
            return (K) b;
        } catch (InstantiationException | IllegalAccessException ex) {
            LOG.error("cannot create instance", ex);
        }
        return null;
    }
    
    private static <K extends BaseTO> K checkInput(final K input) {
        
        if (!input.isInputSet()) {
            return newInstance(input, Type.INPUT_MISMATCH);
        }

        //chekc if the client sent us a time which is way too old (1 day)
        final Calendar fromClient = Calendar.getInstance();
        fromClient.setTime(new Date(input.currentDate()));

        final Calendar fromServerDayBefore = Calendar.getInstance();
        fromServerDayBefore.add(Calendar.DAY_OF_YEAR, -1);

        if (fromClient.before(fromServerDayBefore)) {
            return newInstance(input, Type.TIME_MISMATCH);
        }

        final Calendar fromServerDayAfter = Calendar.getInstance();
        fromServerDayAfter.add(Calendar.DAY_OF_YEAR, 1);

        if (fromClient.after(fromServerDayAfter)) {
            return newInstance(input, Type.TIME_MISMATCH);

        }

        if (!SerializeUtils.verifySig(input, ECKey.fromPublicOnly(input.clientPublicKey()))) {
            return newInstance(input, Type.JSON_SIGNATURE_ERROR);

        }
        return null;
    } 
}
