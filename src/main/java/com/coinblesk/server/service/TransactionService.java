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
import com.coinblesk.server.dao.ApprovedTxDAO;
import com.coinblesk.server.dao.TxDAO;
import com.coinblesk.server.entity.ApprovedTx;
import com.coinblesk.server.entity.Tx;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionInput;
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
    private final static long LOCK_THRESHOLD_MILLIS = 1000 * 60 * 60 * 4;

    @Autowired
    private AppConfig appConfig;

    @Autowired
    private ApprovedTxDAO approvedTxDAO;

    @Autowired
    private TxDAO txDAO;

    @Autowired
    private WalletService walletService;

    @Transactional(readOnly = false)
    public void addTransaction(byte[] clientPublicKey, byte[] serializedTransaction) {
        Tx transaction = new Tx();
        transaction.clientPublicKey(clientPublicKey);
        transaction.tx(serializedTransaction);
        transaction.creationDate(new Date());
        txDAO.save(transaction);
    }

    @Transactional(readOnly = false)
    public boolean isTransactionInstant(byte[] clientPublicKey, Script redeemScript, Transaction fullTx) {
        //check if the funding (inputs) of fullTx are timelocked, so only for the 
        return isTransactionInstant(clientPublicKey, redeemScript, fullTx, null);
    }

    private boolean isTransactionInstant(byte[] clientPublicKey, Script redeemScript, Transaction fullTx,
            Transaction requester) {
        long currentTime = System.currentTimeMillis();
        //fullTx can be null, we did not find a parent!
        if (fullTx == null) {
            LOG.debug("we did not find a parent transaction for {}", requester);
            return false;
        }

        //check if already approved
        List<Transaction> approved = approvedTx2(appConfig.getNetworkParameters(), clientPublicKey);
        for (Transaction tx : approved) {
            if (tx.getHash().equals(fullTx.getHash())) {
                return true;
            }
        }

        if (fullTx.getConfidence().getDepthInBlocks() >= appConfig.getMinConf()) {
            LOG.debug("The confidence of tx {} is good: {}", fullTx.getHash(), fullTx.getConfidence()
                    .getDepthInBlocks());
            return true;
        } else {
            final List<TransactionInput> relevantInputs = fullTx.getInputs();

            final List<Tx> clientTransactions = txDAO.findByClientPublicKey(clientPublicKey);

            // check double signing
            int signedInputCounter = 0;
            for (Tx clientTransaction : clientTransactions) {
                int relevantInputCounter = 0;
                for (TransactionInput relevantTransactionInput : relevantInputs) {
                    final NetworkParameters params = appConfig.getNetworkParameters();
                    final Transaction storedTransaction = new Transaction(params, clientTransaction.tx());
                    int storedInputCounter = 0;
                    for (TransactionInput storedTransactionInput : storedTransaction.getInputs()) {
                        if (storedTransactionInput.getOutpoint().toString().equals(relevantTransactionInput
                                .getOutpoint().toString())) {
                            if (!storedTransaction.hashForSignature(storedInputCounter, redeemScript,
                                    Transaction.SigHash.ALL, false)
                                    .equals(fullTx.hashForSignature(relevantInputCounter, redeemScript,
                                            Transaction.SigHash.ALL, false))) {
                                long lockTime = storedTransaction.getLockTime() * 1000;

                                if (lockTime > currentTime + LOCK_THRESHOLD_MILLIS) {
                                    continue;
                                } else {
                                    signedInputCounter++;
                                }
                            }
                        }
                        storedInputCounter++;
                    }
                    relevantInputCounter++;
                }
            }

            boolean areParentsInstant = false;
            for (TransactionInput relevantTransactionInput : relevantInputs) {
                TransactionOutput transactionOutput = relevantTransactionInput.getOutpoint()
                        .getConnectedOutput();
                //input may not be connected, it may be null
                if (transactionOutput == null) {
                    //TODO, figure out, why fullTx may not be connected, it is called after
                    //walletService.receivePending(fullTx);
                    transactionOutput = walletService.findOutputFor(relevantTransactionInput);
                    LOG.debug("This input is not connected! {}", relevantTransactionInput);
                    if (transactionOutput == null) {
                        LOG.debug("This input is still not connected! {}", relevantTransactionInput);
                        areParentsInstant = false;
                        break;
                    }
                }
                Transaction parentTransaction = transactionOutput.getParentTransaction();
                if (isTransactionInstant(clientPublicKey, redeemScript, parentTransaction, fullTx)) {
                    areParentsInstant = true;
                } else {
                    areParentsInstant = false;
                    break;
                }
            }
            LOG.debug("areParentsInstant {}", areParentsInstant);

            boolean isApproved = signedInputCounter == 0 && areParentsInstant;
            if (isApproved) {
                approveTx2(fullTx, clientPublicKey);
            }
            return isApproved;
        }
    }

    public void approveTx2(Transaction fullTx, byte[] clientPubKey) {
        ApprovedTx approvedTx = new ApprovedTx()
                .txHash(fullTx.getHash().getBytes())
                .tx(fullTx.unsafeBitcoinSerialize())
                .clientPublicKey(clientPubKey)
                .creationDate(new Date());
        approvedTxDAO.save(approvedTx);
    }

    public List<Transaction> approvedTx2(NetworkParameters params, byte[] pubKey) {
        List<ApprovedTx> approved = approvedTxDAO.findByAddress(pubKey);
        List<Transaction> retVal = new ArrayList<>(approved.size());
        for (ApprovedTx approvedTx : approved) {
            Transaction tx = new Transaction(params, approvedTx.tx());
            retVal.add(tx);
        }
        return retVal;
    }

    @Transactional(readOnly = false)
    public void removeApproved(Transaction approved) {
        approvedTxDAO.remove(approved.getHash().getBytes());
    }

    @Transactional(readOnly = true)
    public List<Transaction> approvedTx(NetworkParameters params) {
        List<ApprovedTx> approved = approvedTxDAO.findAll();
        List<Transaction> retVal = new ArrayList<>(approved.size());
        for (ApprovedTx approvedTx : approved) {
            Transaction tx = new Transaction(params, approvedTx.tx());
            retVal.add(tx);
        }
        return retVal;
    }
}
