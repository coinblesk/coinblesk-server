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
import com.coinblesk.json.CompleteSignTO;
import com.coinblesk.json.Type;
import com.coinblesk.util.Pair;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import javax.transaction.Transactional;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutPoint;
import org.bitcoinj.core.TransactionOutput;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 *
 * @author draft
 */
@Service
public class TransactionService {

    @Autowired
    private ApprovedTxDAO approvedTxDAO;
    
    @Autowired
    private BurnedOutputDAO burnedOutputDAO;
    
    @Transactional
    public List<TransactionOutput> approvedReceiving(NetworkParameters params, Address p2shAddress) {
        List<ApprovedTx> approved = approvedTxDAO.findByAddressTo(p2shAddress.getHash160());
        List<TransactionOutput> retVal = new ArrayList<>();
        for(ApprovedTx approvedTx:approved) {
            Transaction tx = new Transaction(params, approvedTx.tx());
            retVal.addAll(tx.getOutputs());
        }
        return retVal;
    }

    @Transactional
    public List<TransactionOutput> approvedSpending(NetworkParameters params, Address p2shAddress) {
        List<ApprovedTx> approved = approvedTxDAO.findByAddressFrom(p2shAddress.getHash160());
        List<TransactionOutput> retVal = new ArrayList<>();
        for(ApprovedTx approvedTx:approved) {
            Transaction tx = new Transaction(params, approvedTx.tx());
            retVal.addAll(tx.getOutputs());
        }
        return retVal;
    }

    
    
    @Transactional
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
    
    
    

    @Transactional
    public List<TransactionOutPoint> burnedOutpoints(NetworkParameters params, byte[] clientPublicKey) {
        final List<TransactionOutPoint> retVal = new ArrayList<>();
        List<BurnedOutput> burnedOutputs = burnedOutputDAO.findByClientKey(clientPublicKey);
        for(BurnedOutput burnedOutput:burnedOutputs) {
            retVal.add(new TransactionOutPoint(params, burnedOutput.txOutpoint(), 0));
        }
        return retVal;
    }
    
    @Transactional
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
                removeConfirmedBurnedOutput(fullTx.getInputs());
                return true;
            } else {
                return false;
            }
    }
    
    private void approveTx(Transaction fullTx, Address p2shAddressFrom, Address p2shAddressTo) {
        ApprovedTx approvedTx = new ApprovedTx()
                .txHash(fullTx.getHash().getBytes())
                .tx(fullTx.unsafeBitcoinSerialize())
                .addressFrom(p2shAddressFrom.getHash160())
                .addressTo(p2shAddressTo.getHash160())
                .creationDate(new Date());
        approvedTxDAO.save(approvedTx);
    }
    
    @Transactional
    public void removeConfirmedBurnedOutput(List<TransactionInput> inputsFromConfirmedTransaction) {
        for(TransactionInput transactionInput:inputsFromConfirmedTransaction) {
            byte[] outpoints = transactionInput.getOutpoint().bitcoinSerialize();
            burnedOutputDAO.remove(outpoints);
        }         
    }

    @Transactional
    public void removeApproved(Transaction approved) {
        approvedTxDAO.remove(approved.getHash().getBytes());
    }
}
