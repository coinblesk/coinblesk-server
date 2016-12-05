package com.coinblesk.server.dao;

import com.coinblesk.server.entity.TxQueue;
import org.springframework.data.repository.CrudRepository;

public interface TxQueueRepository extends CrudRepository<TxQueue, byte[]>
{
}
