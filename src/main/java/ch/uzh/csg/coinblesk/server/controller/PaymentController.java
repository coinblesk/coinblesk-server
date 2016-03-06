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
import com.coinblesk.json.PrepareHalfSignTO;
import com.coinblesk.json.RefundP2shTO;
import com.coinblesk.json.RefundTO;
import com.coinblesk.json.Type;
import com.coinblesk.util.BitcoinUtils;
import com.coinblesk.util.Pair;
import com.coinblesk.util.SerializeUtils;
import java.util.ArrayList;
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
import org.bitcoinj.core.BloomFilter;
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
    
    private static final Map<String, PrepareHalfSignTO> CACHE = Collections.synchronizedMap(new LruCache<String, PrepareHalfSignTO>(1000));

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
            //final Coin balance = walletService.balance(script);
            //return new BalanceTO().balance(balance.value);
            
            List<TransactionOutput> outputs = walletService.getOutputs(p2shAddressFrom);
            LOG.debug("{Prepare} nr. of outputs from network {} for {}. Full list: {}", outputs.size(), "tdb", outputs);
            //in addition, get the outputs from previous TX, possibly not yet published in the BT network
            outputs.addAll(transactionService.approvedOutputs(params, p2shAddressFrom));
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
            //this is how the client sees the tx
            final Transaction refundTransaction = new Transaction(params, refundTO.refundTransaction());
            //TODO: check client setting for locktime
            List<TransactionSignature> clientSigs = SerializeUtils.deserializeSignatures(refundTO.clientSignatures());
            Collections.sort(keys,ECKey.PUBKEY_COMPARATOR);
            final Script redeemScript = ScriptBuilder.createRedeemScript(2, keys);
            List<TransactionSignature> serverSigs = BitcoinUtils.partiallySign(refundTransaction, redeemScript, serverKey);
            boolean clientFirst = BitcoinUtils.clientFirst(keys, clientKey);
            BitcoinUtils.applySignatures(refundTransaction, redeemScript, clientSigs, serverSigs, clientFirst);
            refundTO.serverSignatures(SerializeUtils.serializeSignatures(serverSigs));
            //TODO: enable
            //refundTransaction.verify(); make sure those inputs are from the known p2sh address (min conf)
            byte[] refundTx = refundTransaction.unsafeBitcoinSerialize();
            keyService.addRefundTransaction(refundTO.clientPublicKey(), refundTx);
            return new RefundTO().setSuccess().refundTransaction(refundTx);
        } catch (Exception e) {
            LOG.error("register keys error", e);
            return new RefundTO()
                    .type(Type.SERVER_ERROR)
                    .message(e.getMessage());
        }
    }

    @RequestMapping(value = {"/prepare", "/p"}, method = RequestMethod.POST,
            consumes = "application/json; charset=UTF-8",
            produces = "application/json; charset=UTF-8")
    @ResponseBody
    public PrepareHalfSignTO prepareHalfSign(@RequestBody PrepareHalfSignTO prepareSignTO) {
        //caching is important, if a client requests with the same time, then the last result is returned
        //this will not add the outputs to the burned outputs
        final String key = SerializeUtils.bytesToHexFull(prepareSignTO.clientPublicKey()) + prepareSignTO.currentDate();
        PrepareHalfSignTO prepareHalfSignTO = CACHE.get(key);
        if(prepareHalfSignTO != null) {
            return prepareHalfSignTO;
        }
        final String clientId = SerializeUtils.bytesToHex(prepareSignTO.clientPublicKey());
        LOG.debug("{Prepare} sign half for {}", clientId);
        try {
            PrepareHalfSignTO errorStatus = checkInput(prepareSignTO, "p", keyService);
            if(errorStatus != null) {
                LOG.debug("{Prepare} input error {} for {}", errorStatus.type(), clientId);
                return errorStatus;
            }
            final List<ECKey> keys = keyService.getECKeysByClientPublicKey(prepareSignTO.clientPublicKey());
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
            
            final Coin amountToSpend = Coin.valueOf(prepareSignTO.amountToSpend());

            final Address p2shAddressTo;
            try {
                p2shAddressTo = new Address(params, prepareSignTO.p2shAddressTo());
            } catch (AddressFormatException e) {
                LOG.debug("{Prepare} empty address for {}", clientId);
                return new PrepareHalfSignTO().type(Type.ADDRESS_EMPTY).message(e.getMessage()); 
            }

            //get all outputs from the BT network
            List<TransactionOutput> outputs = walletService.getOutputs(p2shAddressFrom);
            LOG.debug("{Prepare} nr. of outputs from network {} for {}. Full list: {}", outputs.size(), clientId, outputs);
            //in addition, get the outputs from previous TX, possibly not yet published in the BT network
            outputs.addAll(transactionService.approvedOutputs(params, p2shAddressFrom));
            LOG.debug("{Prepare} nr. of outputs after approved {} for {}. Full list: {}", outputs.size(), clientId, outputs);
            
            //List<TransactionOutPoint> to = transactionService.burnedOutpoints(params, prepareSignTO.clientPublicKey());
            //these are 100% spent, as we have done a /complete-sign
            List<TransactionOutPoint> to = transactionService.spentOutputs(params, p2shAddressFrom);

            //bloom filter is optianal, if not provided all found outputs are used
            boolean filtered = false;
            if(prepareSignTO.bloomFilter() != null) {
                final List<TransactionOutput> filteredOutputs = filter(params, outputs, prepareSignTO.bloomFilter());       
                filtered = !filteredOutputs.equals(outputs);
                outputs = filteredOutputs;
            } else {
                removeBurnedOutputs(outputs, to);
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
            
            BloomFilter bloomFilter = new BloomFilter(outputs.size(), 0.001, 42);
            for(TransactionOutput output: outputs) {
                bloomFilter.insert(output.unsafeBitcoinSerialize());
            }
            
            //TODO: repeating the same values does not burn it twice
            List<Pair<TransactionOutPoint, Integer>> burned = transactionService.burnOutputFromNewTransaction(
                    params, prepareSignTO.clientPublicKey(), tx.getInputs());
            walletService.addWatchingOutpointsForRemoval(burned);

            Collections.sort(keys,ECKey.PUBKEY_COMPARATOR);
            final Script redeemScript = ScriptBuilder.createRedeemScript(2, keys);
            //sign the tx with the server keys
            List<TransactionSignature> serverTxSigs = BitcoinUtils.partiallySign(tx, redeemScript, serverKey);
            
            prepareHalfSignTO = new PrepareHalfSignTO().type(filtered ? Type.SUCCESS_FILTERED : Type.SUCCESS)
                    .unsignedTransaction(tx.unsafeBitcoinSerialize())
                    .bloomFilter(bloomFilter.unsafeBitcoinSerialize())
                    .signatures(SerializeUtils.serializeSignatures(serverTxSigs));
            CACHE.put(key, prepareHalfSignTO);
            return prepareHalfSignTO;

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
    public RefundP2shTO refundToP2SH(@RequestBody RefundP2shTO refundP2shTO) {
        final String clientId = SerializeUtils.bytesToHex(refundP2shTO.clientPublicKey());
        LOG.debug("{RefundP2SH} {}", clientId);
        try {
            RefundP2shTO errorStatus = checkInput(refundP2shTO, "f", keyService);
            if(errorStatus != null) {
                LOG.debug("{RefundP2SH} input error {} for {}", errorStatus.type(), clientId);
                return errorStatus;
            }
            final List<ECKey> keys = keyService.getECKeysByClientPublicKey(refundP2shTO.clientPublicKey());
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
                    params, refundP2shTO.refundClientOutpointsCoinPair());
            //Client refund sigs are here, we can do a full refund
            List<TransactionSignature> refundSignatures = SerializeUtils.deserializeSignatures(refundP2shTO.refundSignaturesClient());
            
            //now get all the output we want the refund, for client this will be one entry, for merchant this
            //will return multiple entries
            List<TransactionOutput> clientWalletOutputs = walletService.getOutputs(p2shAddress);
            LOG.debug("{RefundP2SH} nr. of outputs from network {} for {}. Full list {}", clientWalletOutputs.size(), clientId, clientWalletOutputs);
            //add/remove pending 
            clientWalletOutputs.addAll(transactionService.approvedOutputs(params, p2shAddress));
            LOG.debug("{RefundP2SH} nr. of outputs after approve {} for {}. Full list {}", clientWalletOutputs.size(), clientId, clientWalletOutputs);
            //remove pending/burned outputs
            
            //remove burned outputs, either we do it ourselfs, or the client can provide an optional bloomfilter
            //if not we can check the burned output which will be the ones used for the refund
            List<TransactionOutPoint> to = transactionService.burnedOutpoints(params, clientKey.getPubKey());
            if(refundP2shTO.bloomFilter() != null) {
                clientWalletOutputs = filter(params, clientWalletOutputs, refundP2shTO.bloomFilter());       
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
            keyService.addRefundTransaction(refundP2shTO.clientPublicKey(), refundTx);
            //unsignedRefund is now fully signed
            RefundP2shTO retVal = new RefundP2shTO().setSuccess()
                .fullRefundTransaction(refundTx)
                .refundSignaturesServer(SerializeUtils.serializeSignatures(partiallySignedRefundServer));

            return retVal;

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
    public CompleteSignTO sign(@RequestBody CompleteSignTO signTO) {
        final String clientId = SerializeUtils.bytesToHex(signTO.clientPublicKey());
        LOG.debug("{CompleteSign} {}", clientId);
        try {
            CompleteSignTO errorStatus = checkInput(signTO, "s", keyService);
            if(errorStatus != null) {
                LOG.debug("{CompleteSign} input error {} for {}", errorStatus.type(), clientId);
                return errorStatus;
            }
            final List<ECKey> keys = keyService.getECKeysByClientPublicKey(signTO.clientPublicKey());
            if (keys == null || keys.size() != 2) {
                LOG.debug("{CompleteSign} keys not found for {}", clientId);
                return new CompleteSignTO().type(Type.KEYS_NOT_FOUND);
            }
            
            final NetworkParameters params = appConfig.getNetworkParameters();
            
            final Script serverClientRedeemScript = ScriptBuilder.createP2SHOutputScript(2, keys);
            final Address p2shAddressFrom = serverClientRedeemScript.getToAddress(params);
           
            final Address p2shAddressTo = new Address(params, signTO.p2shAddressTo());
            
            final Transaction fullTx = new Transaction(params, signTO.fullSignedTransaction());
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
            for(Transaction refund: keyService.findRefundTransaction(params, signTO.clientPublicKey())) {
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
            
            if(transactionService.checkInstantTx(params, fullTx, signTO.clientPublicKey(), p2shAddressFrom,
                    p2shAddressTo)) {
                LOG.debug("{CompleteSign} instant payment OK for {}", clientId);
                //walletService.commit(fullTx);
                return new CompleteSignTO().setSuccess();
            } else {
                LOG.debug("{CompleteSign} instant payment NOT OK for {}", clientId);
                return new CompleteSignTO().type(Type.NO_INSTANT_PAYMENT);
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
            BloomFilter bloomFilter = new BloomFilter(params, rawBloomFilter);
            for(TransactionOutput output: outputs) {
                if(bloomFilter.contains(output.unsafeBitcoinSerialize())) {
                    filteredOutputs.add(output);
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
    
    private static <K extends BaseTO> K checkInput(K input, String endpoint, KeyService keyService) {
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
            return newInstance(input, Type.SIGNATURE_ERROR);

        }
        if (!keyService.checkReplayAttack(input.clientPublicKey(), endpoint, new Date(input.currentDate()))) {
            return newInstance(input, Type.REPLAY_ATTACK);
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
