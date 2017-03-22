package com.coinblesk.server.dao;

import org.springframework.data.repository.CrudRepository;

import com.coinblesk.server.entity.TxQueue;

public interface TxQueueRepository extends CrudRepository<TxQueue, byte[]> {
}
