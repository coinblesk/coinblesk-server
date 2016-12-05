package com.coinblesk.server.dao;

import com.coinblesk.server.entity.Tx;
import org.springframework.data.repository.CrudRepository;

import java.util.List;

public interface TxRepository extends CrudRepository<Tx, byte[]>
{
    List<Tx> findByClientPublicKeyAndApproved(final byte[] clientPublicKey, final boolean approved);
    List<Tx> findByApproved(final boolean approved);
}
