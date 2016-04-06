/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.uzh.csg.coinblesk.server.controller;

import static ch.uzh.csg.coinblesk.server.controller.PaymentController.filter;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.AddressFormatException;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionBroadcast;
import org.bitcoinj.core.TransactionConfidence;
import org.bitcoinj.core.TransactionInput;
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

import com.coinblesk.json.SignTO;
import com.coinblesk.json.Type;
import com.coinblesk.json.VerifyTO;
import com.coinblesk.util.BitcoinUtils;
import com.coinblesk.util.Pair;
import com.coinblesk.util.SerializeUtils;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;

import ch.uzh.csg.coinblesk.server.config.AppConfig;
import ch.uzh.csg.coinblesk.server.service.KeyService;
import ch.uzh.csg.coinblesk.server.service.TransactionService;
import ch.uzh.csg.coinblesk.server.service.WalletService;

/**
 *
 * @author Alessandro Di Carli
 * @author Thomas Bocek
 *
 */
@RestController
@RequestMapping(value = {"/full-payment", "/f"})
public class PaymentFullTxController {

    private final static Logger LOG = LoggerFactory.getLogger(PaymentFullTxController.class);

    @Autowired
    private AppConfig appConfig;

    @Autowired
    private WalletService walletService;

    @Autowired
    private KeyService keyService;
    
    @Autowired
    private TransactionService transactionService;

