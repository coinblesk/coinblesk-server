package com.coinblesk.server.dao;

import java.util.List;

import org.springframework.data.repository.CrudRepository;

import com.coinblesk.server.entity.Tx;

public interface TxRepository extends CrudRepository<Tx, byte[]> {

	List<Tx> findByClientPublicKeyAndApproved(final byte[] clientPublicKey, final boolean approved);

	List<Tx> findByApproved(final boolean approved);
}
