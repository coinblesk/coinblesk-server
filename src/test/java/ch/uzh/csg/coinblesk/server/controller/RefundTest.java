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
import com.coinblesk.json.Type;
import com.coinblesk.util.BitcoinUtils;
import com.coinblesk.util.Pair;
import com.coinblesk.util.SerializeUtils;
import com.coinblesk.util.SimpleBloomFilter;
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
    public void testBurnedOutputs() throws Exception {
        List<TransactionSignature> serverSigs = SerializeUtils.deserializeSignatures(status.signatures());
        Transaction txClient = BitcoinUtils.createTx(
                params, funding.getOutputs(), client.p2shAddress(), merchant.p2shAddress(),
                amountToRequest.value);
        RefundInput refundInput = createInputForRefund(
                params, client, merchant.p2shAddress(), serverSigs, lockTime, txClient);
        List<Pair<TransactionOutPoint, Coin>> burned = BitcoinUtils.outpointsFromOutputFor(params, funding,
                client.p2shAddress());
        RefundP2shTO statusRefund1 = GenericEndpointTest.refundServerCall(mockMvc,
                client.ecKey(), burned, refundInput.clientSinatures(), new Date());
        Assert.assertFalse(statusRefund1.isSuccess());
        Assert.assertEquals(Type.BURNED_OUTPUTS, statusRefund1.type());
    }

    @Test
    public void testSignatureVerification() throws Exception {
        List<TransactionSignature> serverSigs = SerializeUtils.deserializeSignatures(status.signatures());
        Transaction txClient = BitcoinUtils.createTx(
                params, funding.getOutputs(), client.p2shAddress(), merchant.p2shAddress(),
                amountToRequest.value);
        RefundInput refundInput = createInputForRefund(
                params, client, merchant.p2shAddress(), serverSigs, lockTime, txClient);
        RefundP2shTO statusRefund1 = GenericEndpointTest.refundServerCall(mockMvc,
                client.ecKey(), refundInput.clientOutpoint(), serverSigs, new Date());
        Assert.assertFalse(statusRefund1.isSuccess());
        Assert.assertEquals(Type.SIGNATURE_ERROR, statusRefund1.type());
    }

    @Test
    public void testRefund() throws Exception {
        List<TransactionSignature> serverSigs = SerializeUtils.deserializeSignatures(status.signatures());
        Transaction txClient = BitcoinUtils.createTx(
                params, funding.getOutputs(), client.p2shAddress(), merchant.p2shAddress(),
                amountToRequest.value);
        RefundInput refundInput = createInputForRefund(
                params, client, merchant.p2shAddress(), serverSigs, lockTime, txClient);
        RefundP2shTO statusRefund1 = GenericEndpointTest.refundServerCall(mockMvc,
                client.ecKey(), refundInput.clientOutpoint(), refundInput.clientSinatures(), new Date());

        Assert.assertTrue(statusRefund1.isSuccess());
        Transaction refund = new Transaction(params, statusRefund1.fullRefundTransaction());
        Assert.assertEquals(12, refund.getLockTime());
        //in unit test we can't wait for locktime, as block includes all tx
        sendFakeBroadcast(refund, walletService.blockChain(), clientAppKit.chain());
        Assert.assertEquals(103574, clientAppKit.wallet().getBalance().value);
        //we have not yet sent out the real tx
        Assert.assertEquals(123450, walletService.balance(params, client.p2shAddress()));
        sendFakeBroadcast(refundInput.fullTx(), walletService.blockChain(), clientAppKit.chain());
        Assert.assertEquals(108574, walletService.balance(params, client.p2shAddress()));

    }

    @Test
    public void testRefundIncreasedLocktime() throws Exception {
        for (int i = 0; i < 6; i++) {
            PrepareTest.sendFakeCoins(params,
                    Coin.valueOf(123450), new ECKey().toAddress(params), walletService.blockChain(),
                    clientAppKit.chain());
        }
        lockTime = walletService.refundLockTime();
        List<TransactionSignature> serverSigs = SerializeUtils.deserializeSignatures(status.signatures());
        Transaction txClient = BitcoinUtils.createTx(
                params, funding.getOutputs(), client.p2shAddress(), merchant.p2shAddress(),
                amountToRequest.value);
        RefundInput refundInput = createInputForRefund(
                params, client, merchant.p2shAddress(), serverSigs, lockTime, txClient);
        RefundP2shTO statusRefund1 = GenericEndpointTest.refundServerCall(mockMvc,
                client.ecKey(), refundInput.clientOutpoint(), refundInput.clientSinatures(), new Date());

        Assert.assertTrue(statusRefund1.isSuccess());
        Transaction refund = new Transaction(params, statusRefund1.fullRefundTransaction());
        Assert.assertEquals(18, refund.getLockTime());
        refund.verify();
        Assert.assertTrue(SerializeUtils.verifyRefund(refund, refundInput.fullTx()));
        //SerializeUtils.verifyTxSignatures(refund, serverSigs, redeemScript, serverPubKey)
    }

    @Test
    public void testRefundTxSigs() throws Exception {
        List<TransactionSignature> serverSigs = SerializeUtils.deserializeSignatures(status.signatures());
        Transaction txClient = BitcoinUtils.createTx(
                params, funding.getOutputs(), client.p2shAddress(), merchant.p2shAddress(),
                amountToRequest.value);
        RefundInput refundInput = createInputForRefund(
                params, client, merchant.p2shAddress(), serverSigs, lockTime, txClient);
        RefundP2shTO statusRefund1 = GenericEndpointTest.refundServerCall(mockMvc,
                client.ecKey(), refundInput.clientOutpoint(), refundInput.clientSinatures(), new Date());
        Assert.assertTrue(statusRefund1.isSuccess());
        List<TransactionSignature> merchantSigs = createMerchantInputForRefund(refundInput.merchantOutpoint());
        RefundP2shTO statusRefund2 = GenericEndpointTest.refundServerCall(mockMvc,
                merchant.ecKey(), refundInput.merchantOutpoint(), merchantSigs, new Date());
        Assert.assertTrue(statusRefund2.isSuccess());
        Transaction fullTx = refundInput.fullTx();
        Transaction refund1 = new Transaction(params, statusRefund1.fullRefundTransaction());
        Transaction refund2 = new Transaction(params, statusRefund2.fullRefundTransaction());
        Assert.assertTrue(SerializeUtils.verifyRefund(refund1, fullTx));
        Assert.assertTrue(SerializeUtils.verifyRefund(refund2, fullTx));
        Transaction unsigned = new Transaction(params, status.unsignedTransaction());
        BitcoinUtils.applySignatures(unsigned, client.redeemScript(), serverSigs, serverSigs, true);
        Assert.assertFalse(SerializeUtils.verifyRefund(refund2, unsigned));
        Assert.assertEquals(12, refund1.getLockTime());
        Assert.assertEquals(12, refund2.getLockTime());
    }

    @Test
    public void testRefundWrongBloomFilter() throws Exception {
        List<TransactionSignature> serverSigs = SerializeUtils.deserializeSignatures(status.signatures());
        Transaction txClient = BitcoinUtils.createTx(
                params, funding.getOutputs(), client.p2shAddress(), merchant.p2shAddress(),
                amountToRequest.value);
        RefundInput refundInput = createInputForRefund(
                params, client, merchant.p2shAddress(), serverSigs, lockTime, txClient);

        //add more funding to have more than 1 input to test bloomfilter
        Transaction t = PrepareTest.sendFakeCoins(params,
                Coin.valueOf(222222), client.p2shAddress(), walletService.blockChain(), clientAppKit.chain());
        
        RefundP2shTO input = GenericEndpointTest.refundServerCallInput(
                client.ecKey(), refundInput.clientOutpoint(), refundInput.clientSinatures(), new Date());

        //empty bloomfilter
        SimpleBloomFilter<byte[]> bf = new SimpleBloomFilter(0.001, 2);
        bf.add(t.getOutput(0).getOutPointFor().unsafeBitcoinSerialize());
        input.bloomFilter(bf.encode());
        SerializeUtils.sign(input, client.ecKey());
        RefundP2shTO output = GenericEndpointTest.refundServerCallOutput(mockMvc, input);
        Assert.assertEquals(Type.SIGNATURE_ERROR, output.type());
    }

    @Test
    public void testRefundCorrectBloomFilter1() throws Exception {
        List<TransactionSignature> serverSigs = SerializeUtils.deserializeSignatures(status.signatures());
        Transaction txClient = BitcoinUtils.createTx(
                params, funding.getOutputs(), client.p2shAddress(), merchant.p2shAddress(),
                amountToRequest.value);
        RefundInput refundInput = createInputForRefund(
                params, client, merchant.p2shAddress(), serverSigs, lockTime, txClient);

        RefundP2shTO input = GenericEndpointTest.refundServerCallInput(
                client.ecKey(), refundInput.clientOutpoint(), refundInput.clientSinatures(), new Date());

        SimpleBloomFilter<byte[]> bf = new SimpleBloomFilter<>(0.001, 2);
        input.bloomFilter(bf.encode());
        SerializeUtils.sign(input, client.ecKey());
        RefundP2shTO output = GenericEndpointTest.refundServerCallOutput(mockMvc, input);
        Assert.assertTrue(output.isSuccess());
    }

    @Test
    public void testRefundTopUpInBetween() throws Exception {
        List<TransactionSignature> serverSigs = SerializeUtils.deserializeSignatures(status.signatures());

        Transaction txClient = BitcoinUtils.createTx(
                params, funding.getOutputs(), client.p2shAddress(), merchant.p2shAddress(),
                amountToRequest.value);

        Transaction t = PrepareTest.sendFakeCoins(params,
                Coin.valueOf(222222), client.p2shAddress(), walletService.blockChain(), clientAppKit.chain());

        RefundInput refundInput = createInputForRefund(
                params, client, merchant.p2shAddress(), serverSigs,
                lockTime, txClient, t.getOutput(0));

        RefundP2shTO input = GenericEndpointTest.refundServerCallInput(
                client.ecKey(), refundInput.clientOutpoint(), refundInput.clientSinatures(), new Date());
        RefundP2shTO output = GenericEndpointTest.refundServerCallOutput(mockMvc, input);
        Assert.assertTrue(output.isSuccess());
    }

    @Test
    public void testRefundCorrectBloomFilter2() throws Exception {
        List<TransactionSignature> serverSigs = SerializeUtils.deserializeSignatures(status.signatures());

        Transaction txClient = BitcoinUtils.createTx(
                params, funding.getOutputs(), client.p2shAddress(), merchant.p2shAddress(),
                amountToRequest.value);

        Transaction t = PrepareTest.sendFakeCoins(params,
                Coin.valueOf(222222), client.p2shAddress(), walletService.blockChain(), clientAppKit.chain());

        RefundInput refundInput = createInputForRefund(
                params, client, merchant.p2shAddress(), serverSigs,
                lockTime, txClient, t.getOutput(0));

        RefundP2shTO input = GenericEndpointTest.refundServerCallInput(
                client.ecKey(), refundInput.clientOutpoint(), refundInput.clientSinatures(), new Date());
        SimpleBloomFilter<byte[]> bf = new SimpleBloomFilter<>(0.001, 1);
        bf.add(t.getOutput(0).getOutPointFor().unsafeBitcoinSerialize());
        input.bloomFilter(bf.encode());
        SerializeUtils.sign(input, client.ecKey());
        RefundP2shTO output = GenericEndpointTest.refundServerCallOutput(mockMvc, input);
        Assert.assertTrue(output.isSuccess());
    }

    @Test
    public void testRefundMissingRedundantOutpoints() throws Exception {
        List<TransactionSignature> serverSigs = SerializeUtils.deserializeSignatures(status.signatures());

        Transaction txClient = BitcoinUtils.createTx(
                params, funding.getOutputs(), client.p2shAddress(), merchant.p2shAddress(),
                amountToRequest.value);

        Transaction t = PrepareTest.sendFakeCoins(params,
                Coin.valueOf(222222), client.p2shAddress(), walletService.blockChain(), clientAppKit.chain());

        RefundInput refundInput = createInputForRefund(
                params, client, merchant.p2shAddress(), serverSigs,
                lockTime, txClient, t.getOutput(0));

        List<Pair<TransactionOutPoint, Coin>> l = new ArrayList<>();
        l.add(new Pair<>(refundInput.fullTx().getOutput(1).getOutPointFor(), Coin.valueOf(108574)));
        //
        RefundP2shTO input = GenericEndpointTest.refundServerCallInput(
                client.ecKey(), l, refundInput.clientSinatures(), new Date());
        //we need bf, otherwise the server will know add them
        RefundP2shTO output = GenericEndpointTest.refundServerCallOutput(mockMvc, input);
        Assert.assertTrue(output.isSuccess());
        Transaction refund = new Transaction(params, output.fullRefundTransaction());
        Assert.assertTrue(SerializeUtils.verifyRefund(refund, refundInput.fullTx(), t));
    }
    
    @Test
    public void testRefundMissingImportantOutpoints() throws Exception {
        List<TransactionSignature> serverSigs = SerializeUtils.deserializeSignatures(status.signatures());

        Transaction txClient = BitcoinUtils.createTx(
                params, funding.getOutputs(), client.p2shAddress(), merchant.p2shAddress(),
                amountToRequest.value);

        Transaction t = PrepareTest.sendFakeCoins(params,
                Coin.valueOf(222222), client.p2shAddress(), walletService.blockChain(), clientAppKit.chain());

        RefundInput refundInput = createInputForRefund(
                params, client, merchant.p2shAddress(), serverSigs,
                lockTime, txClient, t.getOutput(0));

        List<Pair<TransactionOutPoint, Coin>> l = new ArrayList<>();
        l.add(new Pair<>(refundInput.fullTx().getOutput(0).getOutPointFor(), Coin.valueOf(222222)));
        
        RefundP2shTO input = GenericEndpointTest.refundServerCallInput(
                client.ecKey(), l, refundInput.clientSinatures(), new Date());
        //we need bf, otherwise the server will know add them
        RefundP2shTO output = GenericEndpointTest.refundServerCallOutput(mockMvc, input);
        Assert.assertFalse(output.isSuccess());
    }

    private List<TransactionSignature> createMerchantInputForRefund(
            List<Pair<TransactionOutPoint, Coin>> refundMerchantOutpoints) {
        List<TransactionInput> preBuiltInupts = BitcoinUtils.convertPointsToInputs(
                appConfig.getNetworkParameters(), refundMerchantOutpoints, merchant.redeemScript());
        List<TransactionOutput> merchantWalletOutputs = walletService.unspentOutputs(
                appConfig.getNetworkParameters(), merchant.p2shAddress());
        //add/remove pending, approved, remove burned
        Transaction unsignedRefundMerchant = BitcoinUtils.generateUnsignedRefundTx(
                appConfig.getNetworkParameters(), merchantWalletOutputs, preBuiltInupts,
                merchant.ecKey().toAddress(appConfig.getNetworkParameters()), merchant.redeemScript(),
                walletService.refundLockTime());
        if (unsignedRefundMerchant == null) {
            throw new RuntimeException("not enough funds");
        }
        List<TransactionSignature> partiallySignedRefundMerchant = BitcoinUtils.partiallySign(
                unsignedRefundMerchant, merchant.redeemScript(), merchant.ecKey());
        return partiallySignedRefundMerchant;
    }

}
