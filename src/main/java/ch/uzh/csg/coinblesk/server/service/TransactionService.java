/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.uzh.csg.coinblesk.server.service;

import ch.uzh.csg.coinblesk.server.dao.ApprovedTxDAO;
import ch.uzh.csg.coinblesk.server.entity.ApprovedTx;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import javax.transaction.Transactional;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Transaction;
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
    public void approveTx(Transaction fullTx, Address p2shAddressFrom, Address p2shAddressTo) {
        ApprovedTx approvedTx = new ApprovedTx()
                .txHash(fullTx.getHash().getBytes())
                .tx(fullTx.bitcoinSerialize())
                .addressFrom(p2shAddressFrom.getHash160())
                .addressTo(p2shAddressTo.getHash160())
                .creationDate(new Date());
        approvedTxDAO.save(approvedTx);
    }
    
}
