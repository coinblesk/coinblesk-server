package ch.uzh.csg.coinblesk.server.controller;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import javax.annotation.Nullable;

import org.apache.commons.codec.binary.Hex;
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
import org.bitcoinj.core.Utils;
import org.bitcoinj.crypto.TransactionSignature;
import org.bitcoinj.script.Script;
import org.bitcoinj.utils.Threading;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import com.coinblesk.bitcoin.TimeLockedAddress;
import com.coinblesk.json.BalanceTO;
import com.coinblesk.json.BaseTO;
import com.coinblesk.json.CompleteSignTO;
import com.coinblesk.json.KeyTO;
import com.coinblesk.json.PrepareFullTxTO;
import com.coinblesk.json.PrepareHalfSignTO;
import com.coinblesk.json.RefundP2shTO;
import com.coinblesk.json.RefundTO;
import com.coinblesk.json.SignTO;
import com.coinblesk.json.TimeLockedAddressTO;
import com.coinblesk.json.Type;
import com.coinblesk.json.VerifyTO;
import com.coinblesk.util.BitcoinUtils;
import com.coinblesk.util.Pair;
import com.coinblesk.util.SerializeUtils;
import com.coinblesk.util.SimpleBloomFilter;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;

import ch.uzh.csg.coinblesk.server.config.AppConfig;
import ch.uzh.csg.coinblesk.server.entity.Keys;
import ch.uzh.csg.coinblesk.server.entity.TimeLockedAddressEntity;
import ch.uzh.csg.coinblesk.server.service.KeyService;
import ch.uzh.csg.coinblesk.server.service.TransactionService;
import ch.uzh.csg.coinblesk.server.service.WalletService;
import ch.uzh.csg.coinblesk.server.utils.ApiVersion;
import ch.uzh.csg.coinblesk.server.utils.LruCache;

/**
 *
 * @author Alessandro Di Carli
 * @author Andreas Albrecht
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
    private TransactionService transactionService;
    
    @Autowired
    private KeyService keyService;
    
    private static final Map<String, PrepareHalfSignTO> CACHE_PREPARE = Collections.synchronizedMap(new LruCache<>(1000));
    private static final Map<String, PrepareFullTxTO> CACHE_PREPARE_FULL = Collections.synchronizedMap(new LruCache<>(1000));
    private static final Map<String, RefundP2shTO> CACHE_REFUND = Collections.synchronizedMap(new LruCache<>(1000));
    private static final Map<String, CompleteSignTO> CACHE_COMPLETE = Collections.synchronizedMap(new LruCache<>(1000));

    
    @RequestMapping(
    		value = {"/createTimeLockedAddress"},
    		method = RequestMethod.POST,
    		consumes = "application/json; charset=UTF-8",
            produces = "application/json; charset=UTF-8")
    @ResponseBody
    public TimeLockedAddressTO createTimeLockedAddress(@RequestBody KeyTO keyTO) {
    	try {
    		LOG.debug("{createTimeLockedAddress} - clientPubKey={}", Hex.encodeHexString(keyTO.publicKey()));
    		final NetworkParameters params = appConfig.getNetworkParameters();
    		final byte[] clientPubKey = keyTO.publicKey();
    		final ECKey clientKey = ECKey.fromPublicOnly(clientPubKey); // make sure it is an ECKey
    		
    		final Keys keys = getKeysOrCreate(clientKey.getPubKey());
    		if (keys == null || keys.serverPrivateKey() == null || keys.serverPublicKey() == null || keys.clientPublicKey() == null) {
    			LOG.error("{createTimeLockedAddress} - keys not found for clientPubKey={}", clientKey.getPublicKeyAsHex());
    			return new TimeLockedAddressTO().type(Type.KEYS_NOT_FOUND);
    		}
    		final ECKey serverKey = ECKey.fromPublicOnly(keys.serverPublicKey());
    		// TODO: lock time relative to blockchain height/time?
            final long lockTime = walletService.refundLockTime();
            final TimeLockedAddress address = new TimeLockedAddress(clientKey.getPubKey(), serverKey.getPubKey(), lockTime, params);
            
            TimeLockedAddressEntity checkExists = keyService.getTimeLockedAddressByAddressHash(address.getAddressHash());
            if (checkExists == null) {
                keyService.storeTimeLockedAddress(keys, address);
                walletService.addWatching(address.createRedeemScript());
                LOG.debug("{createTimeLockedAddress} - new address created: {}", address.toStringDetailed());
            } else {
                LOG.warn("{createTimeLockedAddress} - address does already exist (probably due to multiple requests in a short time): {}", 
                		address.toStringDetailed());
            }
            
            TimeLockedAddressTO addressTO = new TimeLockedAddressTO();
            addressTO.timeLockedAddress(address);
            addressTO.setSuccess();
            // TODO: sign response?
            
            LOG.info("{createTimeLockedAddress} - new address created: {}", address);
    		return addressTO;
    	} catch (Exception e) {
    		LOG.error("{createTimeLockedAddress} - error: ", e);
    		return new TimeLockedAddressTO()
    				.type(Type.SERVER_ERROR)
    				.message(e.getMessage());
    	}
    }
    
    /**
     * @param clientPubKey 
     * @return the keys for the given client public key. new key is created and added if not in DB yet.
     */
    private Keys getKeysOrCreate(final byte[] clientPubKey) {
    	Keys keys = keyService.getByClientPublicKey(clientPubKey);
    	if (keys != null) {
    		return keys;
    	}
    	
    	ECKey serverKey = new ECKey();
    	// TODO: should not be required to add an address!
    	Address p2pkh = ECKey.fromPublicOnly(clientPubKey).toAddress(appConfig.getNetworkParameters());
    	keyService.storeKeysAndAddress(clientPubKey, p2pkh, serverKey.getPubKey(), serverKey.getPrivKeyBytes());
    	
    	Keys createdKeys = keyService.getByClientPublicKey(clientPubKey);
    	LOG.info("{createTimeLockedAddress} - created new serverKey - serverPubKey={}, clientPubKey={}", 
    			Utils.HEX.encode(createdKeys.serverPublicKey()), Utils.HEX.encode(createdKeys.clientPublicKey()));
    	
    	return createdKeys;
    }
    
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
    public BalanceTO balance(@RequestBody KeyTO keyTO) {
        LOG.debug("Balance clientHash for {}", SerializeUtils.bytesToHex(keyTO.publicKey()));
        try {
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
                    p2shAddressTo = new Address(params, input.p2shAddressTo());
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
    @ApiVersion("v2")
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
}
