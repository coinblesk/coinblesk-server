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
import ch.uzh.csg.coinblesk.server.utils.LruCache;
import com.coinblesk.json.BalanceTO;
import com.coinblesk.json.BaseTO;
import com.coinblesk.json.CompleteSignTO;
import com.coinblesk.json.KeyTO;
import com.coinblesk.json.PrepareFullTxTO;
import com.coinblesk.json.PrepareHalfSignTO;
import com.coinblesk.json.RefundP2shTO;
import com.coinblesk.json.RefundTO;
import com.coinblesk.json.Type;
import com.coinblesk.util.BitcoinUtils;
import com.coinblesk.util.Pair;
import com.coinblesk.util.SerializeUtils;
import com.coinblesk.util.SimpleBloomFilter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import javax.annotation.Nullable;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.AddressFormatException;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionBroadcast;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutPoint;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.crypto.TransactionSignature;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;
import org.bitcoinj.utils.Threading;
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
    private WalletService walletService;

    @Autowired
    private TransactionService transactionService;
    
    @Autowired
    private KeyService keyService;
    
    private static final Map<String, PrepareHalfSignTO> CACHE_PREPARE = Collections.synchronizedMap(new LruCache<>(1000));
    private static final Map<String, PrepareFullTxTO> CACHE_PREPARE_FULL = Collections.synchronizedMap(new LruCache<>(1000));
    private static final Map<String, RefundP2shTO> CACHE_REFUND = Collections.synchronizedMap(new LruCache<>(1000));
    private static final Map<String, CompleteSignTO> CACHE_COMPLETE = Collections.synchronizedMap(new LruCache<>(1000));

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
            final NetworkParameters params = appConfig.getNetworkParameters();
            final byte[] clientPublicKey = keyTO.publicKey();
            final ECKey serverEcKey = new ECKey();
            final List<ECKey> keys = new ArrayList<>(2);
            keys.add(ECKey.fromPublicOnly(clientPublicKey));
            keys.add(serverEcKey);
            //2-of-2 multisig
            final Script script = ScriptBuilder.createP2SHOutputScript(2, keys);
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
    public BalanceTO balance(@RequestBody KeyTO keyTO) {
        LOG.debug("Balance clientHash for {}", SerializeUtils.bytesToHex(keyTO.publicKey()));
        try {
            final NetworkParameters params = appConfig.getNetworkParameters();
            final List<ECKey> keys = keyService.getPublicECKeysByClientPublicKey(keyTO.publicKey());
            final Script script = ScriptBuilder.createP2SHOutputScript(2, keys);
            final Address p2shAddressFrom = script.getToAddress(params);
            List<TransactionOutput> outputs = walletService.unspentOutputs(params, p2shAddressFrom);
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
            final List<ECKey> keys = keyService.getECKeysByClientPublicKey(refundTO.clientPublicKey());
            if (keys == null || keys.size() != 2) {
                return new RefundTO().type(Type.KEYS_NOT_FOUND);
            }
            final NetworkParameters params = appConfig.getNetworkParameters();
            final ECKey serverKey = keys.get(1);
            final ECKey clientKey = keys.get(0);
            final Script redeemScript = ScriptBuilder.createRedeemScript(2, keys);
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
    
    @RequestMapping(value = {"/prepare-fulltx", "/t"}, method = RequestMethod.POST,
            consumes = "application/json; charset=UTF-8",
            produces = "application/json; charset=UTF-8")
    @ResponseBody
    public PrepareFullTxTO prepareFullTx(@RequestBody PrepareFullTxTO input) {
        
        final String clientId = SerializeUtils.bytesToHex(input.clientPublicKey());
        LOG.debug("{Prepare} sign half for {}", clientId);
        try {
            PrepareFullTxTO errorOrCache = checkInput(input, CACHE_PREPARE_FULL);
            if(errorOrCache != null) {
                LOG.debug("{Prepare} input error/caching {} for {}", errorOrCache.type(), clientId);
                return errorOrCache;
            }
            final List<ECKey> keys = keyService.getECKeysByClientPublicKey(input.clientPublicKey());
            if (keys == null || keys.size() != 2) {
                LOG.debug("{Prepare} keys not found for {}", clientId);
                return new PrepareFullTxTO().type(Type.KEYS_NOT_FOUND);
            }
            
            final NetworkParameters params = appConfig.getNetworkParameters();
            final ECKey serverKey = keys.get(1);
            final Script p2SHOutputScript = ScriptBuilder.createP2SHOutputScript(2, keys);
            final Address p2shAddressFrom = p2SHOutputScript.getToAddress(params);
            //this should never happen, check anyway
            if (!keyService.containsP2SH(p2shAddressFrom)) {
                LOG.debug("{Prepare} unknow address for {}", clientId);
                return new PrepareFullTxTO().type(Type.ADDRESS_UNKNOWN);
            }
            
            final Transaction tx = new Transaction(params, input.unsignedTransaction());
            LOG.debug("{Prepare} used tx {}: {}", clientId, tx);
            
            List<Pair<TransactionOutPoint, Integer>> burned = transactionService.burnOutputFromNewTransaction(
                    params, input.clientPublicKey(), tx.getInputs());
            walletService.addWatchingOutpointsForRemoval(burned);

            //Collections.sort(keys,ECKey.PUBKEY_COMPARATOR);
            final Script redeemScript = ScriptBuilder.createRedeemScript(2, keys);
            //sign the tx with the server keys
            List<TransactionSignature> serverTxSigs = BitcoinUtils.partiallySign(tx, redeemScript, serverKey);
            
            PrepareFullTxTO output = new PrepareFullTxTO().type(Type.SUCCESS)
                    .unsignedTransaction(tx.unsafeBitcoinSerialize())
                    .signatures(SerializeUtils.serializeSignatures(serverTxSigs));
            
            final String key = createKey(input);
            CACHE_PREPARE_FULL.put(key, output);
            return output;

        } catch (Exception e) {
            LOG.error("{Prepare} Register keys error: " + clientId, e);
            return new PrepareFullTxTO()
                    .type(Type.SERVER_ERROR)
                    .message(e.getMessage());
        }
    }

    @RequestMapping(value = {"/prepare", "/p"}, method = RequestMethod.POST,
            consumes = "application/json; charset=UTF-8",
            produces = "application/json; charset=UTF-8")
    @ResponseBody
    public PrepareHalfSignTO prepareHalfSign(@RequestBody PrepareHalfSignTO input) {
        
        final String clientId = SerializeUtils.bytesToHex(input.clientPublicKey());
        LOG.debug("{Prepare} sign half for {}", clientId);
        try {
            PrepareHalfSignTO errorOrCache = checkInput(input, CACHE_PREPARE);
            if(errorOrCache != null) {
                LOG.debug("{Prepare} input error/caching {} for {}", errorOrCache.type(), clientId);
                return errorOrCache;
            }
            final List<ECKey> keys = keyService.getECKeysByClientPublicKey(input.clientPublicKey());
            if (keys == null || keys.size() != 2) {
                LOG.debug("{Prepare} keys not found for {}", clientId);
                return new PrepareHalfSignTO().type(Type.KEYS_NOT_FOUND);
            }
            
            final NetworkParameters params = appConfig.getNetworkParameters();
            final ECKey serverKey = keys.get(1);
            final Script p2SHOutputScript = ScriptBuilder.createP2SHOutputScript(2, keys);
            final Address p2shAddressFrom = p2SHOutputScript.getToAddress(params);
            //this should never happen, check anyway
            if (!keyService.containsP2SH(p2shAddressFrom)) {
                LOG.debug("{Prepare} unknow address for {}", clientId);
                return new PrepareHalfSignTO().type(Type.ADDRESS_UNKNOWN);
            }
            
            final Coin amountToSpend = Coin.valueOf(input.amountToSpend());

            final Address p2shAddressTo;
            try {
                p2shAddressTo = new Address(params, input.p2shAddressTo());
            } catch (AddressFormatException e) {
                LOG.debug("{Prepare} empty address for {}", clientId);
                return new PrepareHalfSignTO().type(Type.ADDRESS_EMPTY).message(e.getMessage()); 
            }

            //get all outputs from the BT network
            List<TransactionOutput> outputs = walletService.unspentOutputs(params, p2shAddressFrom);
            LOG.debug("{Prepare} nr. of outputs from network {} for {}. Full list: {}", outputs.size(), clientId, outputs);
            //in addition, get the outputs from previous TX, possibly not yet published in the BT network
            
            //outputs.addAll(transactionService.approvedOutputs(params, p2shAddressFrom));
            //LOG.debug("{Prepare} nr. of outputs after approved {} for {}. Full list: {}", outputs.size(), clientId, outputs);
            
            //removed burned outputs
            //List<TransactionOutPoint> to = transactionService.burnedOutpoints(params, input.clientPublicKey());

            //bloom filter is optianal, if not provided all found outputs are used
            boolean filtered = false;
            if(input.bloomFilter() != null) {
                final List<TransactionOutput> filteredOutputs = filter(params, outputs, input.bloomFilter());       
                filtered = !filteredOutputs.equals(outputs);
                outputs = filteredOutputs;
            } else {
                //removeBurnedOutputs(outputs, to);
            }
            LOG.debug("{Prepare} nr. of outputs after bured outputs {} for {}. Full list {}", outputs.size(), clientId, outputs);
            
            final Transaction tx = BitcoinUtils.createTx(
                    params, outputs, p2shAddressFrom,
                    p2shAddressTo, amountToSpend.value);
            
            if (tx == null) {
                LOG.debug("{Prepare} not enough coins for {}", clientId);
                return new PrepareHalfSignTO().type(Type.NOT_ENOUGH_COINS);
            }
            
            LOG.debug("{Prepare} used tx {}: {}", clientId, tx);
            
            SimpleBloomFilter<byte[]> bloomFilter = new SimpleBloomFilter(0.001, outputs.size());
            for(TransactionOutput output: outputs) {
                bloomFilter.add(output.getOutPointFor().unsafeBitcoinSerialize());
            }
            
            List<Pair<TransactionOutPoint, Integer>> burned = transactionService.burnOutputFromNewTransaction(
                    params, input.clientPublicKey(), tx.getInputs());
            walletService.addWatchingOutpointsForRemoval(burned);

            //Collections.sort(keys,ECKey.PUBKEY_COMPARATOR);
            final Script redeemScript = ScriptBuilder.createRedeemScript(2, keys);
            //sign the tx with the server keys
            List<TransactionSignature> serverTxSigs = BitcoinUtils.partiallySign(tx, redeemScript, serverKey);
            
            PrepareHalfSignTO output = new PrepareHalfSignTO().type(filtered ? Type.SUCCESS_FILTERED : Type.SUCCESS)
                    .unsignedTransaction(tx.unsafeBitcoinSerialize())
                    .bloomFilter(bloomFilter.encode())
                    .signatures(SerializeUtils.serializeSignatures(serverTxSigs));
            
            final String key = createKey(input);
            CACHE_PREPARE.put(key, output);
            return output;

        } catch (Exception e) {
            LOG.error("{Prepare} Register keys error: " + clientId, e);
            return new PrepareHalfSignTO()
                    .type(Type.SERVER_ERROR)
                    .message(e.getMessage());
        }
    }
    
    
    
    @RequestMapping(value = {"/refund-p2sh", "/f"}, method = RequestMethod.POST,
            consumes = "application/json; charset=UTF-8",
            produces = "application/json; charset=UTF-8")
    @ResponseBody
    public RefundP2shTO refundToP2SH(@RequestBody RefundP2shTO input) {
        final String clientId = SerializeUtils.bytesToHex(input.clientPublicKey());
        LOG.debug("{RefundP2SH} {}", clientId);
        try {
            RefundP2shTO errorOrCache = checkInput(input, CACHE_REFUND);
            if(errorOrCache != null) {
                LOG.debug("{RefundP2SH} input error/caching {} for {}", errorOrCache.type(), clientId);
                return errorOrCache;
            }
            final List<ECKey> keys = keyService.getECKeysByClientPublicKey(input.clientPublicKey());
            if (keys == null || keys.size() != 2) {
                LOG.debug("{RefundP2SH} keys not found for {}", clientId);
                return new RefundP2shTO().type(Type.KEYS_NOT_FOUND);
            }
            
            final NetworkParameters params = appConfig.getNetworkParameters();
            //get client public key (identifier)
            
            final ECKey clientKey = keys.get(0);
            final ECKey serverKey = keys.get(1);
            final Script serverClientRedeemScript = ScriptBuilder.createP2SHOutputScript(2, keys);
            
            Collections.sort(keys,ECKey.PUBKEY_COMPARATOR);
            final Script redeemScript = ScriptBuilder.createRedeemScript(2, keys);
            final Address p2shAddress = serverClientRedeemScript.getToAddress(params);
            
            //we now get from the client the outpoints for the refund tx (including hash)
            List<Pair<TransactionOutPoint,Coin>> refundClientPoints = SerializeUtils.deserializeOutPointsCoin(
                    params, input.refundClientOutpointsCoinPair());
            //Client refund sigs are here, we can do a full refund
            List<TransactionSignature> refundSignatures = SerializeUtils.deserializeSignatures(input.refundSignaturesClient());
            
            //now get all the output we want the refund, for client this will be one entry, for merchant this
            //will return multiple entries
            List<TransactionOutput> clientWalletOutputs = walletService.unspentOutputs(params, p2shAddress);
            LOG.debug("{RefundP2SH} nr. of outputs from network {} for {}. Full list {}", clientWalletOutputs.size(), clientId, clientWalletOutputs);
            //add/remove pending 
            
            //clientWalletOutputs.addAll(transactionService.approvedOutputs(params, p2shAddress));
            //LOG.debug("{RefundP2SH} nr. of outputs after approve {} for {}. Full list {}", clientWalletOutputs.size(), clientId, clientWalletOutputs);
            
            //remove pending/burned outputs
            
            //remove burned outputs, either we do it ourselfs, or the client can provide an optional bloomfilter
            //if not we can check the burned output which will be the ones used for the refund
            List<TransactionOutPoint> to = transactionService.burnedOutpoints(params, clientKey.getPubKey());
            if(input.bloomFilter() != null) {
                clientWalletOutputs = filter(params, clientWalletOutputs, input.bloomFilter());       
            } else {
                removeBurnedOutputs(clientWalletOutputs, to);
            }
            LOG.debug("{RefundP2SH} nr. of outputs after bured outputs {} for {}. Full list {}", clientWalletOutputs.size(), clientId, clientWalletOutputs);
            
            
            //the outpoints need to point to the original tx
            if (checkBurnedOutpoints(to, refundClientPoints)) {
                return new RefundP2shTO().type(Type.BURNED_OUTPUTS);
            }
            
            List<TransactionInput> preBuiltInupts = BitcoinUtils.convertPointsToInputs(params, refundClientPoints, redeemScript);
            
            LOG.debug("{RefundP2SH} going for the following outputs in {} and outpoints {} for {}", 
                    clientWalletOutputs, preBuiltInupts, clientId);
            
            Transaction unsignedRefund = BitcoinUtils.generateUnsignedRefundTx(
                params, clientWalletOutputs, preBuiltInupts,
                clientKey.toAddress(params), redeemScript, walletService.refundLockTime());

            if (unsignedRefund == null) {
                LOG.debug("{RefundP2SH} not enough coins for {}", clientId);
                return new RefundP2shTO().type(Type.NOT_ENOUGH_COINS);
            }
            
            //now we can check the client sigs
            if(!SerializeUtils.verifyTxSignatures(unsignedRefund, refundSignatures, redeemScript, clientKey)) {
                LOG.debug("{RefundP2SH} signature mismatch with sigs {} for {}", refundSignatures, clientId);
                return new RefundP2shTO().type(Type.SIGNATURE_ERROR);
            }

            List<TransactionSignature> partiallySignedRefundServer = BitcoinUtils.partiallySign(
                    unsignedRefund, redeemScript, serverKey);
            boolean clientFirst = BitcoinUtils.clientFirst(keys, clientKey);
            BitcoinUtils.applySignatures(unsignedRefund, redeemScript, refundSignatures, partiallySignedRefundServer, clientFirst);
            byte[] refundTx = unsignedRefund.unsafeBitcoinSerialize();
            keyService.addRefundTransaction(input.clientPublicKey(), refundTx);
            //unsignedRefund is now fully signed
            RefundP2shTO output = new RefundP2shTO().setSuccess()
                .fullRefundTransaction(refundTx)
                .refundSignaturesServer(SerializeUtils.serializeSignatures(partiallySignedRefundServer));

            final String key = createKey(input);
            CACHE_REFUND.put(key, output);
            
            return output;

        } catch (Exception e) {
            LOG.error("{RefundP2SH} register keys error: " + clientId, e);
            return new RefundP2shTO()
                    .type(Type.SERVER_ERROR)
                    .message(e.getMessage());
        }
    }

    @RequestMapping(value = {"/complete-sign", "/s"}, method = RequestMethod.POST,
            consumes = "application/json; charset=UTF-8",
            produces = "application/json; charset=UTF-8")
    @ResponseBody
    public CompleteSignTO sign(@RequestBody CompleteSignTO input) {
        final String clientId = SerializeUtils.bytesToHex(input.clientPublicKey());
        LOG.debug("{CompleteSign} {}", clientId);
        try {
            CompleteSignTO errorOrCache = checkInput(input, CACHE_COMPLETE);
            if(errorOrCache != null) {
                LOG.debug("{CompleteSign} input error/caching {} for {}", errorOrCache.type(), clientId);
                return errorOrCache;
            }
            final List<ECKey> keys = keyService.getECKeysByClientPublicKey(input.clientPublicKey());
            if (keys == null || keys.size() != 2) {
                LOG.debug("{CompleteSign} keys not found for {}", clientId);
                return new CompleteSignTO().type(Type.KEYS_NOT_FOUND);
            }
            
            final NetworkParameters params = appConfig.getNetworkParameters();
            
            final Script serverClientRedeemScript = ScriptBuilder.createP2SHOutputScript(2, keys);
            final Address p2shAddressFrom = serverClientRedeemScript.getToAddress(params);
           
            final Address p2shAddressTo = new Address(params, input.p2shAddressTo());
            
            final Transaction fullTx = new Transaction(params, input.fullSignedTransaction());
            LOG.debug("{CompleteSign} client {} received {}", clientId, fullTx);
            
            //this includes approved tx
            Map<Sha256Hash, Transaction> copy = new HashMap<>(walletService.unspentTransactions(params));
            
            if(copy.containsKey(fullTx.getHash())) {
                LOG.debug("{CompleteSign} already have this TX for {}", clientId);
                return new CompleteSignTO().setSuccess();
            }
            //check if tx is valid, outputs not spent and script/sigantures are valid
            if(!SerializeUtils.verifyRefund(fullTx, copy)) {
                LOG.debug("{CompleteSign} verifyTx error for {}", clientId);
                return new CompleteSignTO().type(Type.INVALID_TX);
            }
            
            //check if outputs are timelocked and not expiring, if not timelocked, ok as well
            copy.put(fullTx.getHash(), fullTx);
            for(Transaction refund: keyService.findRefundTransaction(params, input.clientPublicKey())) {
                //check if refund is valid and could be cashed in
                if(SerializeUtils.verifyRefund(refund, copy)) {
                    long lockTimeRefund = refund.getLockTime();
                    long observedBlockTime = walletService.refundEarliestLockTime();
                    if(lockTimeRefund < observedBlockTime) {
                        LOG.debug("{CompleteSign} locktime of refund {} is about to expire, we see {}, renew locktime for {}",lockTimeRefund, observedBlockTime, clientId);
                        return new CompleteSignTO().type(Type.INVALID_LOCKTIME);
                    } else {
                        LOG.debug("{CompleteSign} locktime of refund {} is valid, we see {}, renew locktime for {}",lockTimeRefund, observedBlockTime, clientId);
                    }
                }
            }
            //ok, refunds are locked or no refund found
            broadcast(fullTx, clientId);
            
            if(transactionService.checkInstantTx(params, fullTx, input.clientPublicKey(), p2shAddressFrom,
                    p2shAddressTo)) {
                LOG.debug("{CompleteSign} instant payment OK for {}", clientId);
                CompleteSignTO output = new CompleteSignTO().setSuccess();
                final String key = createKey(input);
                CACHE_COMPLETE.put(key, output);
                return output;
            } else {
                LOG.debug("{CompleteSign} instant payment NOT OK for {}", clientId);
                CompleteSignTO output = new CompleteSignTO().type(Type.NO_INSTANT_PAYMENT);
                final String key = createKey(input);
                CACHE_COMPLETE.put(key, output);
                return output;
            }
            
        } catch (Exception e) {
            LOG.error("{CompleteSign} register keys error: "+clientId, e);
            return new CompleteSignTO()
                    .type(Type.SERVER_ERROR)
                    .message(e.getMessage());
        }
    }

    private void broadcast(final Transaction fullTx, final String clientId) {
        //broadcast immediately
        final TransactionBroadcast broadcast = walletService.peerGroup().broadcastTransaction(fullTx);
        broadcast.future().addListener(new Runnable() {
            @Override
            public void run() {
                try {
                    Transaction tx = broadcast.future().get();
                    LOG.debug("{CompleteSign} tx {} broadcasted for {}", tx, clientId);
                } catch (InterruptedException | ExecutionException ex) {
                    LOG.error("{CompleteSign} tx {} NOT broadcasted for {}", fullTx, clientId);
                    LOG.error("{CompleteSign} broadcast error", ex);
                }
            }
        }, Threading.USER_THREAD);
    }
    
    public static List<TransactionOutput> filter(NetworkParameters params, List<TransactionOutput> outputs, @Nullable byte[] rawBloomFilter) {
        final List<TransactionOutput> filteredOutputs = new ArrayList<>(outputs.size());    
        if(rawBloomFilter != null) {
            SimpleBloomFilter<byte[]> bloomFilter = new SimpleBloomFilter(rawBloomFilter);
            for(TransactionOutput output: outputs) {
                if(bloomFilter.contains(output.getOutPointFor().unsafeBitcoinSerialize())) {
                    filteredOutputs.add(output);
                    LOG.debug("bloomfilter adding output with outpoint: {}, {}", output, output.getOutPointFor());
                } else {
                    LOG.debug("no bloomfilter match for output with outpoint: {}, {}", output, output.getOutPointFor());
                }
            }
            return filteredOutputs;
        }
        return outputs;
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
    
    private static <K extends BaseTO> String createKey(K input) {
        return SerializeUtils.bytesToHexFull(input.clientPublicKey()) + input.currentDate();
    }
    
    private static <K extends BaseTO> K checkInput(K input, Map<String, K> CACHE) {
        final String key = createKey(input);
        final K baseTo = CACHE.get(key);
        if(baseTo != null) {
            return baseTo;
        }
        
        if (!input.isInputSet()) {
            return newInstance(input, Type.INPUT_MISMATCH);
        }

        //chekc if the client sent us a time which is way too old (1 day)
        Calendar fromClient = Calendar.getInstance();
        fromClient.setTime(new Date(input.currentDate()));

        Calendar fromServerDayBefore = Calendar.getInstance();
        fromServerDayBefore.add(Calendar.DAY_OF_YEAR, -1);

        if (fromClient.before(fromServerDayBefore)) {
            return newInstance(input, Type.TIME_MISMATCH);

        }

        Calendar fromServerDayAfter = Calendar.getInstance();
        fromServerDayAfter.add(Calendar.DAY_OF_YEAR, 1);

        if (fromClient.after(fromServerDayAfter)) {
            return newInstance(input, Type.TIME_MISMATCH);

        }

        if (!SerializeUtils.verifySig(input, ECKey.fromPublicOnly(input.clientPublicKey()))) {
            return newInstance(input, Type.JSON_SIGNATURE_ERROR);

        }
        return null;
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
