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
package com.coinblesk.server.entity;

import java.io.Serializable;
import java.util.List;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.OneToMany;
import javax.persistence.OrderBy;
import javax.persistence.Table;


/**
 *
 * @author Thomas Bocek
 */
@Entity(name = "KEYS")
@Table(indexes = {
		@Index(name = "KEYS_CLIENT_PUBLIC_KEY", columnList = "CLIENT_PUBLIC_KEY")
})
public class Keys implements Serializable {

    private static final long serialVersionUID = -7496348013847426913L;

    @Id
    @GeneratedValue(strategy=GenerationType.SEQUENCE)
    private long id;
    
    @Column(name = "CLIENT_PUBLIC_KEY", unique = true, nullable = false, updatable = false, length = 255)
    private byte[] clientPublicKey;

    @Column(name = "SERVER_PUBLIC_KEY", unique = true, nullable = false, updatable = false, length = 255)
    private byte[] serverPublicKey;

    @Column(name = "SERVER_PRIVATE_KEY", unique = true, nullable = false, updatable = false, length = 255)
    private byte[] serverPrivateKey;

    @Column(name = "TIME_CREATED", updatable = false, nullable = false)
	private long timeCreated;
    
    @OneToMany(mappedBy="keys", fetch = FetchType.EAGER)
    @OrderBy("TIME_CREATED ASC")
    private List<TimeLockedAddressEntity> timeLockedAddresses; 
    
    public byte[] clientPublicKey() {
        return clientPublicKey;
    }

    public Keys clientPublicKey(byte[] clientPublicKey) {
        this.clientPublicKey = clientPublicKey;
        return this;
    }

    public byte[] serverPublicKey() {
        return serverPublicKey;
    }

    public Keys serverPublicKey(byte[] serverPublicKey) {
        this.serverPublicKey = serverPublicKey;
        return this;
    }

    public byte[] serverPrivateKey() {
        return serverPrivateKey;
    }

    public Keys serverPrivateKey(byte[] serverPrivateKey) {
        this.serverPrivateKey = serverPrivateKey;
        return this;
    }
    
    public long timeCreated() {
    	return timeCreated;
    }
    
    public Keys timeCreated(long timeCreatedSeconds) {
    	this.timeCreated = timeCreatedSeconds;
    	return this;
    }

    public List<TimeLockedAddressEntity> timeLockedAddresses() {
    	return timeLockedAddresses;
    }
    
    public TimeLockedAddressEntity latestTimeLockedAddresses() {
    	return timeLockedAddresses.get(timeLockedAddresses.size() - 1);
    }
    
    public Keys timeLockedAddresses(List<TimeLockedAddressEntity> timeLockedAddresses) {
	this.timeLockedAddresses = timeLockedAddresses;
	return this;
    }
	
	/*public List<Address> btcAddresses(NetworkParameters params) {
		List<Address> addressList = new ArrayList<>(addresses.size());
		for (TimeLockedAddressEntity e : addresses) {
			addressList.add(e.toAddress(params));
		}
		// make explicit that this list is not stored in DB!
		return Collections.unmodifiableList(addressList);
	}*/
}
