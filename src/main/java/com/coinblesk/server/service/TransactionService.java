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
package com.coinblesk.server.service;

import com.coinblesk.server.config.AppConfig;
import com.coinblesk.server.dao.TxDAO;
import com.coinblesk.server.entity.AddressEntity;
import com.coinblesk.server.entity.TimeLockedAddressEntity;
import com.coinblesk.server.entity.Tx;
import com.coinblesk.util.BitcoinUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutPoint;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.core.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 *
 * @author Thomas Bocek
 */
@Service
public class TransactionService {

    private final static Logger LOG = LoggerFactory.getLogger(TransactionService.class);
    private final static long LOCK_THRESHOLD_MILLIS = 1000 * 60 * 60 * 4; //4h

    @Autowired
    private AppConfig appConfig;

    @Autowired
    private TxDAO txDAO;

    @Autowired
    private WalletService walletService;
    
    @Autowired
    private KeyService keyService;

    @Transactional(readOnly = false)
    public boolean isTransactionInstant(final NetworkParameters params, byte[] clientPublicKey, Transaction fullTx) {
        //check if the funding (inputs) of fullTx are timelocked
        return isTransactionInstant(params, clientPublicKey, fullTx, null);
    }

    private boolean isTransactionInstant(final NetworkParameters params, final byte[] clientPublicKey, 
																		 final Transaction fullTx,
        																 final Transaction requester) {
        // fullTx can be null, we did not find a parent!
        if (fullTx == null) {
            LOG.debug("(instant-check) we did not find a parent transaction for tx={}", requester);
            return false;
        }

        // check if already approved
        List<Transaction> approved = listTransactions(params, clientPublicKey, true);
        for (Transaction tx : approved) {
            if (tx.getHash().equals(fullTx.getHash())) {
                LOG.debug("(instant-check) already approved tx={}", tx.getHash());
                return true;
            }
        }
        // if we have a tx that is confirmed, we are good to go
        if (fullTx.getConfidence().getDepthInBlocks() >= appConfig.getMinConf()) {
            LOG.debug("(instant-check) the confidence is good: confirmations={}, tx={}", 
            		fullTx.getConfidence().getDepthInBlocks(), fullTx.getHashAsString());
            return true;
        }
        
        final List<TransactionInput> inputsToCheck = fullTx.getInputs();
        final List<Transaction> clientTransactions = listTransactions(params, clientPublicKey, false);

        // check double signing
        final boolean signedExactlyOnce = signedExactlyOnce(clientTransactions, inputsToCheck);
        if (!signedExactlyOnce) {
        	LOG.debug("(instant-check) signedExactlyOnce={}, tx={}", signedExactlyOnce, fullTx.getHashAsString());
            return false;
        }
        
        // check that the outputs are locked: either 2-of-2 multisig OR timeLocked in the future.
        boolean inputsLocked = areInputsLocked(inputsToCheck);
        if (!inputsLocked) {
        	LOG.debug("(instant-check) areInputsLocked={}, tx={}", inputsLocked, fullTx.getHashAsString());
        	return false;
        }

        // check whether the parents of the inputs are instant 
        boolean areParentsInstant = areParentsInstant(params, fullTx, clientPublicKey);
        if (areParentsInstant) {
        	LOG.debug("(instant-check) areParentsInstant={}, tx={}", areParentsInstant, fullTx.getHashAsString());
            addTransaction(clientPublicKey, fullTx.unsafeBitcoinSerialize(), fullTx.getHash().getBytes(), true);
        }
        return areParentsInstant;
    }
    
    private boolean areParentsInstant(NetworkParameters params, Transaction fullTx, byte[] clientPublicKey) {
    	boolean areParentsInstant = false;
        for (TransactionInput relevantTransactionInput : fullTx.getInputs()) {
            TransactionOutput transactionOutput = walletService.findOutputFor(relevantTransactionInput);
            //input may not be connected, it may be null
            if (transactionOutput == null) {
                LOG.debug("(instant-check) this input is not connected (tx={})! {}", 
                		fullTx.getHashAsString(), relevantTransactionInput);
                areParentsInstant = false;
                break;
            }
            // continue recursion
            final Transaction parentTransaction = transactionOutput.getParentTransaction();
            areParentsInstant = isTransactionInstant(params, clientPublicKey, parentTransaction, fullTx);
            if (!areParentsInstant) {
            	break;
            }
        }
        return areParentsInstant;
    }

