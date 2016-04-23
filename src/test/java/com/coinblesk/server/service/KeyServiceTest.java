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

import com.coinblesk.server.config.BeanConfig;
import com.coinblesk.server.entity.Keys;
import com.github.springtestdbunit.DbUnitTestExecutionListener;
import java.util.List;
import org.bitcoinj.core.ECKey;
import org.junit.Assert;
import org.junit.BeforeClass;
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
@TestExecutionListeners(
            {DependencyInjectionTestExecutionListener.class, TransactionalTestExecutionListener.class,
                DbUnitTestExecutionListener.class})
@WebAppConfiguration
@ContextConfiguration(classes = {BeanConfig.class})
public class KeyServiceTest {
    
    @BeforeClass
    public static void beforeClass() {
        System.setProperty("coinblesk.config.dir", "/tmp/lib/coinblesk");
    }

    @Autowired
    private KeyService keyService;

    @Test
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

}
