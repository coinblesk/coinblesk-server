/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.coinblesk.server.service;

import com.coinblesk.server.dao.TxQueueDAO;
import com.coinblesk.server.entity.TxQueue;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Transaction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 *
 * @author Thomas Bocek
 */
public class TxQueueService {

    @Autowired
    private TxQueueDAO txQueueDAO;

    @Transactional(readOnly = false)
    public void addTx(Transaction tx) {
        TxQueue entity = new TxQueue()
                .tx(tx.unsafeBitcoinSerialize())
                .txHash(tx.getHash().getBytes())
                .creationDate(new Date());
        txQueueDAO.save(entity);
    }
    
    @Transactional(readOnly = true)
    public List<Transaction> all(NetworkParameters params) {
        List<TxQueue> txQueues = txQueueDAO.findAll();
        List<Transaction> retVal = new ArrayList<Transaction>(txQueues.size());
        for(TxQueue txQueue:txQueues) {
            retVal.add(new Transaction(params, txQueue.tx()));
        }
        return retVal;
    }
    
    @Transactional(readOnly = false)
    public void removeTx(Transaction tx) {
        txQueueDAO.remove(tx.getHash().getBytes());
    }
}
