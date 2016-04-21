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
package com.coinblesk.server.controller;

import com.coinblesk.json.RefundTO;
import com.coinblesk.json.SignTO;
import com.coinblesk.server.config.AppConfig;
import com.coinblesk.server.config.BeanConfig;
import com.coinblesk.server.config.SecurityConfig;
import com.coinblesk.server.service.WalletService;
import com.coinblesk.server.utilTest.TestBean;
import com.coinblesk.json.Type;
import com.coinblesk.server.utilTest.Client;
import com.coinblesk.server.utilTest.ServerCalls;
import com.coinblesk.util.BitcoinUtils;
import com.coinblesk.util.Pair;
import com.coinblesk.util.SerializeUtils;
import com.coinblesk.util.SimpleBloomFilter;
import com.coinblesk.util.Triple;
import com.github.springtestdbunit.DbUnitTestExecutionListener;
import com.google.common.io.Files;
import java.io.File;
import java.io.FilenameFilter;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutPoint;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.crypto.TransactionSignature;
import org.bitcoinj.kits.WalletAppKit;
import org.bitcoinj.net.discovery.PeerDiscovery;
import org.bitcoinj.net.discovery.PeerDiscoveryException;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;
import org.springframework.test.context.transaction.TransactionalTestExecutionListener;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

//http://www.soroushjp.com/2014/12/20/bitcoin-multisig-the-hard-way-understanding-raw-multisignature-bitcoin-transactions/
/**
 *
 * @author Thomas Bocek
 * @author Raphael Voellmy
 */
@RunWith(SpringJUnit4ClassRunner.class)
@TestExecutionListeners(
            {DependencyInjectionTestExecutionListener.class, TransactionalTestExecutionListener.class,
                DbUnitTestExecutionListener.class})
@ContextConfiguration(classes = {TestBean.class, BeanConfig.class, SecurityConfig.class})
@WebAppConfiguration
public class RefundTest {

    @Autowired
    private WebApplicationContext webAppContext;

    @Autowired
    private FilterChainProxy springSecurityFilterChain;

    @Autowired
    private AppConfig appConfig;

    @Autowired
    private WalletService walletService;

    private static MockMvc mockMvc;

    private NetworkParameters params;
    
    private SignTO status;
    private Client client;
    private Client merchant;
    //private Coin amountToRequest = Coin.valueOf(9876);
    private Transaction funding;
    final private static long LOCK_TIME = System.currentTimeMillis() + (1000 * 60 * 10 * 3);

    @Before
    public void setUp() throws Exception {
        walletService.shutdown();
        mockMvc = MockMvcBuilders
                .webAppContextSetup(webAppContext)
                .addFilter(springSecurityFilterChain)
                .build();
        walletService.init();
        client = new Client(appConfig.getNetworkParameters(), mockMvc);
        merchant = new Client(appConfig.getNetworkParameters(), mockMvc);
        params = appConfig.getNetworkParameters();

        funding = Client.sendFakeCoins(params, Coin.valueOf(123450), client.p2shAddress(), 100,
                walletService.blockChain(), client.blockChain(), merchant.blockChain());
        
        status = ServerCalls.signServerCall(mockMvc, client.outpointsRaw(funding),
                new ECKey().toAddress(params), 9876, client, new Date());
    }