    /* SIGN ENDPOINT CODE STARTS HERE */
    @RequestMapping(value = {"/sign", "/s"}, method = RequestMethod.POST,
            consumes = "application/json; charset=UTF-8",
            produces = "application/json; charset=UTF-8")
    @ResponseBody
    public SignTO sign(@RequestBody SignTO input) {
        LOG.debug("Sign for {}", SerializeUtils.bytesToHex(input.clientPublicKey()));
        try {
            final NetworkParameters params = appConfig.getNetworkParameters();
            final List<ECKey> keys = keyService.getECKeysByClientPublicKey(input.clientPublicKey());
            if (keys == null || keys.size() != 2) {
                return new SignTO().type(Type.KEYS_NOT_FOUND);
            }

            final ECKey serverKey = keys.get(1);
            final ECKey clientKey = keys.get(0);
            final Script redeemScript = BitcoinUtils.createRedeemScript(2, keys);
            final Script p2SHOutputScript = BitcoinUtils.createP2SHOutputScript(2, keys);
            Collections.sort(keys,ECKey.PUBKEY_COMPARATOR);
            final Address p2shAddressFrom = p2SHOutputScript.getToAddress(params);
            

            final List<TransactionSignature> clientSignatures;
            final Transaction transaction;
            //choice 1
            if (input.transaction() != null) {
                clientSignatures = null;
                LOG.debug("{sign} got transaction from input");
                transaction = new Transaction(appConfig.getNetworkParameters(), input.transaction());
            } 

            //choice 2
            else if (input.amountToSpend() != 0 && input.p2shAddressTo() != null) {
                clientSignatures = null;
                final Coin amountToSpend = Coin.valueOf(input.amountToSpend());
                final Address p2shAddressTo;
                try {
                	p2shAddressTo = Address.fromBase58(params, input.p2shAddressTo());
                } catch (AddressFormatException e) {
                    LOG.debug("{sign} empty address for");
                    return new SignTO().type(Type.ADDRESS_EMPTY).message(e.getMessage());
                }

                //get all outputs from the BT network
                List<TransactionOutput> outputs = walletService.verifiedOutputs(params, p2shAddressFrom);
                LOG.debug("{sign} nr. of outputs from network {}. Full list: {}", outputs.size(), outputs);

                //bloom filter is optianal, if not provided all found outputs are used
                if (input.bloomFilter() != null) {
                    outputs = filter(params, outputs, input.bloomFilter());
                }
                LOG.debug("{sign} nr. of outputs after bured outputs {}. Full list {}", outputs.size(),
                        outputs);

                transaction = BitcoinUtils.createTx(
                        params, outputs, p2shAddressFrom,
                        p2shAddressTo, amountToSpend.value);

                if (transaction == null) {
                    LOG.debug("{sign} not enough coins for");
                    return new SignTO().type(Type.NOT_ENOUGH_COINS);
                }
            } 

            //choice 3
            else if (input.refundClientOutpointsCoinPair() != null && input.refundSignaturesClient() != null) {
                
                
                //we now get from the client the outpoints for the refund tx (including hash)
                List<Pair<TransactionOutPoint, Coin>> refundClientPoints = SerializeUtils
                        .deserializeOutPointsCoin(
                                params, input.refundClientOutpointsCoinPair());
               

                //now get all the output we want the refund, for client this will be one entry, for merchant this
                //will return multiple entries
                List<TransactionOutput> clientWalletOutputs = walletService.verifiedOutputs(params,
                        p2shAddressFrom);
                LOG.debug("{sign} nr. of outputs from network {}. Full list {}",
                        clientWalletOutputs.size(), clientWalletOutputs);
                //add/remove pending 

                
                //remove pending/burned outputs
                //remove burned outputs, either we do it ourselfs, or the client can provide an optional bloomfilter
                //if not we can check the burned output which will be the ones used for the refund
                List<TransactionOutPoint> to = keyService.burnedOutpoints(clientKey.getPubKey());
                if (input.bloomFilter() != null) {
                    clientWalletOutputs = filter(params, clientWalletOutputs, input.bloomFilter());
                } else {
                    removeBurnedOutputs(clientWalletOutputs, to);
                }
                LOG.debug("{sign} nr. of outputs after bured outputs {}. Full list {}",
                        clientWalletOutputs.size(), clientWalletOutputs);

                //the outpoints need to point to the original tx
                if (checkBurnedOutpoints(to, refundClientPoints)) {
                    return new SignTO().type(Type.BURNED_OUTPUTS);
                }

                List<TransactionInput> preBuiltInupts = BitcoinUtils.convertPointsToInputs(params,
                        refundClientPoints, redeemScript);

                LOG.debug("{sign} going for the following outputs in {} and outpoints {}",
                        clientWalletOutputs, preBuiltInupts);

                transaction = BitcoinUtils.generateUnsignedRefundTx(
                        params, clientWalletOutputs, preBuiltInupts,
                        clientKey.toAddress(params), redeemScript, walletService.refundLockTime());

                if (transaction == null) {
                    LOG.debug("{sign} not enough coins");
                    return new SignTO().type(Type.NOT_ENOUGH_COINS);
                }

                if(input.refundSignaturesClient() != null) {
                    //Client refund sigs are here, we can do a full refund
                    clientSignatures = SerializeUtils.deserializeSignatures(input
                        .refundSignaturesClient());                    
                    //now we can check the client sigs
                    if (!SerializeUtils.verifyTxSignatures(transaction, clientSignatures, redeemScript,
                            clientKey)) {
                        LOG.debug("{sign} signature mismatch with sigs {}", clientSignatures);
                        return new SignTO().type(Type.SIGNATURE_ERROR);
                    }
                    //now we could fully sign it and store it!
                } else {
                    clientSignatures = null;
                }
            } else {
                return new SignTO().type(Type.INPUT_MISMATCH);
            }
            
            final List<TransactionSignature> serverSigs = BitcoinUtils
                    .partiallySign(transaction, redeemScript, serverKey);
            
            //only for choice, we have now a fully signed refund in for the client
            if(clientSignatures!= null && serverSigs!=null) {
                boolean clientFirst = BitcoinUtils.clientFirst(keys, clientKey);
                BitcoinUtils.applySignatures(transaction, redeemScript, clientSignatures, serverSigs, clientFirst);
            }

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
    @ResponseBody
    public VerifyTO verify(@RequestBody VerifyTO input) {
        final long start = System.currentTimeMillis();
        final String clientId = SerializeUtils.bytesToHex(input.clientPublicKey());
        LOG.debug("{verify} {}", clientId);
        try {
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
            broadcastBlocking(fullTx);
            
            
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
            LOG.error("{verify} register keys error: " + clientId, e);
            return new VerifyTO()
                    .type(Type.SERVER_ERROR)
                    .message(e.getMessage());
        }
    }

    /* SIGN ENDPOINT CODE ENDS HERE */

    private void broadcastBlocking(final Transaction fullTx) {
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
                    broadcastBlocking(fullTx);
                } catch (InterruptedException ex) {
                    LOG.debug("don't wait for tx {}", fullTx.getHash());
                }
                
            }
        });
        
    }
    
    private static boolean checkBurnedOutpoints(List<TransactionOutPoint> to, List<Pair<TransactionOutPoint, Coin>> refundClientPoints) {
        //sanity check: client cannot give us burned outpoints
        for (TransactionOutPoint t : to) {
            for (Pair<TransactionOutPoint,Coin> p : refundClientPoints) {
                if (t.equals(p.element0())) {
                    return true;
                }
            }
        }
        return false;
    }
    
    private static void removeBurnedOutputs(List<TransactionOutput> clientWalletOutputs, List<TransactionOutPoint> to) {
        for(Iterator<TransactionOutput> i=clientWalletOutputs.iterator();i.hasNext();) {
            TransactionOutput output = i.next();
            for(TransactionOutPoint t:to) {
                if(output.getParentTransaction() == null) {
                    throw new IllegalStateException("cannot handle detached transaction outputs");
                }
                if(t.getHash().equals(output.getParentTransaction().getHash()) &&
                        output.getIndex() == t.getIndex()) {
                    i.remove();
                    break;
                }
            }
        }
    } 
}
