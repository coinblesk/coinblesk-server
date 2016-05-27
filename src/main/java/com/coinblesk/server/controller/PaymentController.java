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

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.AddressFormatException;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionConfidence;
import org.bitcoinj.core.TransactionOutPoint;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.core.VerificationException;
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

import com.coinblesk.bitcoin.TimeLockedAddress;
import com.coinblesk.json.BalanceTO;
import com.coinblesk.json.KeyTO;
import com.coinblesk.json.RefundTO;
import com.coinblesk.json.SignTO;
import com.coinblesk.json.TimeLockedAddressTO;
import com.coinblesk.json.Type;
import com.coinblesk.json.VerifyTO;
import com.coinblesk.server.config.AppConfig;
import com.coinblesk.server.entity.Keys;
import com.coinblesk.server.service.KeyService;
import com.coinblesk.server.service.TransactionService;
import com.coinblesk.server.service.WalletService;
import com.coinblesk.server.utils.ApiVersion;
import com.coinblesk.server.utils.ToUtils;
import com.coinblesk.util.BitcoinUtils;
import com.coinblesk.util.CoinbleskException;
import com.coinblesk.util.InsufficientFunds;
import com.coinblesk.util.Pair;
import com.coinblesk.util.SerializeUtils;

/**
 *
 * @author Alessandro Di Carli
 * @author Thomas Bocek
 * @author Andreas Albrecht
 *
 */
@RestController
@RequestMapping(value = {"/payment", "/p"})
@ApiVersion({"v1", ""})
public class PaymentController {

    private final static Logger LOG = LoggerFactory.getLogger(PaymentController.class);
    private final static Set<String> CONCURRENCY = Collections.synchronizedSet(new HashSet<>());

    @Autowired
    private AppConfig appConfig;

    @Autowired
    private WalletService walletService;

    @Autowired
    private KeyService keyService;

    @Autowired
    private TransactionService txService;
    
    
    @RequestMapping(
    		value = {"/createTimeLockedAddress", "/ctla"},
    		method = RequestMethod.POST,
    		consumes = "application/json; charset=UTF-8",
            produces = "application/json; charset=UTF-8")
    @ResponseBody
    public TimeLockedAddressTO createTimeLockedAddress(@RequestBody TimeLockedAddressTO input) {
    	final String tag = "{createTimeLockedAddress}";
    	final Instant startTime = Instant.now();
    	final NetworkParameters params = appConfig.getNetworkParameters();
    	
    	try {
    		final TimeLockedAddressTO error = ToUtils.checkInput(input);
            if (error != null) {
            	LOG.debug("{} - input error - type={}", tag, error.type().toString());
                return error;
            }

            // set client/server keys
    		final ECKey clientKey = ECKey.fromPublicOnly(input.publicKey());
    		final String clientPubKeyHex = clientKey.getPublicKeyAsHex();
    		final Keys keys = keyService.getByClientPublicKey(clientKey.getPubKey());
			if (keys == null) {
				LOG.debug("{} - keys not found for clientPubKey={}", tag, clientPubKeyHex);
				return new TimeLockedAddressTO().type(Type.KEYS_NOT_FOUND);
			}
			final ECKey serverKey = ECKey.fromPrivateAndPrecalculatedPublic(
					keys.serverPrivateKey(), keys.serverPublicKey());
    		
    		// lockTime: do not allow locking by block and lockTime in the past
            final long lockTime = input.lockTime();
            if (!BitcoinUtils.isLockTimeByTime(lockTime) || 
            	 BitcoinUtils.isAfterLockTime(startTime.getEpochSecond(), lockTime)) {
            	return new TimeLockedAddressTO().type(Type.LOCKTIME_ERROR);
            }
            
            // Input is OK: create and return address
            final TimeLockedAddress address = new TimeLockedAddress(
            		clientKey.getPubKey(), serverKey.getPubKey(), lockTime);
            TimeLockedAddressTO responseTO = new TimeLockedAddressTO()
            		.currentDate(startTime.toEpochMilli())
            		.timeLockedAddress(address);
            
            // save if not exists
            if (!keyService.addressExists(address.getAddressHash())) {
                keyService.storeTimeLockedAddress(keys, address);
                walletService.addWatching(address.createPubkeyScript());
                responseTO.setSuccess();
                LOG.debug("{} - new address created: {}", tag, address.toStringDetailed(params));
            } else {
            	responseTO.type(Type.SUCCESS_BUT_ADDRESS_ALREADY_EXISTS);
                LOG.debug("{} - address already exists (multiple requests in a short time?): {}", 
                		tag, address.toStringDetailed(params));
            }
            
            SerializeUtils.signJSON(responseTO, serverKey);
            LOG.info("{} - created address: {}, clientPubKey={}", tag, address.toString(params), clientPubKeyHex);
    		return responseTO;
    		
    	} catch (Exception e) {
    		LOG.error("{} - Failed with exception: ", tag, e);
    		return new TimeLockedAddressTO()
    				.type(Type.SERVER_ERROR)
    				.message(e.getMessage());
    	} finally {
    		LOG.debug("{} - finished in {} ms", tag, Duration.between(startTime, Instant.now()).toMillis());
    	}
    }
    
