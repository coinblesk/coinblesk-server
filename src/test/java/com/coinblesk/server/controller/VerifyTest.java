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
import com.coinblesk.json.VerifyTO;
import com.coinblesk.server.utilTest.Client;
import com.coinblesk.server.utilTest.ServerCalls;
import com.coinblesk.util.BitcoinUtils;
import com.coinblesk.util.SerializeUtils;
import com.coinblesk.util.Triple;
import com.github.springtestdbunit.DbUnitTestExecutionListener;
import java.util.Date;
import java.util.List;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.crypto.TransactionSignature;
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
public class VerifyTest {

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

    private Client client;
    private Client merchant;
    
    private Transaction funding;


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
    }

    @After
    public void tearDown() {
        client.deleteWallet();
        merchant.deleteWallet();
    }

    @Test
    public void testVerify() throws Exception {
        Transaction txClient = BitcoinUtils.createTx(
                params,  client.outpoints(funding), client.redeemScript(), client.p2shAddress(), merchant.p2shAddress(),
                9876);
        
        SignTO status = ServerCalls.signServerCall(mockMvc, client.outpointsRaw(funding),
                merchant.p2shAddress(), 9876, client, new Date());
        Assert.assertTrue(status.isSuccess());
        
        List<TransactionSignature> clientSigs = BitcoinUtils.partiallySign(txClient, client.redeemScript(), client.ecKey());
        
        Transaction txVerification = BitcoinUtils.createTx(params, client.outpoints(funding), client.redeemScript(),
                    client.p2shAddress(), merchant.p2shAddress(), 9876);
        Assert.assertTrue(SerializeUtils.verifyTxSignatures(txVerification, clientSigs, client.redeemScript(), client.ecKey()));
        
        VerifyTO verify = ServerCalls.verifyServerCall(mockMvc, client.outpointsRaw(funding), 
                merchant.p2shAddress(), 9876, client, SerializeUtils.serializeSignatures(clientSigs),
                status.serverSignatures(), new Date());
        Assert.assertTrue(verify.isSuccess());
        
        verify = ServerCalls.verifyServerCall(mockMvc, client.outpointsRaw(funding), 
                merchant.p2shAddress(), 9876, client, SerializeUtils.serializeSignatures(clientSigs),
                status.serverSignatures(), new Date());
        Assert.assertTrue(verify.isSuccess());        
    }
    
    @Test
    public void testVerifyDoubleSpending() throws Exception {
        Transaction txClient1 = BitcoinUtils.createTx(
                params,  client.outpoints(funding), client.redeemScript(), client.p2shAddress(), merchant.p2shAddress(),
                9876);
        Address unknown = new ECKey().toAddress(params);
        
        SignTO status1 = ServerCalls.signServerCall(mockMvc, client.outpointsRaw(funding),
                merchant.p2shAddress(), 9876, client, new Date());
        Assert.assertTrue(status1.isSuccess());
        SignTO status2 = ServerCalls.signServerCall(mockMvc, client.outpointsRaw(funding),
                unknown, 9876, client, new Date());
        Assert.assertTrue(status2.isSuccess());
        
        List<TransactionSignature> clientSigs1 = BitcoinUtils.partiallySign(txClient1, client.redeemScript(), client.ecKey());
        
        VerifyTO verify = ServerCalls.verifyServerCall(mockMvc, client.outpointsRaw(funding), 
                merchant.p2shAddress(), 9876, client, SerializeUtils.serializeSignatures(clientSigs1),
                status1.serverSignatures(), new Date());
        Assert.assertTrue(verify.isSuccess());
        Assert.assertEquals(Type.SUCCESS_BUT_NO_INSTANT_PAYMENT, verify.type());
    }
    
    @Test
    public void testVerifyDoubleSpending2() throws Exception {
        Transaction txClient1 = BitcoinUtils.createTx(
                params,  client.outpoints(funding), client.redeemScript(), client.p2shAddress(), merchant.p2shAddress(),
                9876);
        Address unknown = new ECKey().toAddress(params);
        Transaction txClient2 = BitcoinUtils.createTx(
                params,  client.outpoints(funding), client.redeemScript(), client.p2shAddress(), unknown,
                9876);
        
        Assert.assertFalse(txClient1.getHash().equals(txClient2.getHash()));
        
        SignTO status1 = ServerCalls.signServerCall(mockMvc, client.outpointsRaw(funding),
                merchant.p2shAddress(), 9876, client, new Date());
        Assert.assertTrue(status1.isSuccess());
        
        
        List<TransactionSignature> clientSigs1 = BitcoinUtils.partiallySign(txClient1, client.redeemScript(), client.ecKey());
        List<TransactionSignature> clientSigs2 = BitcoinUtils.partiallySign(txClient2, client.redeemScript(), client.ecKey());
        
        VerifyTO verify = ServerCalls.verifyServerCall(mockMvc, client.outpointsRaw(funding), 
                merchant.p2shAddress(), 9876, client, SerializeUtils.serializeSignatures(clientSigs1),
                status1.serverSignatures(), new Date());
        Assert.assertTrue(verify.isSuccess());
        Assert.assertEquals(Type.SUCCESS, verify.type());
        
        SignTO status2 = ServerCalls.signServerCall(mockMvc, client.outpointsRaw(funding),
                unknown, 9876, client, new Date());
        Assert.assertTrue(status2.isSuccess());
        
        verify = ServerCalls.verifyServerCall(mockMvc, client.outpointsRaw(funding), 
                unknown, 9876, client, SerializeUtils.serializeSignatures(clientSigs2),
                status2.serverSignatures(), new Date());
        Assert.assertTrue(verify.isSuccess());        
        Assert.assertEquals(Type.SUCCESS_BUT_NO_INSTANT_PAYMENT, verify.type());
    }
    
    @Test
    public void testVerifyExpiredRefund() throws Exception {
        Transaction txClient1 = BitcoinUtils.createTx(
                params,  client.outpoints(funding), client.redeemScript(), client.p2shAddress(), merchant.p2shAddress(),
                9876);
        
        SignTO status1 = ServerCalls.signServerCall(mockMvc, client.outpointsRaw(funding),
                merchant.p2shAddress(), 9876, client, new Date());
        Assert.assertTrue(status1.isSuccess());
        
        Triple<RefundTO,Transaction,List<TransactionSignature>> t =
        RefundTest.refundServerCall(params, mockMvc, client, funding, new Date(), (System.currentTimeMillis() / 1000) - 10);
        Assert.assertTrue(t.element0().isSuccess());
        
        List<TransactionSignature> clientSigs1 = BitcoinUtils.partiallySign(txClient1, client.redeemScript(), client.ecKey());
        
        VerifyTO verify = ServerCalls.verifyServerCall(mockMvc, client.outpointsRaw(funding), 
                merchant.p2shAddress(), 9876, client, SerializeUtils.serializeSignatures(clientSigs1),
                status1.serverSignatures(), new Date());
        Assert.assertTrue(verify.isSuccess());
        Assert.assertEquals(Type.SUCCESS_BUT_NO_INSTANT_PAYMENT, verify.type());
    }
    
    @Test
    public void testVerifyNonExpiredRefund() throws Exception {
        Transaction txClient1 = BitcoinUtils.createTx(
                params,  client.outpoints(funding), client.redeemScript(), client.p2shAddress(), merchant.p2shAddress(),
                9876);
        
        SignTO status1 = ServerCalls.signServerCall(mockMvc, client.outpointsRaw(funding),
                merchant.p2shAddress(), 9876, client, new Date());
        Assert.assertTrue(status1.isSuccess());
        
        Triple<RefundTO,Transaction,List<TransactionSignature>> t =
        RefundTest.refundServerCall(params, mockMvc, client, funding, new Date(), ((System.currentTimeMillis() / 1000) + (24 * 60 * 60)));
        Assert.assertTrue(t.element0().isSuccess());
        
        List<TransactionSignature> clientSigs1 = BitcoinUtils.partiallySign(txClient1, client.redeemScript(), client.ecKey());
        
        VerifyTO verify = ServerCalls.verifyServerCall(mockMvc, client.outpointsRaw(funding), 
                merchant.p2shAddress(), 9876, client, SerializeUtils.serializeSignatures(clientSigs1),
                status1.serverSignatures(), new Date());
        Assert.assertTrue(verify.isSuccess());
        Assert.assertEquals(Type.SUCCESS, verify.type());
    }
}