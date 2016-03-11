/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.uzh.csg.coinblesk.server.service;

import ch.uzh.csg.coinblesk.server.dao.ApprovedTxDAO;
import ch.uzh.csg.coinblesk.server.dao.BurnedOutputDAO;
import ch.uzh.csg.coinblesk.server.entity.ApprovedTx;
import ch.uzh.csg.coinblesk.server.entity.BurnedOutput;
import com.coinblesk.util.Pair;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutPoint;
import org.bitcoinj.core.TransactionOutput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 *
 * @author draft
 */
@Service
public class TransactionService {
    
    private final static Logger LOG = LoggerFactory.getLogger(TransactionService.class);

    @Autowired
    private ApprovedTxDAO approvedTxDAO;
    
    @Autowired
    private BurnedOutputDAO burnedOutputDAO;
    
    @Transactional(readOnly = true)
    public List<TransactionOutput> approvedOutputs(NetworkParameters params, Address p2shAddress) {
        List<ApprovedTx> approved = approvedTxDAO.findByAddress(p2shAddress.getHash160());
        List<TransactionOutput> retVal = new ArrayList<>();
        for(ApprovedTx approvedTx:approved) {
            Transaction tx = new Transaction(params, approvedTx.tx());
            LOG.debug("approved for receiving {}", tx);
            for(TransactionOutput transactionOutput:tx.getOutputs()) {
                if(p2shAddress.equals(transactionOutput.getAddressFromP2SH(params))) {
                    retVal.add(transactionOutput);
                }
            }
        }
        return retVal;
    }
    
    @Transactional(readOnly = true)
    public List<TransactionOutPoint> spentOutputs(NetworkParameters params, Address p2shAddress) {
        List<ApprovedTx> approved = approvedTxDAO.findByAddress(p2shAddress.getHash160());
        List<TransactionOutPoint> retVal = new ArrayList<>();
        for(ApprovedTx approvedTx:approved) {
            Transaction tx = new Transaction(params, approvedTx.tx());
            LOG.debug("approved spent {}", tx);
            for(TransactionInput transactionInput:tx.getInputs()) {
                retVal.add(transactionInput.getOutpoint());
            }
        }
        return retVal;
    }
    
    @Transactional(readOnly = false)
    public List<Pair<TransactionOutPoint, Integer>> burnOutputFromNewTransaction(
            NetworkParameters params, byte[] clientPublicKey, List<TransactionInput> inputsFromNewTransaction) {
        final List<Pair<TransactionOutPoint, Integer>> retVal = new ArrayList<>(2 * inputsFromNewTransaction.size());
        for(TransactionInput transactionInput:inputsFromNewTransaction) {
            byte[] outpoints = transactionInput.getOutpoint().bitcoinSerialize();
            BurnedOutput burnedOutput = burnedOutputDAO.findByTxOutpoint(outpoints);
            if(burnedOutput != null) {
                burnedOutput.txOutpointCounter(burnedOutput.txOutpointCounter() + 1);
            } else {
                burnedOutput = new BurnedOutput()
                    .txOutpoint(outpoints)
                    .txOutpointCounter(1)
                    .clientPublicKey(clientPublicKey)
                    .creationDate(new Date());
                burnedOutputDAO.save(burnedOutput);
            }
            TransactionOutPoint txOutpoint = new TransactionOutPoint(params, burnedOutput.txOutpoint(), 0);
            retVal.add(new Pair<>(txOutpoint, burnedOutput.txOutpointCounter()));
        }
        return retVal;
    }
    
    
    

    @Transactional(readOnly = true)
    public List<TransactionOutPoint> burnedOutpoints(NetworkParameters params, byte[] clientPublicKey) {
        final List<TransactionOutPoint> retVal = new ArrayList<>();
        List<BurnedOutput> burnedOutputs = burnedOutputDAO.findByClientKey(clientPublicKey);
        for(BurnedOutput burnedOutput:burnedOutputs) {
            retVal.add(new TransactionOutPoint(params, burnedOutput.txOutpoint(), 0));
        }
        return retVal;
    }
    
    @Transactional(readOnly = false)
    public boolean checkInstantTx(NetworkParameters params, Transaction fullTx, 
            byte[] clientPublicKey, Address p2shAddressFrom, Address p2shAddressTo) {
         List<Pair<TransactionOutPoint, Integer>> outpoints = burnOutputFromNewTransaction(
                    params, clientPublicKey, fullTx.getInputs());
            boolean instantPayment = true;
            for(Pair<TransactionOutPoint, Integer> p:outpoints) {
                if(p.element1() != 2) {
                    instantPayment = false;
                    break;
                }
            }
            
            if(instantPayment) {
                approveTx(fullTx, p2shAddressFrom, p2shAddressTo);
                //removeConfirmedBurnedOutput(fullTx.getInputs());
                return true;
            } else {
                return false;
            }
    }
    
    public void approveTx(Transaction fullTx, Address p2shAddressFrom, Address p2shAddressTo) {
        ApprovedTx approvedTx = new ApprovedTx()
                .txHash(fullTx.getHash().getBytes())
                .tx(fullTx.unsafeBitcoinSerialize())
                .addressFrom(p2shAddressFrom.getHash160())
                .addressTo(p2shAddressTo.getHash160())
                .creationDate(new Date());
        approvedTxDAO.save(approvedTx);
    }
    
    public void approveTx2(Transaction fullTx, byte[] clientPubKey, byte[] merchantPubKey) {
        ApprovedTx approvedTx = new ApprovedTx()
                .txHash(fullTx.getHash().getBytes())
                .tx(fullTx.unsafeBitcoinSerialize())
                .addressFrom(clientPubKey)
                .addressTo(merchantPubKey)
                .creationDate(new Date());
        approvedTxDAO.save(approvedTx);
    }
    
    public List<Transaction> approvedTx2(NetworkParameters params, byte[] pubKey) {
        List<ApprovedTx> approved = approvedTxDAO.findByAddress(pubKey);
        List<Transaction> retVal = new ArrayList<>(approved.size());
        for(ApprovedTx approvedTx:approved) {
            Transaction tx = new Transaction(params, approvedTx.tx());
            retVal.add(tx);
        }
        return retVal;
    }
    
    @Transactional(readOnly = false)
    public void removeConfirmedBurnedOutput(List<TransactionInput> inputsFromConfirmedTransaction) {
        for(TransactionInput transactionInput:inputsFromConfirmedTransaction) {
            byte[] outpoints = transactionInput.getOutpoint().bitcoinSerialize();
            burnedOutputDAO.remove(outpoints);
        }         
    }
    
    @Transactional(readOnly = false)
    public int removeAllBurnedOutput() {
        return burnedOutputDAO.removeAll();
    }

    @Transactional(readOnly = false)
    public void removeApproved(Transaction approved) {
        approvedTxDAO.remove(approved.getHash().getBytes());
    }

    @Transactional(readOnly = true)
    public List<Transaction> approvedTx(NetworkParameters params) {
        List<ApprovedTx> approved = approvedTxDAO.findAll();
        List<Transaction> retVal = new ArrayList<>(approved.size());
        for(ApprovedTx approvedTx:approved) {
            Transaction tx = new Transaction(params, approvedTx.tx());
            retVal.add(tx);
        }
        return retVal;
    }
}
