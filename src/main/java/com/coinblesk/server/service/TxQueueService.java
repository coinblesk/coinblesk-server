/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.coinblesk.server.service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Transaction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.coinblesk.server.dao.TxQueueRepository;
import com.coinblesk.server.entity.TxQueue;

/**
 *
 * @author Thomas Bocek
 */
@Service
public class TxQueueService {

	@Autowired
	private TxQueueRepository repository;

	@Transactional()
	public void addTx(Transaction tx) {
		TxQueue entity = new TxQueue()
				.tx(tx.unsafeBitcoinSerialize())
				.txHash(tx.getHash().getBytes())
				.creationDate(new Date());
		repository.save(entity);
	}

	@Transactional(readOnly = true)
	public List<Transaction> all(NetworkParameters params) {
		Iterable<TxQueue> txQueues = repository.findAll();
		List<Transaction> retVal = new ArrayList<Transaction>();
		for (TxQueue txQueue : txQueues) {
			retVal.add(new Transaction(params, txQueue.tx()));
		}
		return retVal;
	}

	@Transactional()
	public void removeTx(Transaction tx) {
		repository.delete(tx.getHash().getBytes());
	}
}
