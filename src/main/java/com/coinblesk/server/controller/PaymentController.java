/*
 * Copyright 2016 The Coinblesk team and the CSG Group at University of Zurich
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.coinblesk.server.controller;

import com.coinblesk.server.config.AppConfig;
import com.coinblesk.server.entity.Keys;
import com.coinblesk.server.service.KeyService;
import com.coinblesk.server.service.TransactionService;
import com.coinblesk.server.service.WalletService;
import com.coinblesk.server.utils.ApiVersion;
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

    @Autowired
    private TransactionService txService;

    /**
     * Input is the KeyTO with the client public key. The server will create for this client public key its
     * own server keypair and return the server public key, or indicate an error in KeyTO (or via status
     * code). Make sure to check for isSuccess(). Internally the server will hash the client public key with
     * SHA-256 (UUID) and clients need to identify themselfs with this UUID for subsequent calls.
     *
     */
    @RequestMapping(value = {"/key-exchange", "/x"}, method = RequestMethod.POST,
            consumes = "application/json; charset=UTF-8",
            produces = "application/json; charset=UTF-8")
    @ResponseBody
    public KeyTO keyExchange(@RequestBody KeyTO keyTO) {
        final long start = System.currentTimeMillis();
        try {
            LOG.debug("{register} clientHash for {}", SerializeUtils.bytesToHex(keyTO.publicKey()));
            //no input checking as input may not be signed
            final byte[] clientPublicKey = keyTO.publicKey();
            final ECKey serverEcKey = new ECKey();
            final List<ECKey> keys = new ArrayList<>(2);
            keys.add(ECKey.fromPublicOnly(clientPublicKey));
            keys.add(serverEcKey);
            //2-of-2 multisig
            final Script script = BitcoinUtils.createP2SHOutputScript(2, keys);
            
            final Pair<Boolean, Keys> retVal = keyService.storeKeysAndAddress(clientPublicKey,
                    serverEcKey.getPubKey(),
                    serverEcKey.getPrivKeyBytes());
            final KeyTO serverKeyTO = new KeyTO();
            if (retVal.element0()) {
                serverKeyTO.publicKey(serverEcKey.getPubKey());
                walletService.addWatching(script);
                LOG.debug("{register}:{} done", (System.currentTimeMillis() - start));
                return serverKeyTO.setSuccess();
            } else {
                serverKeyTO.publicKey(retVal.element1().serverPublicKey());
                LOG.debug("{register}:{} keys already there", (System.currentTimeMillis() - start));
                return serverKeyTO.type(Type.KEY_ALREADY_EXISTS);
            }
        } catch (Exception e) {
            LOG.error("{register} keys error", e);
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
        final long start = System.currentTimeMillis();
        try {
            LOG.debug("{balance} clientHash for {}", SerializeUtils.bytesToHex(keyTO.publicKey()));
            final BalanceTO error = checkInput(keyTO);
            if (error != null) {
                return error;
            }
            final NetworkParameters params = appConfig.getNetworkParameters();
            final List<ECKey> keys = keyService.getPublicECKeysByClientPublicKey(keyTO.publicKey());
            final Script script = BitcoinUtils.createP2SHOutputScript(2, keys);
            final Address p2shAddressFrom = script.getToAddress(params);
            List<TransactionOutput> outputs = walletService.verifiedOutputs(params, p2shAddressFrom);
            LOG.debug("{balance} nr. of outputs from network {} for {}. Full list: {}", outputs.size(), "tdb",
                    outputs);
            long total = 0;
            for (TransactionOutput transactionOutput : outputs) {
                total += transactionOutput.getValue().value;
            }
            LOG.debug("{balance}:{} done", (System.currentTimeMillis() - start));
            return new BalanceTO().balance(total);

        } catch (Exception e) {
            LOG.error("{balance} keys error", e);
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
        final long start = System.currentTimeMillis();
        try {
            LOG.debug("{refund} for {}", SerializeUtils.bytesToHex(refundTO.clientPublicKey()));
            final RefundTO error = checkInput(refundTO);
            if (error != null) {
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
            final Transaction refundTransaction;
            
            //choice 1 - full refund tx
            if (refundTO.refundTransaction() != null) {
                    refundTransaction = new Transaction(params, refundTO.refundTransaction());
            } 
            //choice 2 - send outpoints, coins, where to send btc to, and amount
            else if (refundTO.outpointsCoinPair() != null && refundTO.lockTime() > 0 &&
                    refundTO.refundSendTo() != null) {
                final List<Pair<TransactionOutPoint, Coin>> refundClientPoints = SerializeUtils
                        .deserializeOutPointsCoin(
                                params, refundTO.outpointsCoinPair());
                try {
                    Address refundSendTo = new Address(params, refundTO.refundSendTo());
                    refundTransaction = BitcoinUtils.createRefundTx(params, refundClientPoints, redeemScript,
                            refundSendTo, refundTO.lockTime());
                } catch (AddressFormatException e) {
                    LOG.debug("{refund}:{} empty address for", (System.currentTimeMillis() - start));
                    return new RefundTO().type(Type.ADDRESS_EMPTY).message(e.getMessage());
                }
            }
            //wrong choice
            else {
                return new RefundTO().type(Type.INPUT_MISMATCH);
            }
            
            List<TransactionSignature> clientSigs = SerializeUtils.deserializeSignatures(refundTO
                    .clientSignatures());

            //now we can check the client sigs
            if (!SerializeUtils.verifyTxSignatures(refundTransaction, clientSigs, redeemScript, clientKey)) {
                LOG.debug("{refund} signature mismatch for tx {} with sigs {}", refundTransaction, clientSigs);
                return new RefundTO().type(Type.SIGNATURE_ERROR);
            } else {
                LOG.debug("{refund} signature good! for tx {} with sigs", refundTransaction, clientSigs);
            }
            //also check the server sigs, that the reedemscript has our public key
            

            Collections.sort(keys, ECKey.PUBKEY_COMPARATOR);

            List<TransactionSignature> serverSigs = BitcoinUtils
                    .partiallySign(refundTransaction, redeemScript, serverKey);
            boolean clientFirst = BitcoinUtils.clientFirst(keys, clientKey);
            BitcoinUtils.applySignatures(refundTransaction, redeemScript, clientSigs, serverSigs, clientFirst);
            refundTO.serverSignatures(SerializeUtils.serializeSignatures(serverSigs));
            //TODO: enable
            //refundTransaction.verify(); make sure those inputs are from the known p2sh address (min conf)
            byte[] refundTx = refundTransaction.unsafeBitcoinSerialize();
            txService.addTransaction(refundTO.clientPublicKey(), refundTx);
            LOG.debug("{refund}:{} done", (System.currentTimeMillis() - start));
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
        final long start = System.currentTimeMillis();
        try {
            LOG.debug("{sign} for {}", SerializeUtils.bytesToHex(input.clientPublicKey()));
            final SignTO error = checkInput(input);
            if (error != null) {
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
            Collections.sort(keys, ECKey.PUBKEY_COMPARATOR);
            final Address p2shAddressFrom = p2SHOutputScript.getToAddress(params);

            final Transaction transaction;
            //choice 1 - full tx, without sigs
            if (input.transaction() != null) {
                LOG.debug("{sign}:{} got transaction from input", (System.currentTimeMillis() - start));
                transaction = new Transaction(appConfig.getNetworkParameters(), input.transaction());
            } 
            //choice 2 - send outpoints, coins, where to send btc to, and amount
            else if (input.outpointsCoinPair() != null && input.p2shAddressTo() != null
                    && input.amountToSpend() != 0) {
                //having the coins and the output allows us to create the tx without looking into our wallet
                try {
                    transaction = createTx(params, input.p2shAddressTo(), p2shAddressFrom, input.outpointsCoinPair(), 
                            input.amountToSpend(), redeemScript);
                } catch (AddressFormatException e) {
                    LOG.debug("{sign}:{} empty address for", (System.currentTimeMillis() - start));
                    return new SignTO().type(Type.ADDRESS_EMPTY).message(e.getMessage());
                }  catch (CoinbleskException e) {
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
            txService.addTransaction(input.clientPublicKey(), serializedTransaction);
            LOG.debug("{sign}:{} done", (System.currentTimeMillis() - start));
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
        try {
            LOG.debug("{verif} for {}", SerializeUtils.bytesToHex(input.clientPublicKey()));
            final VerifyTO error = checkInput(input);
            if (error != null) {
                return error;
            }
            final NetworkParameters params = appConfig.getNetworkParameters();
            final String clientId = SerializeUtils.bytesToHex(input.clientPublicKey());
            final List<ECKey> keys = keyService.getECKeysByClientPublicKey(input.clientPublicKey());
            if (keys == null || keys.size() != 2) {
                return new VerifyTO().type(Type.KEYS_NOT_FOUND);
            }
            final ECKey clientKey = keys.get(0);
            final ECKey serverKey = keys.get(1);
            final Script redeemScript = BitcoinUtils.createRedeemScript(2, keys);
            final Script p2SHOutputScript = BitcoinUtils.createP2SHOutputScript(2, keys);
            Collections.sort(keys, ECKey.PUBKEY_COMPARATOR);
            final Address p2shAddressFrom = p2SHOutputScript.getToAddress(params);
            
            final Transaction fullTx;
            //choice 1 - full tx
            if (input.transaction() != null) {
                LOG.debug("{verif}:{} got transaction from input", (System.currentTimeMillis() - start));
                fullTx = new Transaction(appConfig.getNetworkParameters(), input.transaction());
                //TODO: verify that this was sent from us
            }
            //choice 2 - send outpoints, coins, where to send btc to, and amount
            else if (input.outpointsCoinPair() != null && input.p2shAddressTo() != null
                    && input.amountToSpend() != 0 && input.clientSignatures() != null &&
                    input.serverSignatures() != null) {
                try {
                    fullTx = createTx(params, input.p2shAddressTo(), p2shAddressFrom, input.outpointsCoinPair(), 
                            input.amountToSpend(), redeemScript);
                    List<TransactionSignature> clientSigs = SerializeUtils.deserializeSignatures(
                            input.clientSignatures());
                    List<TransactionSignature> severSigs = SerializeUtils.deserializeSignatures(
                            input.serverSignatures());
                    BitcoinUtils.applySignatures(fullTx, redeemScript, clientSigs, severSigs, true);
                    
                    if (!SerializeUtils.verifyTxSignatures(fullTx, clientSigs, redeemScript, clientKey)) {
                        LOG.debug("{verif} signature mismatch for client-sigs tx {} with sigs {}", fullTx, clientSigs);
                        return new VerifyTO().type(Type.SIGNATURE_ERROR);
                    } else {
                        LOG.debug("{verif} signature good! for client-sigs tx {} with sigs", fullTx, clientSigs);
                    }
                    
                    if (!SerializeUtils.verifyTxSignatures(fullTx, severSigs, redeemScript, serverKey)) {
                        LOG.debug("{verif} signature mismatch for server-sigs tx {} with sigs {}", fullTx, severSigs);
                        return new VerifyTO().type(Type.SIGNATURE_ERROR);
                    } else {
                        LOG.debug("{verif} signature good! for server-sigs tx {} with sigs", fullTx, clientSigs);
                    }
                    
                } catch (AddressFormatException e) {
                    LOG.debug("{verif}:{} empty address for", (System.currentTimeMillis() - start));
                    return new VerifyTO().type(Type.ADDRESS_EMPTY).message(e.getMessage());
                }  catch (CoinbleskException e) {
                    LOG.warn("{verif} could not create tx", e);
                    return new VerifyTO().type(Type.SERVER_ERROR).message(e.getMessage());
                } catch (InsuffientFunds e) {
                    LOG.debug("{verif} not enough coins or amount too small");
                    return new VerifyTO().type(Type.NOT_ENOUGH_COINS);
                }
            }
            //wrong choice
            else {
                return new VerifyTO().type(Type.INPUT_MISMATCH);
            }
            
            
            LOG.debug("{verif}:{} client {} received {}", (System.currentTimeMillis() - start), clientId,
                    fullTx);

            //ok, refunds are locked or no refund found
            fullTx.getConfidence().setSource(TransactionConfidence.Source.SELF);
            final Transaction connectedFullTx = walletService.receivePending(fullTx);
            broadcast(fullTx);

            LOG.debug("{verif}:{} broadcast done {}", (System.currentTimeMillis() - start), clientId);

            if (txService.isTransactionInstant(input.clientPublicKey(), redeemScript, connectedFullTx)) {
                LOG.debug("{verif}:{} instant payment OK for {}", (System.currentTimeMillis() - start),
                        clientId);
                VerifyTO output = new VerifyTO().setSuccess();
                return output;
            } else {
                LOG.debug("{verif}:{} instant payment NOT OK for {}", (System.currentTimeMillis() - start),
                        clientId);
                VerifyTO output = new VerifyTO().type(Type.NO_INSTANT_PAYMENT);
                return output;
            }

        } catch (Exception e) {
            LOG.error("{verif} register keys error: ", e);
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
                    Thread.sleep(60 * 1000);
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

    private static Transaction createTx(NetworkParameters params, String p2shAddressTo, Address p2shAddressFrom,
            List<Pair<byte[], Long>> outpointsCoinPair, long amountToSpend, Script redeemScript) 
            throws AddressFormatException, CoinbleskException, InsuffientFunds {
        final Address p2shAddressTo1 = new Address(params, p2shAddressTo);

        //we now get from the client the outpoints for the refund tx (including hash)
        final List<Pair<TransactionOutPoint, Coin>> refundClientPoints = SerializeUtils
                .deserializeOutPointsCoin(params, outpointsCoinPair);

        return BitcoinUtils.createTx(params, refundClientPoints, redeemScript,
                    p2shAddressFrom, p2shAddressTo1, amountToSpend);
    }
}
