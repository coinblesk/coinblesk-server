/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.uzh.csg.coinblesk.server.service;

import ch.uzh.csg.coinblesk.server.config.BeanConfig;
import ch.uzh.csg.coinblesk.server.entity.Keys;
import com.github.springtestdbunit.DbUnitTestExecutionListener;
import java.util.Base64;
import java.util.List;
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


/**
 *
 * @author Thomas Bocek
 */
@RunWith(SpringJUnit4ClassRunner.class)
@TestExecutionListeners({DependencyInjectionTestExecutionListener.class, TransactionalTestExecutionListener.class,
    DbUnitTestExecutionListener.class})
@WebAppConfiguration
@ContextConfiguration(classes = {BeanConfig.class})
public class KeyServiceTest {
    
    @Autowired
    private KeyService keyService;
    
    @Test
    public void testAddKey() throws Exception {
        ECKey ecKeyClient = new ECKey();
        String clientPublicKey = Base64.getEncoder().encodeToString(ecKeyClient.getPubKey());
        ECKey ecKeyServer = new ECKey();
        byte[] pubKey = ecKeyServer.getPubKey();
        byte[] privKey = ecKeyServer.getPrivKeyBytes();

        boolean retVal = keyService.create(clientPublicKey, pubKey, privKey);
        Assert.assertTrue(retVal);
        Keys keys = keyService.getByHash(KeyService.sha256(ecKeyClient.getPubKey()));
        Assert.assertNotNull(keys);
        //adding again should fail
        retVal = keyService.create(clientPublicKey, pubKey, privKey);
        Assert.assertFalse(retVal);
    }
    
    @Test
    public void testAddKey2() throws Exception {
        ECKey ecKeyClient = new ECKey();
        String clientPublicKey = Base64.getEncoder().encodeToString(ecKeyClient.getPubKey());
        ECKey ecKeyServer = new ECKey();
        byte[] pubKey = ecKeyServer.getPubKey();
        byte[] privKey = ecKeyServer.getPrivKeyBytes();

        boolean retVal = keyService.create(clientPublicKey, pubKey, privKey);
        Assert.assertTrue(retVal);
        byte[] hash = KeyService.sha256(ecKeyClient.getPubKey());
        Keys keys = keyService.getByHash(hash);
        Assert.assertNotNull(keys);
        
        String key = Base64.getEncoder().encodeToString(hash);
        keys = keyService.getByHash(key);
        Assert.assertNotNull(keys);
        //
        ECKey ec = keyService.getClientECPublicKeyByHash(key);
        Assert.assertArrayEquals(ec.getPubKey(), ecKeyClient.getPubKey());
        //
        ec = keyService.getClientECPublicKeyByHash(hash);
        Assert.assertArrayEquals(ec.getPubKey(), ecKeyClient.getPubKey());
        //
        ec = keyService.getServerECKeysByHash(hash);
        Assert.assertArrayEquals(ec.getPubKey(), ecKeyServer.getPubKey());
        Assert.assertArrayEquals(ec.getPrivKeyBytes(), ecKeyServer.getPrivKeyBytes());
        //
        ec = keyService.getServerECKeysByHash(key);
        Assert.assertArrayEquals(ec.getPubKey(), ecKeyServer.getPubKey());
        Assert.assertArrayEquals(ec.getPrivKeyBytes(), ecKeyServer.getPrivKeyBytes());
        //
        List<ECKey> list = keyService.getPublicECKeysByHash(hash);
        Assert.assertEquals(2, list.size());
        Assert.assertArrayEquals(list.get(0).getPubKey(), ecKeyClient.getPubKey());
        Assert.assertArrayEquals(list.get(1).getPubKey(), ecKeyServer.getPubKey());
    }
    
}
