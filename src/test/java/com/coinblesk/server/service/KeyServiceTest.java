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
import com.coinblesk.server.entity.Keys;
import com.coinblesk.server.entity.TimeLockedAddressEntity;
import com.coinblesk.server.utilTest.KeyTestUtil;
import com.github.springtestdbunit.DbUnitTestExecutionListener;
import com.github.springtestdbunit.annotation.DatabaseSetup;
import com.github.springtestdbunit.annotation.DatabaseTearDown;
import org.bitcoinj.core.ECKey;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.List;

import static org.junit.Assert.*;

/**
 *
 * @author Thomas Bocek
 * @author Andreas Albrecht
 */
@SpringBootTest
@RunWith(SpringRunner.class)
@TestExecutionListeners( listeners = DbUnitTestExecutionListener.class,
        mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
public class KeyServiceTest {
    
    @Autowired
    private KeyService keyService;

    @Test
    @DatabaseSetup("/EmptyDatabase.xml")
    @DatabaseTearDown("/EmptyDatabase.xml")
    public void testAddKey() throws Exception {
        ECKey ecKeyClient = new ECKey();
        ECKey ecKeyServer = new ECKey();

        boolean retVal = keyService.storeKeysAndAddress(ecKeyClient.getPubKey(), ecKeyServer.getPubKey(),
                ecKeyServer.getPrivKeyBytes()).element0();
        Assert.assertTrue(retVal);
        //adding again should fail
        retVal = keyService.storeKeysAndAddress(ecKeyClient.getPubKey(), ecKeyServer.getPubKey(), ecKeyServer
                .getPrivKeyBytes()).element0();
        Assert.assertFalse(retVal);
    }

    @Test
    @DatabaseSetup("/EmptyDatabase.xml")
    @DatabaseTearDown("/EmptyDatabase.xml")
    public void testAddKey2() throws Exception {
        ECKey ecKeyClient = new ECKey();
        ECKey ecKeyServer = new ECKey();

        boolean retVal = keyService.storeKeysAndAddress(ecKeyClient.getPubKey(), ecKeyServer.getPubKey(),
                ecKeyServer.getPrivKeyBytes()).element0();
        Assert.assertTrue(retVal);
        retVal = keyService.storeKeysAndAddress(ecKeyClient.getPubKey(), ecKeyServer.getPubKey(), ecKeyServer
                .getPrivKeyBytes()).element0();
        Assert.assertFalse(retVal);

        Keys keys = keyService.getByClientPublicKey(ecKeyClient.getPubKey());
        Assert.assertNotNull(keys);

        keys = keyService.getByClientPublicKey(ecKeyClient.getPubKey());
        Assert.assertNotNull(keys);
        //
        List<ECKey> list = keyService.getPublicECKeysByClientPublicKey(ecKeyClient.getPubKey());
        Assert.assertEquals(2, list.size());
        Assert.assertArrayEquals(list.get(0).getPubKey(), ecKeyClient.getPubKey());
        Assert.assertArrayEquals(list.get(1).getPubKey(), ecKeyServer.getPubKey());
    }

    
    @Test
    @DatabaseSetup("/EmptyDatabase.xml")
    @DatabaseSetup("/keys.xml")
    @DatabaseTearDown("/EmptyDatabase.xml")
	public void testGetTimeLockedAddress_EmptyResult() {
		long lockTime = 123456;
		ECKey clientKey = KeyTestUtil.ALICE_CLIENT;
		
		Keys keys = keyService.getByClientPublicKey(clientKey.getPubKey());
		
		TimeLockedAddress address = new TimeLockedAddress(clientKey.getPubKey(), keys.serverPublicKey(), lockTime);
		// do not store -> empty result
		
		TimeLockedAddressEntity fromDB  = keyService.getTimeLockedAddressByAddressHash(address.getAddressHash());
		assertNull(fromDB);
	}

    @Test
    @DatabaseSetup("/EmptyDatabase.xml")
    @DatabaseSetup("/keys.xml")
    @DatabaseTearDown("/EmptyDatabase.xml")
    public void testStoreAndGetTimeLockedAddress() {
    	long lockTime = 123456;
    	ECKey clientKey = KeyTestUtil.ALICE_CLIENT;
    	
    	Keys keys = keyService.getByClientPublicKey(clientKey.getPubKey());
    	
    	TimeLockedAddress address = new TimeLockedAddress(clientKey.getPubKey(), keys.serverPublicKey(), lockTime);
    	TimeLockedAddressEntity intoDB = keyService.storeTimeLockedAddress(keys, address);
    	assertNotNull(intoDB);
    	
    	TimeLockedAddressEntity fromDB  = keyService.getTimeLockedAddressByAddressHash(address.getAddressHash());
    	assertNotNull(fromDB);
    	assertEquals(intoDB, fromDB);
    	
    	keys = keyService.getByClientPublicKey(clientKey.getPubKey());
    	assertTrue(keys.timeLockedAddresses().contains(fromDB));
    }

    @Test
    @DatabaseSetup("/EmptyDatabase.xml")
    @DatabaseSetup("/keys.xml")
    @DatabaseTearDown("/EmptyDatabase.xml")
    public void testStoreAndGetTimeLockedAddresses() {
    	ECKey clientKey = KeyTestUtil.ALICE_CLIENT;
    	
    	Keys keys = keyService.getByClientPublicKey(clientKey.getPubKey());
    	
    	TimeLockedAddress address_1 = new TimeLockedAddress(clientKey.getPubKey(), keys.serverPublicKey(), 42);
    	TimeLockedAddress address_2 = new TimeLockedAddress(clientKey.getPubKey(), keys.serverPublicKey(), 4242);
    	TimeLockedAddressEntity addressEntity_1 = keyService.storeTimeLockedAddress(keys, address_1);
		assertNotNull( addressEntity_1 );
    	TimeLockedAddressEntity addressEntity_2 = keyService.storeTimeLockedAddress(keys, address_2);
		assertNotNull( addressEntity_2 );
    	
    	List<TimeLockedAddressEntity> fromDB  = keyService.getTimeLockedAddressesByClientPublicKey(clientKey.getPubKey());
    	assertNotNull(fromDB);
    	assertTrue(fromDB.size() == 2);
    	assertTrue(fromDB.contains(addressEntity_1));
    	assertTrue(fromDB.contains(addressEntity_2));
    	
    	keys = keyService.getByClientPublicKey(clientKey.getPubKey());
    	assertTrue(keys.timeLockedAddresses().containsAll(fromDB));
    }
}