    @RequestMapping(value = {"/signverify", "/sv"}, 
			method = RequestMethod.POST,
	        consumes = "application/json; charset=UTF-8",
	        produces = "application/json; charset=UTF-8")
	@ResponseBody
	public SignTO signVerify(@RequestBody SignTO request) {
    	final String tag = "{signverify}";
    	final Instant startTime = Instant.now();
    	String clientPubKeyHex = "(UNKNOWN)";
    	
		try {
			final NetworkParameters params = appConfig.getNetworkParameters();
			final Keys keys;
			final ECKey clientKey = ECKey.fromPublicOnly(request.publicKey());
    		clientPubKeyHex = clientKey.getPublicKeyAsHex();
    		final ECKey serverKey;
    		final Transaction transaction;
    		final List<TransactionSignature> clientSigs;
    		
    		final SignTO error = ToUtils.checkInput(request);
			if (error != null) {
				LOG.info("{} - clientPubKey={} - input error - type={}", tag, clientPubKeyHex, error.type());
				return error;
			}
				
			LOG.debug("{} - clientPubKey={} - request", tag, clientPubKeyHex);
	        keys = keyService.getByClientPublicKey(clientKey.getPubKey());
	        if (keys == null || keys.clientPublicKey() == null ||
	        		keys.serverPrivateKey() == null || keys.serverPublicKey() == null) {
	        	LOG.debug("{} - clientPubKey={} - KEYS_NOT_FOUND", tag, clientPubKeyHex);
	        	return ToUtils.newInstance(SignTO.class, Type.KEYS_NOT_FOUND);
	        }
	        serverKey = ECKey.fromPrivateAndPrecalculatedPublic(keys.serverPrivateKey(), keys.serverPublicKey());
	        
			if (keys.addresses().isEmpty()) {
				LOG.debug("{} - clientPubKey={} - ADDRESS_EMPTY", tag, clientPubKeyHex);
				return ToUtils.newInstance(SignTO.class, Type.ADDRESS_EMPTY, serverKey); 
			}
	     		        
	        /*
	         * Got a transaction in the request - sign
	         */
			if (request.transaction() != null) {
				transaction = new Transaction(params, request.transaction());
				LOG.debug("{} - clientPubKey={} - transaction from input: \n{}", tag, clientPubKeyHex, transaction);
								
				// if amount to spend && address provided, add corresponding output
				if (request.amountToSpend() > 0 && request.p2shAddressTo() != null && !request.p2shAddressTo().isEmpty()) {
					TransactionOutput txOut = transaction.addOutput(
							Coin.valueOf(request.amountToSpend()), 
							Address.fromBase58(params, request.p2shAddressTo()));
					LOG.debug("{} - added output={} to Tx={}", tag, txOut, transaction.getHash());
				}
				
				// if change is provided, we add an output to the most recently created address of the client.
				if (request.amountChange() > 0) {
					Address changeAddress = keys.addresses().last().toAddress(params);
					Coin changeAmount = Coin.valueOf(request.amountChange());
					TransactionOutput changeOut = transaction.addOutput(changeAmount, changeAddress);
					LOG.debug("{} - added change output={} to Tx={}", tag, changeOut, transaction.getHash());
				}
				
			} else {
				LOG.debug("{} - clientPubKey={} - INPUT_MISMATCH", tag, clientPubKeyHex);
				return ToUtils.newInstance(SignTO.class, Type.INPUT_MISMATCH, serverKey);
			}
			
			// check signatures
			if (request.signatures() == null || (request.signatures().size() != transaction.getInputs().size())) {
				LOG.debug("{} - clientPubKey={} - INPUT_MISMATCH - number of signatures ({}) != number of inputs ({})", 
					tag, clientPubKeyHex, request.signatures().size(), transaction.getInputs().size());
				return ToUtils.newInstance(SignTO.class, Type.INPUT_MISMATCH, serverKey);
			}

			clientSigs = SerializeUtils.deserializeSignatures(request.signatures());
			SignTO responseTO = txService.signVerifyTransaction(transaction, clientKey, serverKey, clientSigs);
			return responseTO;
		} catch (Exception e) {
			LOG.error("{} - clientPubKey={} - SERVER_ERROR: ", tag, clientPubKeyHex, e);
	        return new SignTO()
	        		.currentDate(System.currentTimeMillis())
	        		.type(Type.SERVER_ERROR)
	                .message(e.getMessage());
		} finally {
			LOG.debug("{} - clientPubKey={} - finished in {} ms", 
					tag, clientPubKeyHex, Duration.between(startTime, Instant.now()).toMillis());
		}
	} 

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
    public KeyTO keyExchange(@RequestBody KeyTO input) {
        final long startTime = System.currentTimeMillis();
        final String tag = "{key-exchange}";
        try {
            if (input.publicKey() == null || ECKey.isPubKeyCanonical(input.publicKey())) {
                return ToUtils.newInstance(KeyTO.class, Type.INPUT_MISMATCH);
            }
            
            LOG.debug("{} - clientPubKey={}", tag, SerializeUtils.bytesToHex(input.publicKey()));
            //no input checking as input may not be signed
            final byte[] clientPublicKey = input.publicKey();
            final ECKey serverEcKey = new ECKey();
            final List<ECKey> keyList = new ArrayList<>(2);
            keyList.add(ECKey.fromPublicOnly(clientPublicKey));
            keyList.add(serverEcKey);
            
            //2-of-2 multisig
            final Script script = BitcoinUtils.createP2SHOutputScript(2, keyList);
            final Pair<Boolean, Keys> retVal = keyService.storeKeysAndAddress(
            		clientPublicKey,
                    serverEcKey.getPubKey(),
                    serverEcKey.getPrivKeyBytes());

            final KeyTO serverKeyTO = new KeyTO().currentDate(System.currentTimeMillis());
            if (retVal.element0()) {
                walletService.addWatching(script);
            	serverKeyTO.publicKey(serverEcKey.getPubKey());
                serverKeyTO.setSuccess();
                SerializeUtils.signJSON(serverKeyTO, serverEcKey);
            } else {
            	Keys keys = retVal.element1();
                serverKeyTO.publicKey(keys.serverPublicKey());
                serverKeyTO.type(Type.SUCCESS_BUT_KEY_ALREADY_EXISTS);
                ECKey existingServerKey = ECKey.fromPrivateAndPrecalculatedPublic(keys.serverPrivateKey(), keys.serverPublicKey());
                SerializeUtils.signJSON(serverKeyTO, existingServerKey);
            }
            LOG.debug("{} - done - {}", serverKeyTO.type().toString());
            return serverKeyTO;
        } catch (Exception e) {
            LOG.error("{} - SERVER_ERROR: ", e);
            return new KeyTO()
            		.currentDate(System.currentTimeMillis())
                    .type(Type.SERVER_ERROR)
                    .message(e.getMessage());
        } finally {
        	LOG.debug("{} - finished in {} ms", tag, (System.currentTimeMillis()-startTime));
        }
    }

