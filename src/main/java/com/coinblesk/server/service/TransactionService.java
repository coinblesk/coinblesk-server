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
import com.coinblesk.server.entity.Tx;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutPoint;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.script.Script;
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

    @Transactional(readOnly = true)
    public boolean isTransactionInstant(final NetworkParameters params, byte[] clientPublicKey, Script redeemScript, Transaction fullTx) {
        //check if the funding (inputs) of fullTx are timelocked, so only for the 
        return isTransactionInstant(params, clientPublicKey, redeemScript, fullTx, null);
    }

    private boolean isTransactionInstant(final NetworkParameters params, byte[] clientPublicKey, Script redeemScript, Transaction fullTx,
            Transaction requester) {
        
        //fullTx can be null, we did not find a parent!
        if (fullTx == null) {
            LOG.debug("(instast-check) we did not find a parent transaction for {}", requester);
            return false;
        }

        //check if already approved
        List<Transaction> approved = listTransactions(params, clientPublicKey, true);
        for (Transaction tx : approved) {
            if (tx.getHash().equals(fullTx.getHash())) {
                LOG.debug("(instast-check) already approved tx {}", tx.getHash());
                return true;
            }
        }
        //if we have a tx that is confirmed, we are good to go
        if (fullTx.getConfidence().getDepthInBlocks() >= appConfig.getMinConf()) {
            LOG.debug("(instast-check) the confidence of tx {} is good: {}", fullTx.getHash(), fullTx.getConfidence()
                    .getDepthInBlocks());
            return true;
        }
        
        final List<TransactionInput> inputsToCheck = fullTx.getInputs();
        final List<Transaction> clientTransactions = listTransactions(params, clientPublicKey, false);

        // check double signing
        final boolean signedExactlyOnce = signedExactlyOnce(params, clientTransactions, inputsToCheck);

        if (!signedExactlyOnce) {
            return false;
        }

        boolean areParentsInstant = false;
        for (TransactionInput relevantTransactionInput : inputsToCheck) {
            TransactionOutput transactionOutput = walletService.findOutputFor(relevantTransactionInput);
            //input may not be connected, it may be null
            if (transactionOutput == null) {
                LOG.debug("(instast-check) this input is not connected! {}", relevantTransactionInput);
                areParentsInstant = false;
                break;
            }
            final Transaction parentTransaction = transactionOutput.getParentTransaction();
            if (isTransactionInstant(params, clientPublicKey, redeemScript, parentTransaction, fullTx)) {
                areParentsInstant = true;
            } else {
                areParentsInstant = false;
                break;
            }
        }
        LOG.debug("(instast-check) areParentsInstant {}", areParentsInstant);

        if (areParentsInstant) {
            addTransaction(clientPublicKey, fullTx.unsafeBitcoinSerialize(), fullTx.getHash().getBytes(), true);
        }
        return areParentsInstant;
        
    }
    
    private boolean signedExactlyOnce(final NetworkParameters params, final List<Transaction> clientTransactions,
            final List<TransactionInput> inputsToCheck) {
        // check double signing
        int once = 0;
        final long currentTime = System.currentTimeMillis();
        for (final Transaction storedTransaction : clientTransactions) {
            final long lockTime = storedTransaction.getLockTime() * 1000;
            //ignore if we have a locktime in the future
            if (lockTime > currentTime + LOCK_THRESHOLD_MILLIS) {
                continue;
            }
            for (final TransactionInput relevantTransactionInput : inputsToCheck) {
                for (final TransactionInput storedTransactionInput : storedTransaction.getInputs()) {
                    if (storedTransactionInput.getOutpoint().equals(relevantTransactionInput.getOutpoint())) {
                        once++;
                        //abort early
                        if(once > 1) {
                            return false;
                        }
                    }
                }
            }
        }
        return once == 1;
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
        transaction.approved(false);
        txDAO.save(transaction);
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
    public List<Transaction> approvedTx(final NetworkParameters params) {
        final List<Tx> approved = txDAO.findAll(true);
        final List<Transaction> retVal = new ArrayList<>(approved.size());
        for (final Tx approvedTx : approved) {
            final Transaction tx = new Transaction(params, approvedTx.tx());
            retVal.add(tx);
        }
        return retVal;
    }
}
