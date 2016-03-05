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
import com.coinblesk.json.CompleteSignTO;
import com.coinblesk.json.PrepareHalfSignTO;
import com.coinblesk.json.RefundP2shTO;
import com.coinblesk.json.Type;
import com.coinblesk.util.BitcoinUtils;
import com.coinblesk.util.Pair;
import com.coinblesk.util.SerializeUtils;
import com.github.springtestdbunit.DbUnitTestExecutionListener;
import com.google.common.io.Files;
import java.io.File;
import java.io.FilenameFilter;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.bitcoinj.core.BloomFilter;
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
public class CompleteSignTest {

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
    private WalletAppKit clientAppKit;

    private final static String TEST_WALLET_PREFIX = "testwallet";
    private File tmpDir;

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

        tmpDir = Files.createTempDir();
        clientAppKit = new WalletAppKit(params, tmpDir, TEST_WALLET_PREFIX);
        clientAppKit.setDiscovery(new PeerDiscovery() {
            @Override
            public void shutdown() {
            }

            @Override
            public InetSocketAddress[] getPeers(long timeoutValue, TimeUnit timeoutUnit) throws PeerDiscoveryException {
                return new InetSocketAddress[0];
            }
        });
        clientAppKit.setBlockingStartup(false);
        clientAppKit.startAsync().awaitRunning();
        clientAppKit.wallet().addWatchedAddress(client.ecKey().toAddress(params));