	private boolean areInputsLocked(List<TransactionInput> inputs) {
    	for (TransactionInput input : inputs) {
    		TransactionOutput output = walletService.findOutputFor(input);
    		if (output == null) {
    			LOG.debug("Cannot determine whether output is locked or not (Tx not connected).");
    			return false;
    		}
    		
    		byte[] addressHash = output.getScriptPubKey().getPubKeyHash();
    		AddressEntity address = keyService.findAddressByAddressHash(addressHash);
    		if (address == null) {
    			// unknown input (maybe not locked)
    			return false;
    		} else if (address instanceof TimeLockedAddressEntity) {
    			TimeLockedAddressEntity timeLockedAddress = (TimeLockedAddressEntity) address;
    			if (BitcoinUtils.isAfterLockTime(
	    					Utils.currentTimeSeconds()- LOCK_THRESHOLD_MILLIS/1000, 
	    					timeLockedAddress.getLockTime())) {
    				// locktime expired
    				return false;
    			}
    		} else {
    			// 2-of-2 multisig: locked
    		}
    	}
    	
    	return true;
	}

	private boolean signedExactlyOnce(final List<Transaction> clientTransactions, 
									  final List<TransactionInput> inputsToCheck) {
		final long currentTime = System.currentTimeMillis();
		// check double signing:
		// for each input (outpoint), go through all transactions and compare the outpoints.
		for (TransactionInput relevantTxIn : inputsToCheck) {
			int numSigned = 0;
			for (Transaction storedTx : clientTransactions) {
				final long lockTimeMillis = storedTx.getLockTime() * 1000;
	            // ignore if we have a locktime in the future
	            if (lockTimeMillis > currentTime + LOCK_THRESHOLD_MILLIS) {
	                continue;
	            }
				for (TransactionInput storedTxIn : storedTx.getInputs()) {
					if (storedTxIn.getOutpoint().equals(relevantTxIn.getOutpoint())) {
						numSigned++;
                        // abort early
                        if(numSigned > 1) {
                            return false;
                        }
                    }
				}
			}
			// not signed exactly once
			if (numSigned != 1) {
				return false;
			}
		}
		
		return true;
    }
    
    @Transactional(readOnly = true)
    public boolean isBurned(NetworkParameters params, byte[] pubKey, Transaction fullTx) {
        //check for already approved outpoints, if we have, we won't sign
        final List<Transaction> approvedTxs = listTransactions(params, pubKey, true);
        final List<TransactionOutPoint> burned = new ArrayList<>(approvedTxs.size());
        for (final Transaction approvedTx : approvedTxs) {
            for (final TransactionInput txInput : approvedTx.getInputs()) {
                burned.add(txInput.getOutpoint());
            }
        }
        final List<TransactionOutPoint> currents = new ArrayList<>();
        for (final TransactionInput txInput : fullTx.getInputs()) {
            currents.add(txInput.getOutpoint());
        }
        for (final TransactionOutPoint current : currents) {
            if (burned.contains(current)) {
                return true;
            }
        }
        return false;
    }
    
    @Transactional(readOnly = false)
    public void addTransaction(byte[] clientPublicKey, byte[] tx, byte[] txHash, boolean approved) {
        Tx transaction = new Tx();
        transaction.clientPublicKey(clientPublicKey);
        transaction.tx(tx);
        transaction.txHash(txHash);
        transaction.creationDate(new Date());
        transaction.approved(approved);
        transaction = txDAO.save(transaction);
    }
    
    @Transactional(readOnly = false)
    public void removeTransaction(Transaction tx) {
        txDAO.remove(tx.getHash().getBytes());
    }

    private List<Transaction> listTransactions(final NetworkParameters params, 
            final byte[] clientPublicKey, final boolean approved) {
        final List<Tx> list = txDAO.findByClientPublicKey(clientPublicKey, approved);
        final List<Transaction> retVal = new ArrayList<>(list.size());
        for (final Tx enityTx : list) {
            final Transaction tx = new Transaction(params, enityTx.tx());
            retVal.add(tx);
        }
        return retVal;
    }

    @Transactional(readOnly = true)
    public List<Transaction> listApprovedTransactions(final NetworkParameters params) {
        final List<Tx> approved = txDAO.findAll(true);
        final List<Transaction> retVal = new ArrayList<>(approved.size());
        for (final Tx approvedTx : approved) {
            final Transaction tx = new Transaction(params, approvedTx.tx());
            retVal.add(tx);
        }
        return retVal;
    }
}
