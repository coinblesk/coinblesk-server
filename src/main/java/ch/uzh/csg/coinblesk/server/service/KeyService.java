/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.uzh.csg.coinblesk.server.service;

import ch.uzh.csg.coinblesk.server.config.AppConfig;
import ch.uzh.csg.coinblesk.server.dao.KeyDAO;
import ch.uzh.csg.coinblesk.server.dao.RefundDAO;
import ch.uzh.csg.coinblesk.server.dao.TxDAO;
import ch.uzh.csg.coinblesk.server.entity.Keys;
import ch.uzh.csg.coinblesk.server.entity.Refund;
import ch.uzh.csg.coinblesk.server.entity.Tx;
import com.coinblesk.util.Pair;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import com.coinblesk.util.SerializeUtils;
import org.bitcoinj.core.*;
import org.bitcoinj.script.Script;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * @author Thomas Bocek
 */
@Service
public class KeyService {

    private final static Logger LOG = LoggerFactory.getLogger(KeyService.class);
    @Autowired
    private AppConfig appConfig;

    @Autowired
    private KeyDAO clientKeyDAO;

    @Autowired
    private RefundDAO refundDAO;

    @Autowired
    private TxDAO txDAO;

    @Transactional(readOnly = true)
    public Keys getByClientPublicKey(final String clientPublicKey) {
        final byte[] clientPublicKeyRaw = Base64.getDecoder().decode(clientPublicKey);
        return getByClientPublicKey(clientPublicKeyRaw);
    }

    @Transactional(readOnly = true)
    public Keys getByClientPublicKey(final byte[] clientPublicKeyRaw) {
        return clientKeyDAO.findByClientPublicKey(clientPublicKeyRaw);
    }

    @Transactional(readOnly = true)
    public List<ECKey> getPublicECKeysByClientPublicKey(final String clientPublicKey) {
        final byte[] clientPublicKeyRaw = Base64.getDecoder().decode(clientPublicKey);
        return getPublicECKeysByClientPublicKey(clientPublicKeyRaw);
    }

    @Transactional(readOnly = true)
    public List<ECKey> getPublicECKeysByClientPublicKey(final byte[] clientPublicKeyRaw) {
        final Keys keys = clientKeyDAO.findByClientPublicKey(clientPublicKeyRaw);
        final List<ECKey> retVal = new ArrayList<>(2);
        retVal.add(ECKey.fromPublicOnly(keys.clientPublicKey()));
        retVal.add(ECKey.fromPublicOnly(keys.serverPublicKey()));
        return retVal;
    }

    @Transactional(readOnly = true)
    public List<ECKey> getECKeysByClientPublicKey(final String clientPublicKey) {
        final byte[] clientPublicKeyRaw = Base64.getDecoder().decode(clientPublicKey);
        return getECKeysByClientPublicKey(clientPublicKeyRaw);
    }

    @Transactional(readOnly = true)
    public List<ECKey> getECKeysByClientPublicKey(final byte[] clientPublicKeyRaw) {
        final Keys keys = clientKeyDAO.findByClientPublicKey(clientPublicKeyRaw);
        if (keys == null) {
            return Collections.emptyList();
        }
        final List<ECKey> retVal = new ArrayList<>(2);
        retVal.add(ECKey.fromPublicOnly(keys.clientPublicKey()));
        retVal.add(ECKey.fromPrivateAndPrecalculatedPublic(keys.serverPrivateKey(), keys.serverPublicKey()));
        return retVal;
    }

