/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.uzh.csg.coinblesk.server.service;

import ch.uzh.csg.coinblesk.server.dao.KeyDAO;
import ch.uzh.csg.coinblesk.server.entity.Keys;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import javax.transaction.Transactional;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 *
 * @author Thomas Bocek
 */

@Service
public class KeyService {
    
    private final static Logger LOG = LoggerFactory.getLogger(KeyService.class);
    
    @Autowired
    private KeyDAO clientKeyDAO;
    
    @Transactional
    public Keys getByHash(final String clientHash) {
        final byte[] clientHashRaw = Base64.getDecoder().decode(clientHash);
        return getByHash(clientHashRaw);
    }
    
    @Transactional
    public Keys getByHash(final byte[] clientHashRaw) {
        return clientKeyDAO.getByHash(clientHashRaw);
    }

    @Transactional
    public ECKey getClientECPublicKeyByHash(final String clientHash) {
        final byte[] clientHashRaw = Base64.getDecoder().decode(clientHash);
        return getClientECPublicKeyByHash(clientHashRaw);
    }
    
    @Transactional
    public ECKey getClientECPublicKeyByHash(final byte[] clientHashRaw) {
        final Keys keys = clientKeyDAO.getByHash(clientHashRaw);
        return ECKey.fromPublicOnly(keys.clientPublicKey());
    }
    
    @Transactional
    public ECKey getServerECKeysByHash(final String clientHash) {
        final byte[] clientHashRaw = Base64.getDecoder().decode(clientHash);
        return getServerECKeysByHash(clientHashRaw);
    }
    
    @Transactional
    public ECKey getServerECKeysByHash(final byte[] clientHashRaw) {
        final Keys keys = clientKeyDAO.getByHash(clientHashRaw);
        return ECKey.fromPrivateAndPrecalculatedPublic(keys.serverPrivateKey(), keys.serverPublicKey());
    }
    
    @Transactional
    public List<ECKey> getPublicECKeysByHash(final String clientHash) {
        final byte[] clientHashRaw = Base64.getDecoder().decode(clientHash);
        return getPublicECKeysByHash(clientHashRaw);
    }
    
    @Transactional
    public List<ECKey> getPublicECKeysByHash(final byte[] clientHashRaw) {
        final Keys keys = clientKeyDAO.getByHash(clientHashRaw);
        final List<ECKey> retVal = new ArrayList<>(2);
        retVal.add(ECKey.fromPublicOnly(keys.clientPublicKey()));
        retVal.add(ECKey.fromPublicOnly(keys.serverPublicKey()));
        return Collections.unmodifiableList(retVal);
    }
    
    @Transactional
    public boolean create(final String clientPublicKey, 
            final byte[] serverPublicKey, final byte[] serverPrivateKey) {
        if(clientPublicKey == null || serverPublicKey == null || serverPrivateKey == null){
            throw new IllegalArgumentException("null not excpected here");
	}
        
        final byte[] clientPublicKeyRaw = Base64.getDecoder().decode(clientPublicKey);
        final byte[] clientHashRaw = sha256(clientPublicKeyRaw);
        
        final Keys clientKey = new Keys()
                .clientHash(clientHashRaw)
                .clientPublicKey(clientPublicKeyRaw)
                .serverPrivateKey(serverPrivateKey)
                .serverPublicKey(serverPublicKey);
        
        //need to check if it exists here, as not all DBs does that for us
        final Keys keys = clientKeyDAO.getByHash(clientHashRaw);
        if(keys != null) {
            return false;
        }
        
        clientKeyDAO.save(clientKey);
        return true;
    }
    
    public static byte[] sha256(byte[] input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(input);
        } catch(NoSuchAlgorithmException ex) {
            LOG.error("cannot hash", ex);
            throw new RuntimeException(ex);
        }
    }

    public void addRefundTransaction(Transaction tx) {
        byte[] raw = tx.unsafeBitcoinSerialize();
    }
}
