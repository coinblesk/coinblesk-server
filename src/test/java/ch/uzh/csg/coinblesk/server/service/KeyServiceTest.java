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
        byte[] hash = new byte[20];
        hash[0]=1;
        byte[] pubKey = ecKeyServer.getPubKey();
        byte[] privKey = ecKeyServer.getPrivKeyBytes();

        boolean retVal = keyService.create(clientPublicKey, hash, pubKey, privKey).element0();
        Assert.assertTrue(retVal);
        //adding again should fail
        retVal = keyService.create(clientPublicKey, hash, pubKey, privKey).element0();
        Assert.assertFalse(retVal);
    }
    
    @Test
    public void testAddKey2() throws Exception {
        ECKey ecKeyClient = new ECKey();
        String clientPublicKey = Base64.getEncoder().encodeToString(ecKeyClient.getPubKey());
        ECKey ecKeyServer = new ECKey();
        byte[] hash = new byte[20];
        hash[0]=2;
        byte[] pubKey = ecKeyServer.getPubKey();
        byte[] privKey = ecKeyServer.getPrivKeyBytes();

        boolean retVal = keyService.create(clientPublicKey, hash, pubKey, privKey).element0();
        Assert.assertTrue(retVal);
        retVal = keyService.create(clientPublicKey, hash, pubKey, privKey).element0();
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
    
}
