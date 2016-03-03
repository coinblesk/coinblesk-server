/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.uzh.csg.coinblesk.server.controller;

import ch.uzh.csg.coinblesk.server.config.AppConfig;
import ch.uzh.csg.coinblesk.server.config.BeanConfig;
import ch.uzh.csg.coinblesk.server.config.SecurityConfig;
import static ch.uzh.csg.coinblesk.server.controller.GenericEndpointTest.createInputForRefund;
import static ch.uzh.csg.coinblesk.server.controller.IntegrationTest.sendFakeBroadcast;
import ch.uzh.csg.coinblesk.server.service.WalletService;
import ch.uzh.csg.coinblesk.server.utilTest.TestBean;
import com.coinblesk.json.PrepareHalfSignTO;
import com.coinblesk.json.RefundP2shTO;
import com.coinblesk.json.TxSig;
import com.coinblesk.json.Type;
import com.coinblesk.util.BitcoinUtils;
import com.coinblesk.util.Pair;
import com.coinblesk.util.SerializeUtils;
import com.github.springtestdbunit.DbUnitTestExecutionListener;
import com.github.springtestdbunit.annotation.DatabaseOperation;
import com.github.springtestdbunit.annotation.DatabaseTearDown;
import com.github.springtestdbunit.annotation.ExpectedDatabase;
import com.github.springtestdbunit.assertion.DatabaseAssertionMode;
import java.util.Date;
import java.util.List;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.Block;
import org.bitcoinj.core.BlockChain;
import org.bitcoinj.core.BloomFilter;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.PrunedException;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionOutPoint;
import org.bitcoinj.core.VerificationException;
import org.bitcoinj.crypto.TransactionSignature;
import org.bitcoinj.store.BlockStoreException;
import org.bitcoinj.testing.FakeTxBuilder;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;
import org.springframework.test.context.transaction.TransactionalTestExecutionListener;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

//http://www.soroushjp.com/2014/12/20/bitcoin-multisig-the-hard-way-understanding-raw-multisignature-bitcoin-transactions/
/**
 *
 * @author draft
 */
@RunWith(SpringJUnit4ClassRunner.class)
@TestExecutionListeners({DependencyInjectionTestExecutionListener.class, TransactionalTestExecutionListener.class,
    DbUnitTestExecutionListener.class})
@ContextConfiguration(classes = {TestBean.class, BeanConfig.class, SecurityConfig.class})
@WebAppConfiguration
public class RefundTest {

    public final static long UNIX_TIME_MONTH = 60 * 60 * 24 * 30;
    public final static int LOCK_TIME_MONTHS = 3;

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
    
    private PrepareHalfSignTO status;
    private Client client;
    private Client merchant;
    private Coin amountToRequest = Coin.valueOf(9876);
    private Transaction funding;
    private int lockTime;

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
        funding = PrepareTest.sendFakeCoins(params, walletService.blockChain(), 
                Coin.valueOf(123450), client.p2shAddress());
        Date now = new Date();
        status = PrepareTest.prepareServerCall(mockMvc, amountToRequest, client, merchant.p2shAddress(), null, now);
        lockTime = walletService.refundLockTime();
    }

    @Test
    public void testBurnedOutputs() throws Exception {
        List<TransactionSignature> serverSigs = SerializeUtils.deserializeSignatures(status.signatures());
        RefundInput refundInput = createInputForRefund(
                      params, client, merchant.p2shAddress(), amountToRequest, serverSigs, funding.getOutputs(), lockTime);
        List<Pair<TransactionOutPoint, Coin>> burned = BitcoinUtils.outpointsFromOutputFor(params, funding, client.p2shAddress());
        RefundP2shTO statusRefund1 = GenericEndpointTest.refundServerCall(mockMvc, 
                client.ecKey(), burned, refundInput.clientSinatures(), new Date());
        Assert.assertFalse(statusRefund1.isSuccess());
        Assert.assertEquals(Type.BURNED_OUTPUTS, statusRefund1.type());
    }
    
    @Test
    public void testSignatureVerification() throws Exception {
        List<TransactionSignature> serverSigs = SerializeUtils.deserializeSignatures(status.signatures());
         RefundInput refundInput = createInputForRefund(
                      params, client, merchant.p2shAddress(), amountToRequest, serverSigs, funding.getOutputs(), lockTime);
        RefundP2shTO statusRefund1 = GenericEndpointTest.refundServerCall(mockMvc, 
                client.ecKey(), refundInput.clientOutpoint(), serverSigs, new Date());
        Assert.assertFalse(statusRefund1.isSuccess());
        Assert.assertEquals(Type.SIGNATURE_ERROR, statusRefund1.type());
    }
    
    @Test
    public void testRefund() throws Exception {
        List<TransactionSignature> serverSigs = SerializeUtils.deserializeSignatures(status.signatures());
         RefundInput refundInput = createInputForRefund(
                      params, client, merchant.p2shAddress(), amountToRequest, serverSigs, funding.getOutputs(), lockTime);
        RefundP2shTO statusRefund1 = GenericEndpointTest.refundServerCall(mockMvc, 
                client.ecKey(), refundInput.clientOutpoint(), refundInput.clientSinatures(), new Date());
        Assert.assertTrue(statusRefund1.isSuccess());
        Transaction refund = new Transaction(params, statusRefund1.fullRefundTransaction());
        //now we have the refund. we wait two blocks and redeem the refund
        sendFakeBroadcast(walletService.blockChain(), refund);
        PrepareTest.sendFakeCoins(params, walletService.blockChain(), Coin.valueOf(123450), new ECKey().toAddress(params));
        //try to redeem after 1, should not work
        Transaction t1 = sendFakeBroadcast(walletService.blockChain(), refund);
        PrepareTest.sendFakeCoins(params, walletService.blockChain(), Coin.valueOf(123450), new ECKey().toAddress(params));
    }


}