    @Transactional(readOnly = false)
    public Pair<Boolean, Keys> storeKeysAndAddress(final byte[] clientPublicKey, final Address p2shAdderss,
                                                   final byte[] serverPublicKey, final byte[] serverPrivateKey) {
        if (clientPublicKey == null || p2shAdderss == null || serverPublicKey == null || serverPrivateKey == null) {
            throw new IllegalArgumentException("null not excpected here");
        }

        byte[] p2shHash = p2shAdderss.getHash160();

        final Keys clientKey = new Keys()
                .clientPublicKey(clientPublicKey)
                .p2shHash(p2shHash)
                .serverPrivateKey(serverPrivateKey)
                .serverPublicKey(serverPublicKey);

        //need to check if it exists here, as not all DBs does that for us
        final Keys keys = clientKeyDAO.findByClientPublicKey(clientPublicKey);
        if (keys != null) {
            return new Pair<>(false, keys);
        }

        clientKeyDAO.save(clientKey);
        return new Pair<>(true, clientKey);
    }

    @Transactional(readOnly = true)
    public List<List<ECKey>> all() {
        final List<Keys> all = clientKeyDAO.findAll();
        final List<List<ECKey>> retVal = new ArrayList<>();
        for (Keys entity : all) {
            final List<ECKey> keys = new ArrayList<>(2);
            keys.add(ECKey.fromPublicOnly(entity.clientPublicKey()));
            keys.add(ECKey.fromPublicOnly(entity.serverPublicKey()));
            retVal.add(keys);
        }
        return retVal;
    }

    @Transactional(readOnly = false)
    public void addRefundTransaction(byte[] clientPublicKey, byte[] refundTransaction) {
        Refund refund = new Refund();
        refund.clientPublicKey(clientPublicKey);
        refund.refundTx(refundTransaction);
        refund.creationDate(new Date());
        refundDAO.save(refund);
    }

    @Transactional(readOnly = true)
    public List<Transaction> findRefundTransaction(NetworkParameters params, byte[] clientPublicKey) {
        List<Refund> refunds = refundDAO.findByClientPublicKey(clientPublicKey);
        List<Transaction> retVal = new ArrayList<>(refunds.size());
        for (Refund refund : refunds) {
            retVal.add(new Transaction(params, refund.refundTx()));
        }
        return retVal;
    }

    @Transactional(readOnly = true)
    public boolean containsP2SH(Address p2shAddress) {
        return clientKeyDAO.containsP2SH(p2shAddress.getHash160());
    }


    /* SIGN ENDPOINT CODE STARTS HERE */
    @Transactional(readOnly = false)
    public void addTransaction(byte[] clientPublicKey, byte[] serializedTransaction) {
        Tx transaction = new Tx();
        transaction.clientPublicKey(clientPublicKey);
        transaction.tx(serializedTransaction);
        transaction.creationDate(new Date());
        txDAO.save(transaction);
    }

    private final static long LOCK_THRESHOLD_MILLIS = 1000 * 60 * 60 * 4;

    @Transactional(readOnly = true)
    public boolean isTransactionInstant(byte[] clientPublicKey, Script redeemScript, Transaction fullTx) {
        if (fullTx.hasConfidence() && !fullTx.isPending()) {
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
                        if (storedTransactionInput.getOutpoint().toString().equals(relevantTransactionInput.getOutpoint().toString())) {
                            if (!storedTransaction.hashForSignature(storedInputCounter, redeemScript, Transaction.SigHash.ALL, false).equals(fullTx.hashForSignature(relevantInputCounter, redeemScript, Transaction.SigHash.ALL, false))) {
                                long lockTime = storedTransaction.getLockTime() * 1000;
                                long currentTime = System.currentTimeMillis();
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
                TransactionOutput transactionOutput = relevantTransactionInput.getOutpoint().getConnectedOutput();
                Transaction parentTransaction = transactionOutput.getParentTransaction();
                if (isTransactionInstant(clientPublicKey, redeemScript, parentTransaction)) {
                    areParentsInstant = true;
                } else {
                    areParentsInstant = false;
                    break;
                }
            }
            LOG.debug("areParentsInstant {}", areParentsInstant);

            return signedInputCounter == 0 && areParentsInstant;
        }
    }
    /* SIGN ENDPOINT CODE ENDS HERE */
}
