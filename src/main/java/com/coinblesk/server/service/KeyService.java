/*
 * Copyright 2016 The Coinblesk team and the CSG Group at University of Zurich
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.coinblesk.server.service;

import com.coinblesk.bitcoin.TimeLockedAddress;
import com.coinblesk.server.dao.TimeLockedAddressDAO;
import com.coinblesk.server.dao.KeyDAO;
import com.coinblesk.server.entity.Keys;
import com.coinblesk.server.entity.TimeLockedAddressEntity;
import com.coinblesk.util.Pair;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.Utils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 *
 * @author Thomas Bocek
 * @author Andreas Albrecht
 */
@Service
public class KeyService {

    @Autowired
    private KeyDAO clientKeyDAO;
    
    @Autowired
    private TimeLockedAddressDAO addressDAO;
    
    @Transactional(readOnly = true)
    public Keys getByClientPublicKey(final byte[] clientPublicKey) {
        return clientKeyDAO.findByClientPublicKey(clientPublicKey);
    }

    @Transactional(readOnly = true)
    public List<ECKey> getPublicECKeysByClientPublicKey(final byte[] clientPublicKey) {
        final Keys keys = clientKeyDAO.findByClientPublicKey(clientPublicKey);
        final List<ECKey> retVal = new ArrayList<>(2);
        retVal.add(ECKey.fromPublicOnly(keys.clientPublicKey()));
        retVal.add(ECKey.fromPublicOnly(keys.serverPublicKey()));
        return retVal;
    }
    
    @Transactional(readOnly = true)
    public List<ECKey> getECKeysByClientPublicKey(final byte[] clientPublicKey) {
        final Keys keys = clientKeyDAO.findByClientPublicKey(clientPublicKey);
        if(keys == null) {
            return Collections.emptyList();
        }
        final List<ECKey> retVal = new ArrayList<>(2);
        retVal.add(ECKey.fromPublicOnly(keys.clientPublicKey()));
        retVal.add(ECKey.fromPrivateAndPrecalculatedPublic(keys.serverPrivateKey(), keys.serverPublicKey()));
        return retVal;
    }

    @Transactional(readOnly = false)
    public Pair<Boolean, Keys> storeKeysAndAddress(final byte[] clientPublicKey,
            final byte[] serverPublicKey, final byte[] serverPrivateKey) {
        if (clientPublicKey == null || serverPublicKey == null || serverPrivateKey == null ) {
            throw new IllegalArgumentException("null not excpected here");
        }

        //need to check if it exists here, as not all DBs do that for us
        final Keys keys = clientKeyDAO.findByClientPublicKey(clientPublicKey);
        if (keys != null) {
            return new Pair<>(false, keys);
        }
        
        final Keys clientKey = new Keys()
                .clientPublicKey(clientPublicKey)
                .serverPrivateKey(serverPrivateKey)
                .serverPublicKey(serverPublicKey);

        final Keys storedKeys = clientKeyDAO.save(clientKey);
        return new Pair<>(true, storedKeys);
    }

    @Transactional(readOnly = true)
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
    
    @Transactional(readOnly = true)
    public List<Keys> allKeys() {
    	return clientKeyDAO.findAll();
    }
    
    @Transactional(readOnly = false)
	public TimeLockedAddressEntity storeTimeLockedAddress(Keys keys, TimeLockedAddress address) {
		if (address == null || keys == null) {
			throw new IllegalArgumentException("Address/keys must not be null");
		}
		if (keys.serverPrivateKey() == null || keys.serverPublicKey() == null || 
				keys.clientPublicKey() == null) {
			throw new IllegalArgumentException("Keys must not be null.");
		}
		if (address.getAddressHash() == null) {
			throw new IllegalArgumentException("AddressHash must not be null");
		}
		
		TimeLockedAddressEntity addressEntity = new TimeLockedAddressEntity();
		addressEntity
				.setLockTime(address.getLockTime())
				.setAddressHash(address.getAddressHash())
				.setRedeemScript(address.createRedeemScript().getProgram())
				.setTimeCreated(Utils.currentTimeSeconds())
				.setKeys(keys);
		
		TimeLockedAddressEntity result = addressDAO.save(addressEntity);
		return result;
	}
    
    public boolean addressExists(byte[] addressHash) {
    	return addressDAO.findTimeLockedAddressByAddressHash(addressHash) != null;
    }
    
    public TimeLockedAddressEntity getTimeLockedAddressByAddressHash(byte[] addressHash) {
    	if (addressHash == null) {
    		throw new IllegalArgumentException("addressHash must not be null.");
    	}
    	return addressDAO.findTimeLockedAddressByAddressHash(addressHash);
    }
    
    public List<TimeLockedAddressEntity> getTimeLockedAddressesByClientPublicKey(byte[] publicKey) {
    	if (publicKey == null || publicKey.length <= 0) {
    		throw new IllegalArgumentException("publicKey must not be null");
    	}
    	return addressDAO.findTimeLockedAddressesByClientPublicKey(publicKey);
    }
    
    public TimeLockedAddressEntity findAddressByAddressHash(byte[] addressHash) {
    	if (addressHash == null) {
			throw new IllegalArgumentException("addressHash must not be null.");
		}
    	TimeLockedAddressEntity address = addressDAO.findAddressByAddressHash(addressHash);
    	return address;
    }
    
    public byte[] getRedeemScriptByAddressHash(byte[] addressHash) {
		TimeLockedAddressEntity address = findAddressByAddressHash(addressHash);
		byte[] data = address != null ? address.getRedeemScript() : null;
		return data;
	}
}
