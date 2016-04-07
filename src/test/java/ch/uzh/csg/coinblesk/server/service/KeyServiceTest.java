package ch.uzh.csg.coinblesk.server.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.ECKey;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;
import org.springframework.test.context.transaction.TransactionalTestExecutionListener;
import org.springframework.test.context.web.WebAppConfiguration;

import com.coinblesk.bitcoin.TimeLockedAddress;
import com.github.springtestdbunit.DbUnitTestExecutionListener;
import com.github.springtestdbunit.annotation.DatabaseSetup;
import com.github.springtestdbunit.annotation.DatabaseTearDown;

import ch.uzh.csg.coinblesk.server.config.AppConfig;
import ch.uzh.csg.coinblesk.server.config.BeanConfig;
import ch.uzh.csg.coinblesk.server.controller.KeyTest.KeyTestUtil;
import ch.uzh.csg.coinblesk.server.entity.Keys;
import ch.uzh.csg.coinblesk.server.entity.TimeLockedAddressEntity;


/**
 * @author Thomas Bocek
 * @author Andreas Albrecht
 */
@RunWith(SpringJUnit4ClassRunner.class)
@TestExecutionListeners({DependencyInjectionTestExecutionListener.class, TransactionalTestExecutionListener.class,
    DbUnitTestExecutionListener.class})
@WebAppConfiguration
@ContextConfiguration(classes = {BeanConfig.class})
public class KeyServiceTest {
    
    @Autowired
    private KeyService keyService;
    
    @Autowired
    private AppConfig appConfig;
    
    @Test
    public void testAddKey() throws Exception {
        ECKey ecKeyClient = new ECKey();
        ECKey ecKeyServer = new ECKey();
        Address address = ecKeyClient.toAddress(appConfig.getNetworkParameters());
        byte[] pubKey = ecKeyServer.getPubKey();
        byte[] privKey = ecKeyServer.getPrivKeyBytes();

        boolean retVal = keyService.storeKeysAndAddress(ecKeyClient.getPubKey(), address, pubKey, privKey).element0();
        Assert.assertTrue(retVal);
        //adding again should fail
        retVal = keyService.storeKeysAndAddress(ecKeyClient.getPubKey(), address, pubKey, privKey).element0();
        Assert.assertFalse(retVal);
    }
    
    @Test
    public void testAddKey2() throws Exception {
        ECKey ecKeyClient = new ECKey();
        ECKey ecKeyServer = new ECKey();
        Address address = ecKeyServer.toAddress(appConfig.getNetworkParameters());
        
        byte[] pubKey = ecKeyServer.getPubKey();
        byte[] privKey = ecKeyServer.getPrivKeyBytes();

        boolean retVal = keyService.storeKeysAndAddress(ecKeyClient.getPubKey(), address, pubKey, privKey).element0();
        Assert.assertTrue(retVal);
        retVal = keyService.storeKeysAndAddress(ecKeyClient.getPubKey(), address, pubKey, privKey).element0();
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
	@DatabaseSetup("classpath:DbUnitFiles/clientKey.xml")
	@DatabaseTearDown("classpath:DbUnitFiles/emptyAddresses.xml")
	public void testGetTimeLockedAddress_EmptyResult() {
		long lockTime = 123456;
		ECKey clientKey = KeyTestUtil.ALICE_CLIENT;
		
		Keys keys = keyService.getByClientPublicKey(clientKey.getPubKey());
		
		TimeLockedAddress address = new TimeLockedAddress(clientKey.getPubKey(), keys.serverPublicKey(), lockTime, appConfig.getNetworkParameters());
		// do not store -> empty result
		
		TimeLockedAddressEntity fromDB  = keyService.getTimeLockedAddressByAddressHash(address.getAddressHash());
		assertNull(fromDB);
	}

	@Test
    @DatabaseSetup("classpath:DbUnitFiles/clientKey.xml")
    @DatabaseTearDown("classpath:DbUnitFiles/emptyAddresses.xml")
    public void testStoreAndGetTimeLockedAddress() {
    	long lockTime = 123456;
    	ECKey clientKey = KeyTestUtil.ALICE_CLIENT;
    	
    	Keys keys = keyService.getByClientPublicKey(clientKey.getPubKey());
    	
    	TimeLockedAddress address = new TimeLockedAddress(clientKey.getPubKey(), keys.serverPublicKey(), lockTime, appConfig.getNetworkParameters());
    	TimeLockedAddressEntity intoDB = keyService.storeTimeLockedAddress(keys, address);
    	assertNotNull(intoDB);
    	
    	TimeLockedAddressEntity fromDB  = keyService.getTimeLockedAddressByAddressHash(address.getAddressHash());
    	assertNotNull(fromDB);
    	assertEquals(intoDB, fromDB);
    	
    	keys = keyService.getByClientPublicKey(clientKey.getPubKey());
    	assertTrue(keys.addresses().contains(fromDB));
    }

    @Test
    @DatabaseSetup("classpath:DbUnitFiles/clientKey.xml")
    @DatabaseTearDown("classpath:DbUnitFiles/emptyAddresses.xml")
    public void testStoreAndGetTimeLockedAddresses() {
    	ECKey clientKey = KeyTestUtil.ALICE_CLIENT;
    	
    	Keys keys = keyService.getByClientPublicKey(clientKey.getPubKey());
    	
    	TimeLockedAddress address_1 = new TimeLockedAddress(clientKey.getPubKey(), keys.serverPublicKey(), 42, appConfig.getNetworkParameters());
    	TimeLockedAddress address_2 = new TimeLockedAddress(clientKey.getPubKey(), keys.serverPublicKey(), 4242, appConfig.getNetworkParameters());
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
    	assertTrue(keys.addresses().containsAll(fromDB));
    }
}