        funding = PrepareTest.sendFakeCoins(params,
                Coin.valueOf(123450), client.p2shAddress(), walletService.blockChain(), clientAppKit.chain());
        Date now = new Date();
        status = PrepareTest.prepareServerCall(mockMvc, amountToRequest, client, merchant.p2shAddress(), null,
                now);
        lockTime = walletService.refundLockTime();

    }

    @After
    public void tearDown() {
        File[] walletFiles = tmpDir.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.startsWith(TEST_WALLET_PREFIX);
            }

        });
        for (File f : walletFiles) {
            f.delete();
        }
        tmpDir.delete();
    }

    @Test
    public void testComplete() throws Exception {
        List<TransactionSignature> serverSigs = SerializeUtils.deserializeSignatures(status.signatures());
        Transaction txClient = BitcoinUtils.createTx(
                params, funding.getOutputs(), client.p2shAddress(), merchant.p2shAddress(),
                amountToRequest.value);
        RefundInput refundInput = createInputForRefund(
                params, client, merchant.p2shAddress(), serverSigs, lockTime, txClient);
        RefundP2shTO statusRefund1 = GenericEndpointTest.refundServerCall(mockMvc,
                client.ecKey(), refundInput.clientOutpoint(), refundInput.clientSinatures(), new Date());
        Assert.assertTrue(statusRefund1.isSuccess());
        //do signing
        Transaction fullTx = refundInput.fullTx();
        CompleteSignTO status3 = GenericEndpointTest.completeSignServerCall(mockMvc, client.ecKey(), merchant.p2shAddress(), fullTx, new Date());
        Assert.assertTrue(status3.isSuccess());
        Assert.assertEquals(Type.SUCCESS, status3.type());
    }
    
    @Test
    public void testCompleteNoInstant() throws Exception {
        PrepareTest.prepareServerCall(mockMvc, amountToRequest, client, merchant.p2shAddress(), null, new Date());
        List<TransactionSignature> serverSigs = SerializeUtils.deserializeSignatures(status.signatures());
        Transaction txClient = BitcoinUtils.createTx(
                params, funding.getOutputs(), client.p2shAddress(), merchant.p2shAddress(),
                amountToRequest.value);
        RefundInput refundInput = createInputForRefund(
                params, client, merchant.p2shAddress(), serverSigs, lockTime, txClient);
        RefundP2shTO statusRefund1 = GenericEndpointTest.refundServerCall(mockMvc,
                client.ecKey(), refundInput.clientOutpoint(), refundInput.clientSinatures(), new Date());
        Assert.assertTrue(statusRefund1.isSuccess());
        //do signing
        Transaction fullTx = refundInput.fullTx();
        CompleteSignTO status3 = GenericEndpointTest.completeSignServerCall(mockMvc, client.ecKey(), merchant.p2shAddress(), fullTx, new Date());
        Assert.assertTrue(status3.isSuccess());
        Assert.assertEquals(Type.NO_INSTANT_PAYMENT, status3.type());
    }
    
    @Test
    public void testCompleteSpentOutputs() throws Exception {
        List<TransactionSignature> serverSigs = SerializeUtils.deserializeSignatures(status.signatures());
        Transaction txClient = BitcoinUtils.createTx(
                params, funding.getOutputs(), client.p2shAddress(), merchant.p2shAddress(),
                amountToRequest.value);
        RefundInput refundInput = createInputForRefund(
                params, client, merchant.p2shAddress(), serverSigs, lockTime, txClient);
        RefundP2shTO statusRefund1 = GenericEndpointTest.refundServerCall(mockMvc,
                client.ecKey(), refundInput.clientOutpoint(), refundInput.clientSinatures(), new Date());
        Assert.assertTrue(statusRefund1.isSuccess());
        //do signing
        Transaction fullTx = refundInput.fullTx();
        //spend outputs
        Transaction tx = new Transaction(params);
        tx.addInput(funding.getOutput(0));
        tx.addOutput(Coin.valueOf(10000), merchant.p2shAddress());
        IntegrationTest.sendFakeBroadcast(tx, walletService.blockChain(), clientAppKit.chain());
        CompleteSignTO status3 = GenericEndpointTest.completeSignServerCall(mockMvc, client.ecKey(), merchant.p2shAddress(), fullTx, new Date());
        Assert.assertFalse(status3.isSuccess());
        Assert.assertEquals(Type.INVALID_TX, status3.type());
    }
    
    @Test
    public void testCompleteInvalidRefund() throws Exception {
        List<TransactionSignature> serverSigs = SerializeUtils.deserializeSignatures(status.signatures());
        Transaction txClient = BitcoinUtils.createTx(
                params, funding.getOutputs(), client.p2shAddress(), merchant.p2shAddress(),
                amountToRequest.value);
        RefundInput refundInput = createInputForRefund(
                params, client, merchant.p2shAddress(), serverSigs, lockTime, txClient);
        RefundP2shTO statusRefund1 = GenericEndpointTest.refundServerCall(mockMvc,
                client.ecKey(), refundInput.clientOutpoint(), refundInput.clientSinatures(), new Date());
        Assert.assertTrue(statusRefund1.isSuccess());
        //do signing
        Transaction fullTx = refundInput.fullTx();
        //spend outputs
        ECKey sig = new ECKey();
        for(int i=0;i<8;i++) {
            //create 8 blocks
            PrepareTest.sendFakeCoins(params, amountToRequest, 
                sig.toAddress(params), walletService.blockChain(), clientAppKit.chain());
            
        }
        CompleteSignTO status3 = GenericEndpointTest.completeSignServerCall(mockMvc, client.ecKey(), merchant.p2shAddress(), fullTx, new Date());
        Assert.assertFalse(status3.isSuccess());
        Assert.assertEquals(Type.INVALID_LOCKTIME, status3.type());
    }
            
    @Test
    public void testCompleteTwice() throws Exception {
        List<TransactionSignature> serverSigs = SerializeUtils.deserializeSignatures(status.signatures());
        Transaction txClient = BitcoinUtils.createTx(
                params, funding.getOutputs(), client.p2shAddress(), merchant.p2shAddress(),
                amountToRequest.value);
        RefundInput refundInput = createInputForRefund(
                params, client, merchant.p2shAddress(), serverSigs, lockTime, txClient);
        RefundP2shTO statusRefund1 = GenericEndpointTest.refundServerCall(mockMvc,
                client.ecKey(), refundInput.clientOutpoint(), refundInput.clientSinatures(), new Date());
        Assert.assertTrue(statusRefund1.isSuccess());
        //do signing
        Transaction fullTx = refundInput.fullTx();
        CompleteSignTO status3 = GenericEndpointTest.completeSignServerCall(mockMvc, client.ecKey(), merchant.p2shAddress(), fullTx, new Date());
        Assert.assertTrue(status3.isSuccess());
        Assert.assertEquals(Type.SUCCESS, status3.type());
        
        ///************** go again instant
        PrepareHalfSignTO statusTwice = PrepareTest.prepareServerCall(
                mockMvc, amountToRequest, client, merchant.p2shAddress(), null, new Date());
        
        Transaction txClientTwice = BitcoinUtils.createTx(
                params, fullTx.getOutputs(), client.p2shAddress(), merchant.p2shAddress(),
                amountToRequest.value);
        
        RefundInput refundInputTwice = createInputForRefund(
                params, client, merchant.p2shAddress(), serverSigs, lockTime, txClientTwice);
        RefundP2shTO statusRefundTwice = GenericEndpointTest.refundServerCall(mockMvc,
                client.ecKey(), refundInputTwice.clientOutpoint(), refundInputTwice.clientSinatures(), new Date());
        Assert.assertTrue(statusRefundTwice.isSuccess());
        Transaction fullTxTwice = refundInput.fullTx();
        CompleteSignTO completStatusTwice = GenericEndpointTest.completeSignServerCall(mockMvc, client.ecKey(), merchant.p2shAddress(), fullTxTwice, new Date());
        Assert.assertTrue(completStatusTwice.isSuccess());
        Assert.assertEquals(Type.SUCCESS, completStatusTwice.type());
        //TODO: check database
    }
}