    @After
    public void tearDown() {
        client.deleteWallet();
        merchant.deleteWallet();
    }

    
    @Test
    public void testSignatureVerification() throws Exception {
        
        Transaction txClient = BitcoinUtils.createTx(
                params,  client.outpoints(funding), client.redeemScript(), client.p2shAddress(), merchant.p2shAddress(),
                9876);
        
        Triple<RefundTO,Transaction,List<TransactionSignature>> t = 
                refundServerCall(params, mockMvc, client, txClient, new Date(), LOCK_TIME);
        Assert.assertTrue(t.element0().isSuccess());
        
        List<TransactionSignature> serverSigs = SerializeUtils.deserializeSignatures(t.element0().serverSignatures());
        Assert.assertTrue(SerializeUtils.verifyTxSignatures(t.element1(), serverSigs, client.redeemScript(), client.ecKeyServer()));
        Assert.assertTrue(SerializeUtils.verifyTxSignatures(t.element1(), t.element2(), client.redeemScript(), client.ecKey()));
    }

    
    @Test
    public void testRefund() throws Exception {
        //List<TransactionSignature> serverSigs = SerializeUtils.deserializeSignatures(status.serverSignatures());
        Transaction txClient = BitcoinUtils.createTx(
                params,  client.outpoints(funding), client.redeemScript(), client.p2shAddress(), merchant.p2shAddress(),
                9876);
        
        Triple<RefundTO,Transaction,List<TransactionSignature>> t = 
                refundServerCall(params, mockMvc, client, txClient, new Date(), LOCK_TIME);
        Assert.assertTrue(t.element0().isSuccess());
        
        Transaction refund = t.element1();
        Assert.assertEquals(LOCK_TIME, refund.getLockTime());
        //in unit test we can't wait for locktime, as block includes all tx
        Client.sendFakeBroadcast(params, refund, 200, walletService.blockChain(), client.blockChain(), merchant.blockChain());
        Assert.assertEquals(234004, client.wallet().getBalance().value);
        //we have not yet sent out the real tx
        Assert.assertEquals(123450, walletService.balance(params, client.p2shAddress()));
        //we already spent the inputs
        
        Client.sendFakeBroadcast(params, txClient, 200, walletService.blockChain(), client.blockChain(), merchant.blockChain());
        Assert.assertEquals(222485, client.wallet().getBalance().value);
    }
    
    @Test
    public void testRefund2() throws Exception {
        List<TransactionSignature> serverSigs = SerializeUtils.deserializeSignatures(status.serverSignatures());
        Transaction txClient = BitcoinUtils.createTx(
                params,  client.outpoints(funding), client.redeemScript(), client.p2shAddress(), merchant.p2shAddress(),
                9876);
        List<TransactionSignature> clientSigs = BitcoinUtils.partiallySign(txClient, client.redeemScript(), client.ecKey());
        BitcoinUtils.applySignatures(txClient, client.redeemScript(), clientSigs, serverSigs, true);
        
        Triple<RefundTO,Transaction,List<TransactionSignature>> t = 
                refundServerCall(params, mockMvc, client, txClient, new Date(), LOCK_TIME);
        Assert.assertTrue(t.element0().isSuccess());
        
        Transaction refund = t.element1();
        Assert.assertEquals(LOCK_TIME, refund.getLockTime());
        //in unit test we can't wait for locktime, as block includes all tx
        Client.sendFakeBroadcast(params, txClient, 200, walletService.blockChain(), client.blockChain(), merchant.blockChain());
        Assert.assertEquals(111904, client.wallet().getBalance().value);
        Assert.assertEquals(111904, walletService.balance(params, client.p2shAddress()));
        
        Client.sendFakeBroadcast(params, refund, 200, walletService.blockChain(), client.blockChain(), merchant.blockChain());
        Assert.assertEquals(110554, client.wallet().getBalance().value);
        
        Assert.assertTrue(SerializeUtils.verifyTxSignatures(refund, t.element2(), client.redeemScript(), client.ecKey()));
        Assert.assertTrue(SerializeUtils.verifyTxSignatures(refund, 
                SerializeUtils.deserializeSignatures(t.element0().serverSignatures()), client.redeemScript(), client.ecKeyServer()));
        
    }

    public static Triple<RefundTO,Transaction,List<TransactionSignature>> refundServerCall(NetworkParameters params, MockMvc mockMvc, Client client,
            Transaction txClient, Date date, long LOCK_TIME) throws Exception {
        final Transaction txRefund = BitcoinUtils.createRefundTx(params, client.outpoints(txClient), client.redeemScript(),
                client.ecKey().toAddress(params), LOCK_TIME);
        final List<TransactionSignature> clientSigs = BitcoinUtils.partiallySign(txRefund, client.redeemScript(), client.ecKey());
        RefundTO refundTO = ServerCalls.refundServerCall(params, mockMvc, client.ecKey(), client.outpoints(txClient),
                clientSigs, date, LOCK_TIME);
        return new Triple<>(refundTO, txRefund, clientSigs);
    }
}