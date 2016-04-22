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

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
import com.coinblesk.json.KeyTO;
import com.coinblesk.json.RefundTO;
import com.coinblesk.json.SignTO;
import com.coinblesk.json.TimeLockedAddressTO;
import com.coinblesk.json.TxSig;
import com.coinblesk.json.Type;
import com.coinblesk.json.VerifyTO;
import com.coinblesk.server.config.AppConfig;
import com.coinblesk.server.entity.Keys;
import com.coinblesk.server.entity.TimeLockedAddressEntity;
import com.coinblesk.server.service.KeyService;
import com.coinblesk.server.service.TransactionService;
import com.coinblesk.server.service.WalletService;
import com.coinblesk.server.utils.ApiVersion;
import com.coinblesk.util.BitcoinUtils;
import com.coinblesk.util.CoinbleskException;
import com.coinblesk.util.InsuffientFunds;
import com.coinblesk.util.Pair;
import com.coinblesk.util.SerializeUtils;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;

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
    		value = {"/createTimeLockedAddress"},
    		method = RequestMethod.POST,
    		consumes = "application/json; charset=UTF-8",
            produces = "application/json; charset=UTF-8")
    @ApiVersion("v3")
    @ResponseBody
    public TimeLockedAddressTO createTimeLockedAddress(@RequestBody TimeLockedAddressTO input) {
    	final String tag = "{createTimeLockedAddress}";
    	final long tsStart = System.currentTimeMillis();
    	try {
    		final TimeLockedAddressTO error = checkInput(input);
            if (error != null) {
            	LOG.debug("{} - input error - type={}", tag, error.type().toString());
                return error;
            }
    		
    		final NetworkParameters params = appConfig.getNetworkParameters();
    		final ECKey clientKey = ECKey.fromPublicOnly(input.clientPublicKey());
    		final String clientPubKeyHex = clientKey.getPublicKeyAsHex();
    		LOG.debug("{} - clientPubKey={}", tag, clientPubKeyHex);
    		
    		final Keys keys = getKeysOrCreate(clientKey.getPubKey());
			if (keys == null) {
				LOG.error("{} - keys not found for clientPubKey={}", tag, clientPubKeyHex);
				return new TimeLockedAddressTO().type(Type.KEYS_NOT_FOUND);
			}
    		final ECKey serverKey = ECKey.fromPrivateAndPrecalculatedPublic(
    						keys.serverPrivateKey(), keys.serverPublicKey());
            final long lockTime = createNewLockTime();
            final TimeLockedAddress address = new TimeLockedAddress(
            			clientKey.getPubKey(), serverKey.getPubKey(), lockTime);
            
            final TimeLockedAddressEntity checkExists = keyService.getTimeLockedAddressByAddressHash(address.getAddressHash());
            if (checkExists == null) {
                keyService.storeTimeLockedAddress(keys, address);
                walletService.addWatching(address.createPubkeyScript());
                LOG.debug("{} - new address created ({}ms): {}", 
                		tag, (System.currentTimeMillis()-tsStart), address.toStringDetailed(params));
            } else {
                LOG.warn("{} - address does already exist (multiple requests in a short time?): {}", 
                		tag, address.toStringDetailed(params));
            }
            
            TimeLockedAddressTO addressTO = new TimeLockedAddressTO()
            		.timeLockedAddress(address)
            		.setSuccess();
            SerializeUtils.signJSON(addressTO, serverKey);
            LOG.info("{} - time locked address: {}, clientPubKey={} ({}ms)", 
            		tag, address.toString(params), clientPubKeyHex, (System.currentTimeMillis()-tsStart));
    		return addressTO;
    		
    	} catch (Exception e) {
    		LOG.error("{} - error ({}ms): ", tag, (System.currentTimeMillis()-tsStart), e);
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
    	final Keys keys = keyService.getByClientPublicKey(clientPubKey);
    	if (keys != null) {
    		return keys;
    	}
    	
    	final ECKey serverKey = new ECKey();
    	keyService.storeKeysAndAddress(clientPubKey, serverKey.getPubKey(), serverKey.getPrivKeyBytes());
    	
    	final Keys createdKeys = keyService.getByClientPublicKey(clientPubKey);
    	LOG.info("{getKeysOrCreate} - created new serverKey - clientPubKey={}, serverPubKey={}, ", 
    			Utils.HEX.encode(createdKeys.clientPublicKey()), Utils.HEX.encode(createdKeys.serverPublicKey()));
    	
    	return createdKeys;
    }
    
    private long createNewLockTime() {
    	return Utils.currentTimeSeconds() + appConfig.getLockTimeSpan();
    }
    
    @RequestMapping(value = {"/signtx", "/stx"}, 
			method = RequestMethod.POST,
	        consumes = "application/json; charset=UTF-8",
	        produces = "application/json; charset=UTF-8")
	@ApiVersion("v3")
	@ResponseBody
	public SignTO signTx(@RequestBody SignTO request) {
		try {
			LOG.info("{signTx} - sign request from clientPubKey={}", Utils.HEX.encode(request.clientPublicKey()));
			final NetworkParameters params = appConfig.getNetworkParameters();
	        final Keys keys = keyService.getByClientPublicKey(request.clientPublicKey());
	        if (keys == null || 
	        		keys.serverPrivateKey() == null || keys.serverPublicKey() == null || 
	        		keys.clientPublicKey() == null) {
	        	return new SignTO()
	        			.type(Type.KEYS_NOT_FOUND);
	        }
			if (keys.addresses().isEmpty()) {
				return new SignTO()
						.type(Type.ADDRESS_EMPTY)
						.message("No address created yet.");
			}
	        
	        final ECKey clientKey = ECKey.fromPublicOnly(keys.clientPublicKey());
	        final ECKey serverKey = ECKey.fromPrivateAndPrecalculatedPublic(keys.serverPrivateKey(), keys.serverPublicKey());
	        final List<Address> addresses = keys.btcAddresses(params);
	        final List<TransactionSignature> clientSignatures;
	        final Transaction transaction;
	        
	        /*
	         * Got a transaction in the request - sign the tx inputs
	         */
			if (request.transaction() != null) {
				clientSignatures = null;
				transaction = new Transaction(params, request.transaction());
				LOG.debug("{signTx} got transaction from input: \n{}", transaction.toString());
			} else {
				return new SignTO().type(Type.INPUT_MISMATCH);
			}
			
			
			// for each input, search corresponding redeemData (looking up in DB for address hash in the output)
	        List<TransactionInput> txInputs = transaction.getInputs();
	        List<byte[]> redeemScriptsForInputs = new ArrayList<>(txInputs.size());
	        for (int inputIndex = 0; inputIndex < txInputs.size(); ++inputIndex) {
				TransactionInput txIn = txInputs.get(inputIndex);
				// get it from the wallet service because Tx may not be connected if received over the network.
				TransactionOutput txOutput = walletService.findOutputFor(txIn);
				byte[] redeemScriptBytes = null;
				if (txOutput != null) {
					byte[] addressHashFrom = txOutput.getScriptPubKey().getPubKeyHash();
					redeemScriptBytes = keyService.getRedeemScriptByAddressHash(addressHashFrom);
					if (redeemScriptBytes == null) {
						LOG.warn("Redeem script not found for address: {}", Address.fromP2SHHash(params, addressHashFrom));
					}
				} else {
					// may happen if Tx contains inputs of other transactions
					// not related to coinblesk (unknown tx outputs).
					LOG.warn("Transaction output for tx input not found. {}", txIn.toString());
					redeemScriptBytes = new byte[0];
				}
				redeemScriptsForInputs.add(redeemScriptBytes);
				LOG.debug("redeemScript for input {}: {} - {}", inputIndex, txIn, new Script(redeemScriptBytes));
	        }
			
			
			final List<TransactionSignature> serverSigs = BitcoinUtils.partiallySign(transaction, redeemScriptsForInputs, serverKey);
			final List<TxSig> txSigs = SerializeUtils.serializeSignatures(serverSigs);
	        final byte[] serializedTransaction = transaction.unsafeBitcoinSerialize();
	        //keyService.addTransaction(clientKey.getPubKey(), serializedTransaction);
	        
	        return new SignTO()
	                .setSuccess()
	                .serverSignatures(txSigs);
	        
		} catch (Exception e) {
			LOG.error("{signTx} - Sign keys error", e);
	        return new SignTO()
	                .type(Type.SERVER_ERROR)
	                .message(e.getMessage());
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
        final long start = System.currentTimeMillis();
        try {
            if(input.publicKey()== null || input.publicKey().length == 0) {
                return new KeyTO().type(Type.KEYS_NOT_FOUND);
            }
            LOG.debug("{register} clientHash for {}", SerializeUtils.bytesToHex(input.publicKey()));
            //no input checking as input may not be signed
            final byte[] clientPublicKey = input.publicKey();
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
                return serverKeyTO.type(Type.SUCCESS_BUT_KEY_ALREADY_EXISTS);
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
    public BalanceTO balance(@RequestBody BalanceTO input) {
        final long start = System.currentTimeMillis();
        try {
            if(input.publicKey() == null || input.publicKey().length == 0) {
                return new BalanceTO().type(Type.KEYS_NOT_FOUND);
            }
            LOG.debug("{balance} clientHash for {}", SerializeUtils.bytesToHex(input.publicKey()));
            final BalanceTO error = checkInput(input);
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
            final RefundTO error = checkInput(input);
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
                    Address refundSendTo = new Address(params, input.refundSendTo());
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
            final SignTO error = checkInput(input);
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
                } catch (InsuffientFunds e) {
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
                    .serverSignatures(SerializeUtils.serializeSignatures(serverSigs));

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
            final VerifyTO error = checkInput(input);
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
                    BitcoinUtils.applySignatures(fullTx, redeemScript, clientSigs, severSigs, true);
                    
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
                } catch (InsuffientFunds e) {
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
            
            if (txService.isTransactionInstant(params, input.publicKey(), redeemScript, fullTx)) {
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

        //check if the client sent us a time which is way too old (1 day)
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

        if (!SerializeUtils.verifyJSONSignature(input, ECKey.fromPublicOnly(input.publicKey()))) {
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