    @RequestMapping(value = {"/balance", "/b"}, method = RequestMethod.GET,
            consumes = "application/json; charset=UTF-8",
            produces = "application/json; charset=UTF-8")
    @ResponseBody
    public BalanceTO balance(@RequestBody BalanceTO input) {
        final long start = System.currentTimeMillis();
        try {
            if(input.publicKey() == null || input.publicKey().length == 0) {
                return new BalanceTO().type(Type.KEYS_NOT_FOUND);
            }
            LOG.debug("{balance} clientHash for {}", SerializeUtils.bytesToHex(input.publicKey()));
            final BalanceTO error = ToUtils.checkInput(input);
            if (error != null) {
                return error;
            }
            final NetworkParameters params = appConfig.getNetworkParameters();
            final List<ECKey> keys = keyService.getPublicECKeysByClientPublicKey(input.publicKey());
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
    public RefundTO refund(@RequestBody RefundTO input) {
        final long start = System.currentTimeMillis();
        try {
            if(input.publicKey() == null || input.publicKey().length == 0) {
                return new RefundTO().type(Type.KEYS_NOT_FOUND);
            }
            LOG.debug("{refund} for {}", SerializeUtils.bytesToHex(input.publicKey()));
            final RefundTO error = ToUtils.checkInput(input);
            if (error != null) {
                return error;
            }
            final List<ECKey> keys = keyService.getECKeysByClientPublicKey(input.publicKey());
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
            if (input.refundTransaction() != null) {
                    refundTransaction = new Transaction(params, input.refundTransaction());
            } 
            //choice 2 - send outpoints, coins, where to send btc to, and amount
            else if (input.outpointsCoinPair() != null && !input.outpointsCoinPair().isEmpty()
                    && input.lockTimeSeconds() > 0 && input.refundSendTo() != null) {
                final List<Pair<TransactionOutPoint, Coin>> refundClientPoints = SerializeUtils
                        .deserializeOutPointsCoin(
                                params, input.outpointsCoinPair());
                try {
                    Address refundSendTo = Address.fromBase58(params, input.refundSendTo());
                    refundTransaction = BitcoinUtils.createRefundTx(params, refundClientPoints, redeemScript,
                            refundSendTo, input.lockTimeSeconds());
                } catch (AddressFormatException e) {
                    LOG.debug("{refund}:{} empty address for", (System.currentTimeMillis() - start));
                    return new RefundTO().type(Type.ADDRESS_EMPTY).message(e.getMessage());
                }
            }
            //wrong choice
            else {
                return new RefundTO().type(Type.INPUT_MISMATCH);
            }
            
            //sanity check
            refundTransaction.verify();
            
            List<TransactionSignature> clientSigs = SerializeUtils.deserializeSignatures(input
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
            input.serverSignatures(SerializeUtils.serializeSignatures(serverSigs));
            //TODO: enable
            //refundTransaction.verify(); make sure those inputs are from the known p2sh address (min conf)
            byte[] refundTx = refundTransaction.unsafeBitcoinSerialize();
            txService.addTransaction(input.publicKey(), refundTx, refundTransaction.getHash().getBytes(), false);
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
        if(input.publicKey() == null || input.publicKey().length == 0) {
            return new SignTO().type(Type.KEYS_NOT_FOUND);
        }
        final String key = SerializeUtils.bytesToHex(input.publicKey());
        try {    
            LOG.debug("{sign} for {}", key);
            if(!CONCURRENCY.add(key)) {
                return new SignTO().type(Type.CONCURRENCY_ERROR);
            }
            final SignTO error = ToUtils.checkInput(input);
            if (error != null) {
                return error;
            }
            final NetworkParameters params = appConfig.getNetworkParameters();
            final List<ECKey> keys = keyService.getECKeysByClientPublicKey(input.publicKey());
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
            else if (input.outpointsCoinPair() != null && !input.outpointsCoinPair().isEmpty()
                    && input.p2shAddressTo() != null && input.amountToSpend() != 0) {
                //having the coins and the output allows us to create the tx without looking into our wallet
                try {
                    transaction = createTx(params, input.p2shAddressTo(), p2shAddressFrom, input.outpointsCoinPair(), 
                            input.amountToSpend(), redeemScript);
                } catch (AddressFormatException e) {
                    LOG.debug("{sign}:{} empty address for", (System.currentTimeMillis() - start));
                    return new SignTO().type(Type.ADDRESS_EMPTY).message(e.getMessage());
                }  catch (CoinbleskException e) {
                    LOG.warn("{sign} could not create tx", e);
                    return new SignTO().type(Type.TX_ERROR).message(e.getMessage());
                } catch (InsufficientFunds e) {
                    LOG.debug("{sign} not enough coins or amount too small");
                    return new SignTO().type(Type.NOT_ENOUGH_COINS);
                }
            } 
            //wrong choice
            else {
                return new SignTO().type(Type.INPUT_MISMATCH);
            }
            
            //sanity check
            transaction.verify();
            
            if(txService.isBurned(params, input.publicKey(), transaction)) {
                return new SignTO().type(Type.BURNED_OUTPUTS);
            }

            final List<TransactionSignature> serverSigs = BitcoinUtils
                    .partiallySign(transaction, redeemScript, serverKey);

            final byte[] serializedTransaction = transaction.unsafeBitcoinSerialize();
            txService.addTransaction(input.publicKey(), serializedTransaction, transaction.getHash().getBytes(), false);
            LOG.debug("{sign}:tx-hash {} in {} done", transaction.getHash(), (System.currentTimeMillis() - start));
            return new SignTO()
                    .setSuccess()
                    .transaction(serializedTransaction)
                    .signatures(SerializeUtils.serializeSignatures(serverSigs));

        } catch (Exception e) {
            LOG.error("Sign keys error", e);
            return new SignTO()
                    .type(Type.SERVER_ERROR)
                    .message(e.getMessage());
        } finally {
            CONCURRENCY.remove(key);
        }
    }

    @RequestMapping(value = {"/verify", "/v"}, method = RequestMethod.POST,
            consumes = "application/json; charset=UTF-8",
            produces = "application/json; charset=UTF-8")
    @ApiVersion("v2")
    @ResponseBody
    public VerifyTO verify(@RequestBody VerifyTO input) {
        final long start = System.currentTimeMillis();
        if(input.publicKey() == null || input.publicKey().length == 0) {
            return new VerifyTO().type(Type.KEYS_NOT_FOUND);
        }
        final String key = SerializeUtils.bytesToHex(input.publicKey());
        try {
            LOG.debug("{verify} for {}", key);
            if(!CONCURRENCY.add(key)) {
                return new VerifyTO().type(Type.CONCURRENCY_ERROR);
            }
            final VerifyTO error = ToUtils.checkInput(input);
            if (error != null) {
                return error;
            }
            final NetworkParameters params = appConfig.getNetworkParameters();
            final List<ECKey> keys = keyService.getECKeysByClientPublicKey(input.publicKey());
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
                LOG.debug("{verify}:{} got transaction from input", (System.currentTimeMillis() - start));
                fullTx = new Transaction(appConfig.getNetworkParameters(), input.transaction());
                LOG.debug("{verify}:{} tx1 created {}", (System.currentTimeMillis() - start), fullTx);
                //TODO: verify that this was sent from us
            }
            //choice 2 - send outpoints, coins, where to send btc to, and amount
            else if (input.outpointsCoinPair() != null && !input.outpointsCoinPair().isEmpty()
                    && input.p2shAddressTo() != null && input.amountToSpend() != 0 
                    && input.clientSignatures() != null && input.serverSignatures() != null) {
                try {
                    fullTx = createTx(params, input.p2shAddressTo(), p2shAddressFrom, input.outpointsCoinPair(), 
                            input.amountToSpend(), redeemScript);
                    List<TransactionSignature> clientSigs = SerializeUtils.deserializeSignatures(
                            input.clientSignatures());
                    List<TransactionSignature> severSigs = SerializeUtils.deserializeSignatures(
                            input.serverSignatures());
                    final boolean clientFirst = BitcoinUtils.clientFirst(keys, clientKey);
                    BitcoinUtils.applySignatures(fullTx, redeemScript, clientSigs, severSigs, clientFirst);
                    
                    LOG.debug("{verify}:{} tx2 created {}", (System.currentTimeMillis() - start), fullTx);
                    
                    if (!SerializeUtils.verifyTxSignatures(fullTx, clientSigs, redeemScript, clientKey)) {
                        LOG.debug("{verify} signature mismatch for client-sigs tx {} with sigs {}", fullTx, clientSigs);
                        return new VerifyTO().type(Type.SIGNATURE_ERROR);
                    } else {
                        LOG.debug("{verify} signature good! for client-sigs tx {} with sigs", fullTx, clientSigs);
                    }
                    
                    if (!SerializeUtils.verifyTxSignatures(fullTx, severSigs, redeemScript, serverKey)) {
                        LOG.debug("{verify} signature mismatch for server-sigs tx {} with sigs {}", fullTx, severSigs);
                        return new VerifyTO().type(Type.SIGNATURE_ERROR);
                    } else {
                        LOG.debug("{verify} signature good! for server-sigs tx {} with sigs", fullTx, clientSigs);
                    }
                    
                } catch (AddressFormatException e) {
                    LOG.debug("{verify}:{} empty address for", (System.currentTimeMillis() - start));
                    return new VerifyTO().type(Type.ADDRESS_EMPTY).message(e.getMessage());
                }  catch (CoinbleskException e) {
                    LOG.warn("{verify} could not create tx", e);
                    return new VerifyTO().type(Type.SERVER_ERROR).message(e.getMessage());
                } catch (InsufficientFunds e) {
                    LOG.debug("{verify} not enough coins or amount too small");
                    return new VerifyTO().type(Type.NOT_ENOUGH_COINS);
                }
            }
            //wrong choice
            else {
                return new VerifyTO().type(Type.INPUT_MISMATCH);
            }
            
            //sanity check
            fullTx.verify();
                      
            final VerifyTO output = new VerifyTO();
            output.transaction(fullTx.unsafeBitcoinSerialize());
            
            LOG.debug("{verify}:{} received {}", (System.currentTimeMillis() - start), fullTx);

            //ok, refunds are locked or no refund found
            fullTx.getConfidence().setSource(TransactionConfidence.Source.SELF);
            walletService.receivePending(fullTx);
            walletService.broadcast(fullTx);

            LOG.debug("{verify}:{} broadcast done", (System.currentTimeMillis() - start));
            
            if (txService.isTransactionInstant(params, input.publicKey(), fullTx)) {
                LOG.debug("{verify}:{} instant payment **OK**", (System.currentTimeMillis() - start));
                return output.setSuccess();
            } else {
                LOG.debug("{verify}:{} instant payment NOTOK", (System.currentTimeMillis() - start));
                return output.type(Type.SUCCESS_BUT_NO_INSTANT_PAYMENT);
            }

        } catch (Exception e) {
            LOG.error("{verify} register keys error: ", e);
            return new VerifyTO()
                    .type(Type.SERVER_ERROR)
                    .message(e.getMessage());
        } finally {
            CONCURRENCY.remove(key);
        }
    }
    
    private static Transaction createTx(NetworkParameters params, String p2shAddressTo, Address p2shAddressFrom,
            List<Pair<byte[], Long>> outpointsCoinPair, long amountToSpend, Script redeemScript) 
            throws AddressFormatException, CoinbleskException, InsufficientFunds {
        final Address p2shAddressTo1 = new Address(params, p2shAddressTo);

        //we now get from the client the outpoints for the refund tx (including hash)
        final List<Pair<TransactionOutPoint, Coin>> refundClientPoints = SerializeUtils
                .deserializeOutPointsCoin(params, outpointsCoinPair);

        return BitcoinUtils.createTx(params, refundClientPoints, redeemScript,
                    p2shAddressFrom, p2shAddressTo1, amountToSpend);
    }
}
