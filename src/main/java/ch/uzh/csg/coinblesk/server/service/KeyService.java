/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.uzh.csg.coinblesk.server.service;

import ch.uzh.csg.coinblesk.server.dao.KeyDAO;
import ch.uzh.csg.coinblesk.server.dao.RefundDAO;
import ch.uzh.csg.coinblesk.server.dao.ReplayProtectionDAO;
import ch.uzh.csg.coinblesk.server.entity.Keys;
import ch.uzh.csg.coinblesk.server.entity.Refund;
import ch.uzh.csg.coinblesk.server.entity.ReplayProtection;
import com.coinblesk.util.Pair;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import javax.transaction.Transactional;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.ECKey;
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
    
    @Autowired
    private RefundDAO refundDAO;
    
    @Autowired
    private ReplayProtectionDAO replayProtectionDAO;
    
    @Transactional
    public Keys getByClientPublicKey(final String clientPublicKey) {
        final byte[] clientPublicKeyRaw = Base64.getDecoder().decode(clientPublicKey);
        return getByClientPublicKey(clientPublicKeyRaw);
    }

    @Transactional
    public Keys getByClientPublicKey(final byte[] clientPublicKeyRaw) {
        return clientKeyDAO.findByClientPublicKey(clientPublicKeyRaw);
    }

    @Transactional
    public List<ECKey> getPublicECKeysByClientPublicKey(final String clientPublicKey) {
        final byte[] clientPublicKeyRaw = Base64.getDecoder().decode(clientPublicKey);
        return getPublicECKeysByClientPublicKey(clientPublicKeyRaw);
    }

    @Transactional
    public List<ECKey> getPublicECKeysByClientPublicKey(final byte[] clientPublicKeyRaw) {
        final Keys keys = clientKeyDAO.findByClientPublicKey(clientPublicKeyRaw);
        final List<ECKey> retVal = new ArrayList<>(2);
        retVal.add(ECKey.fromPublicOnly(keys.clientPublicKey()));
        retVal.add(ECKey.fromPublicOnly(keys.serverPublicKey()));
        return Collections.unmodifiableList(retVal);
    }
    
    @Transactional
    public List<ECKey> getECKeysByClientPublicKey(final String clientPublicKey) {
        final byte[] clientPublicKeyRaw = Base64.getDecoder().decode(clientPublicKey);
        return getECKeysByClientPublicKey(clientPublicKeyRaw);
    }
    
    @Transactional
    public List<ECKey> getECKeysByClientPublicKey(final byte[] clientPublicKeyRaw) {
        final Keys keys = clientKeyDAO.findByClientPublicKey(clientPublicKeyRaw);
        if(keys == null) {
            return Collections.emptyList();
        }
        final List<ECKey> retVal = new ArrayList<>(2);
        retVal.add(ECKey.fromPublicOnly(keys.clientPublicKey()));
        retVal.add(ECKey.fromPrivateAndPrecalculatedPublic(keys.serverPrivateKey(), keys.serverPublicKey()));
        return Collections.unmodifiableList(retVal);
    }

    @Transactional
    public Pair<Boolean, Keys> storeKeysAndAddress(final byte[] clientPublicKey, final Address p2shAdderss,
            final byte[] serverPublicKey, final byte[] serverPrivateKey) {
        if (clientPublicKey == null || p2shAdderss == null || serverPublicKey == null || serverPrivateKey == null ) {
            throw new IllegalArgumentException("null not excpected here");
        }
        
        byte[] p2shHash = p2shAdderss.getHash160();

        final Keys clientKey = new Keys()
                .clientPublicKey(clientPublicKey)
                .p2shHash(p2shHash)
                .serverPrivateKey(serverPrivateKey)
                .serverPublicKey(serverPublicKey);

        //need to check if it exists here, as not all DBs does that for us
        final Keys keys = clientKeyDAO.findByClientPublicKey(clientPublicKey);
        if (keys != null) {
            return new Pair<>(false, keys);
        }

        clientKeyDAO.save(clientKey);
        return new Pair<>(true, clientKey);
    }

    @Transactional
    public List<List<ECKey>> all() {
        final List<Keys> all = clientKeyDAO.findAll();
        final List<List<ECKey>> retVal = new ArrayList<>();
        for (Keys entity : all) {
            final List<ECKey> keys = new ArrayList<>(2);
            keys.add(ECKey.fromPublicOnly(entity.clientPublicKey()));
            keys.add(ECKey.fromPublicOnly(entity.serverPublicKey()));
            retVal.add(keys);
        }
        return retVal;
    }
    
    @Transactional
    public void addRefundTransaction(byte[] clientPublicKey, byte[] refundTransaction) {
       Refund refund = new Refund();
       refund.clientPublicKey(clientPublicKey);
       refund.refundTx(refundTransaction);
       refund.creationDate(new Date());
       refundDAO.save(refund);
    }
    
    @Transactional
    public boolean containsP2SH(Address p2shAddress) {
        return clientKeyDAO.containsP2SH(p2shAddress.getHash160());
    }
    
    @Transactional
    public boolean checkReplayAttack(byte[] clientPublicKey, String endpoint, Date currentDate) {
        ReplayProtection seen = replayProtectionDAO.findByClientPublicKeyDate(clientPublicKey, endpoint, currentDate);
        if(seen != null) {
            return false;
        }
        //TODO: clean old entries, eg. two days old
        ReplayProtection newEntry = new ReplayProtection()
                .clientPublicKey(clientPublicKey)
                .endpoint(endpoint)
                .seenDate(currentDate);
        replayProtectionDAO.save(newEntry);
        return true;
    }
    
}
