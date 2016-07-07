/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.coinblesk.server.service;

import com.coinblesk.bitcoin.TimeLockedAddress;
import com.coinblesk.json.v1.UserAccountTO;
import com.coinblesk.server.config.AppConfig;
import com.coinblesk.server.config.BeanConfig;
import com.coinblesk.server.entity.Keys;
import com.coinblesk.server.entity.UserAccount;
import com.coinblesk.server.utilTest.FakeTxBuilder;
import com.github.springtestdbunit.DbUnitTestExecutionListener;
import com.github.springtestdbunit.annotation.DatabaseOperation;
import com.github.springtestdbunit.annotation.DatabaseTearDown;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import org.bitcoinj.core.Block;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.PrunedException;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.VerificationException;
import org.bitcoinj.params.UnitTestParams;
import org.bitcoinj.store.BlockStoreException;
import org.bitcoinj.wallet.UnreadableWalletException;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
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
public class UserAccountServiceTest {
    
    @Autowired
    private UserAccountService userAccountService;
    
    @Autowired
    private PasswordEncoder passwordEncoder;
    
    @Autowired
    private TxQueueService txQueueService;
    
    @Autowired
    private KeyService keyService;
    
    @Autowired
    private WalletService walletService;
    
    @Autowired
    private AppConfig cfg;
    
    final private ECKey ecKeyClient = new ECKey();
    final private ECKey ecKeyServer = new ECKey();
    
    private int counter = 0;
    
    @Before
    public void before() throws IOException, UnreadableWalletException, BlockStoreException {
        System.setProperty("coinblesk.config.dir", "/tmp/lib/coinblesk" + (counter++));
        if(counter > 0) {
            walletService.init();
        }
        
        UserAccount userAccount = new UserAccount();
        userAccount.setBalance(BigDecimal.ONE)
                .setCreationDate(new Date(1))
                .setDeleted(false)
                .setEmail("test@test.test")
                .setEmailToken(null)
                .setPassword(passwordEncoder.encode("test"))
                .setUsername("blib");
        userAccountService.save(userAccount);
        
        Keys keys = keyService.storeKeysAndAddress(ecKeyClient.getPubKey(), ecKeyServer.getPubKey(),
                ecKeyServer.getPrivKeyBytes()).element1();
        
        TimeLockedAddress address = new TimeLockedAddress(ecKeyClient.getPubKey(), ecKeyServer.getPubKey(), 123456);
        
        keyService.storeTimeLockedAddress(keys, address);
    }
    
    @After
    public void after() {
        walletService.shutdown();
    }
    
    @Test
    @DatabaseTearDown(value = {"EmptyUser.xml"}, type = DatabaseOperation.DELETE_ALL)
    public void testTransferFailed() {
        UserAccountTO result = userAccountService.transferP2SH(ecKeyClient, "test@test.test");
        Assert.assertFalse(result.isSuccess());
    }
    
    @Test
    @DatabaseTearDown(value = {"EmptyUser.xml"}, type = DatabaseOperation.DELETE_ALL)
    public void testTransferSuccess() throws BlockStoreException, VerificationException, PrunedException {
        Block block = FakeTxBuilder.makeSolvedTestBlock(walletService.blockChain().getBlockStore(), cfg.getPotPrivateKeyAddress().toAddress(cfg.getNetworkParameters()));
        walletService.blockChain().add(block);
        UserAccountTO result = userAccountService.transferP2SH(ecKeyClient, "test@test.test");
        Assert.assertTrue(result.isSuccess());
        List<Transaction> list = txQueueService.all(UnitTestParams.get());
        Assert.assertEquals(1, list.size());
    }
}
