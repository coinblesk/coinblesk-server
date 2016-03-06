/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.uzh.csg.coinblesk.server.controller;

import ch.uzh.csg.coinblesk.server.config.AppConfig;
import ch.uzh.csg.coinblesk.server.config.BeanConfig;
import ch.uzh.csg.coinblesk.server.config.SecurityConfig;
import ch.uzh.csg.coinblesk.server.service.WalletService;
import ch.uzh.csg.coinblesk.server.utilTest.TestBean;
import com.coinblesk.json.PrepareHalfSignTO;
import com.coinblesk.json.TxSig;
import com.coinblesk.json.Type;
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
@TestExecutionListeners(
        {DependencyInjectionTestExecutionListener.class, TransactionalTestExecutionListener.class,
            DbUnitTestExecutionListener.class})
@ContextConfiguration(
        classes = {TestBean.class, BeanConfig.class, SecurityConfig.class})
@WebAppConfiguration
public class PrepareTest {

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

    @Before
    public void setUp() throws Exception {
        walletService.shutdown();
        mockMvc = MockMvcBuilders.webAppContextSetup(webAppContext)
                .addFilter(springSecurityFilterChain).build();
        walletService.init();
        params = appConfig.getNetworkParameters();
    }

    @Test
    public void testAddressEmpty() throws Exception {
        Client client = new Client(params, mockMvc);
        sendFakeCoins(params, Coin.valueOf(123450), client.p2shAddress(), walletService.blockChain());
        Coin amountToRequest = Coin.valueOf(9876);
        Date now = new Date();
        PrepareHalfSignTO prepareHalfSignTO = prepareServerCallInput(
                amountToRequest, client.ecKey(), new ECKey().toAddress(params), null, now);
        prepareHalfSignTO.p2shAddressTo("1");
        SerializeUtils.sign(prepareHalfSignTO, client.ecKey());
        PrepareHalfSignTO status = prepareServerCallOutput(mockMvc, prepareHalfSignTO);
        Assert.assertFalse(status.isSuccess());
        Assert.assertEquals(Type.ADDRESS_EMPTY, status.type());
    }

    @Test
    public void testAddressNotEnoughFunds() throws Exception {
        Client client = new Client(params, mockMvc);
        sendFakeCoins(params, Coin.valueOf(1), client.p2shAddress(),  walletService.blockChain());
        Coin amountToRequest = Coin.valueOf(9876);
        Date now = new Date();
        PrepareHalfSignTO status = prepareServerCall(
                mockMvc, amountToRequest, client, new ECKey().toAddress(params), null, now);
        Assert.assertFalse(status.isSuccess());
        Assert.assertEquals(Type.NOT_ENOUGH_COINS, status.type());
    }

    @Test
    public void testAddressOnlyDust() throws Exception {
        Client client = new Client(params, mockMvc);
        sendFakeCoins(params, Coin.valueOf(700), client.p2shAddress(), walletService.blockChain());
        Coin amountToRequest = Coin.valueOf(100);
        Date now = new Date();
        PrepareHalfSignTO status = prepareServerCall(
                mockMvc, amountToRequest, client, new ECKey().toAddress(params), null, now);
        Assert.assertFalse(status.isSuccess());
        Assert.assertEquals(Type.NOT_ENOUGH_COINS, status.type());
    }

    @Test
    @DatabaseTearDown(value = {"classpath:DbUnitFiles/emptyDB.xml"}, type = DatabaseOperation.DELETE_ALL)
    @ExpectedDatabase(value = "classpath:DbUnitFiles/burnedOutputs.xml",
            assertionMode = DatabaseAssertionMode.NON_STRICT_UNORDERED)
    public void testBurnOutputsTwice() throws Exception {
        Client client = new Client(params, mockMvc);
        sendFakeCoins(params, Coin.valueOf(123450), client.p2shAddress(), walletService.blockChain());
        Date now = new Date();
        PrepareHalfSignTO status = prepareServerCall(
                mockMvc, Coin.valueOf(9876), client, new ECKey().toAddress(params), null, now);
        Assert.assertTrue(status.isSuccess());
        Date now2 = new Date(now.getTime() + 5000L);
        status = prepareServerCall(
                mockMvc, Coin.valueOf(9876), client, new ECKey().toAddress(params), null, now2);
        Assert.assertTrue(status.isSuccess());
    }
    
    @Test
    @DatabaseTearDown(value = {"classpath:DbUnitFiles/emptyDB.xml"}, type = DatabaseOperation.DELETE_ALL)
    public void testBurnCache() throws Exception {
        Client client = new Client(params, mockMvc);
        sendFakeCoins(params, Coin.valueOf(123450), client.p2shAddress(), walletService.blockChain());
        Date now = new Date();
        PrepareHalfSignTO status = prepareServerCall(
                mockMvc, Coin.valueOf(9876), client, new ECKey().toAddress(params), null, now);
        Assert.assertTrue(status.isSuccess());
        status = prepareServerCall(
                mockMvc, Coin.valueOf(9876657653445L), client, new ECKey().toAddress(params), null, now);
        Assert.assertTrue(status.isSuccess());
    }

    @Test
    @DatabaseTearDown(value = {"classpath:DbUnitFiles/emptyDB.xml"},
            type = DatabaseOperation.DELETE_ALL)
    public void testServerSignatures() throws Exception {
        Client client = new Client(params, mockMvc);
        sendFakeCoins(params, Coin.valueOf(123450),
                client.p2shAddress(), walletService.blockChain());
        Date now = new Date();
        PrepareHalfSignTO status = prepareServerCall(mockMvc, Coin.valueOf(9876), client,
                new ECKey().toAddress(params), null, now);
        Assert.assertTrue(status.isSuccess());
        Transaction tx = new Transaction(params, status.unsignedTransaction());
        List<TransactionSignature> sigs = SerializeUtils.deserializeSignatures(
                status.signatures());
        Assert.assertTrue(SerializeUtils.verifyTxSignatures(tx, sigs,
                client.redeemScript(), client.ecKeyServer()));
        Assert.assertFalse(SerializeUtils.verifyTxSignatures(tx, sigs,
                client.redeemScript(), client.ecKey()));
    }

    @Test
    @DatabaseTearDown(value = {"classpath:DbUnitFiles/emptyDB.xml"}, type = DatabaseOperation.DELETE_ALL)
    public void testBloomfilterFiltered() throws Exception {
        Client client = new Client(params, mockMvc);
        Transaction t1 = sendFakeCoins(
                params, Coin.valueOf(123450), client.p2shAddress(), walletService.blockChain());
        sendFakeCoins(params, Coin.valueOf(234560), client.p2shAddress(), walletService.blockChain());
        Date now = new Date();
        PrepareHalfSignTO prepareHalfSignTO = prepareServerCallInput(
                Coin.valueOf(9876), client.ecKey(), new ECKey().toAddress(params), null, now);
        BloomFilter bf = new BloomFilter(2, 0.001, 42L);
        bf.insert(t1.getOutput(0).unsafeBitcoinSerialize());
        prepareHalfSignTO.bloomFilter(bf.unsafeBitcoinSerialize());
        SerializeUtils.sign(prepareHalfSignTO, client.ecKey());
        PrepareHalfSignTO status = prepareServerCallOutput(mockMvc, prepareHalfSignTO);
        Assert.assertEquals(Type.SUCCESS_FILTERED, status.type());
    }

    @Test
    @DatabaseTearDown(value = {"classpath:DbUnitFiles/emptyDB.xml"}, type = DatabaseOperation.DELETE_ALL)
    public void testBloomfilterFilteredAll() throws Exception {
        Client client = new Client(params, mockMvc);
        Date now = new Date();
        PrepareHalfSignTO prepareHalfSignTO = prepareServerCallInput(
                Coin.valueOf(9876), client.ecKey(), new ECKey().toAddress(params), null, now);
        BloomFilter bf = new BloomFilter(2, 0.001, 42L);
        prepareHalfSignTO.bloomFilter(bf.unsafeBitcoinSerialize());
        SerializeUtils.sign(prepareHalfSignTO, client.ecKey());
        PrepareHalfSignTO status = prepareServerCallOutput(mockMvc, prepareHalfSignTO);
        Assert.assertEquals(Type.NOT_ENOUGH_COINS, status.type());
    }

    @Test
    @DatabaseTearDown(value = {"classpath:DbUnitFiles/emptyDB.xml"},
            type = DatabaseOperation.DELETE_ALL)
    public void testBloomfilterNotFiltered() throws Exception {
        Client client = new Client(params, mockMvc);
        Transaction t1 = sendFakeCoins(
                params, Coin.valueOf(123450), client.p2shAddress(), walletService.blockChain());
        Transaction t2 = sendFakeCoins(
                params, Coin.valueOf(234560), client.p2shAddress(), walletService.blockChain());
        Date now = new Date();
        PrepareHalfSignTO prepareHalfSignTO = prepareServerCallInput(
                Coin.valueOf(9876), client.ecKey(), new ECKey().toAddress(params), null, now);
        BloomFilter bf = new BloomFilter(2, 0.001, 42L);
        bf.insert(t1.getOutput(0).unsafeBitcoinSerialize());
        bf.insert(t2.getOutput(0).unsafeBitcoinSerialize());
        prepareHalfSignTO.bloomFilter(bf.unsafeBitcoinSerialize());
        SerializeUtils.sign(prepareHalfSignTO, client.ecKey());
        PrepareHalfSignTO status = prepareServerCallOutput(mockMvc, prepareHalfSignTO);
        Assert.assertEquals(Type.SUCCESS, status.type());
    }

    static PrepareHalfSignTO prepareServerCall(MockMvc mockMvc, Coin amountToRequest,
            Client client, Address to, TxSig clientSig, Date date) throws Exception {
        PrepareHalfSignTO prepareHalfSignTO = prepareServerCallInput(
                amountToRequest, client.ecKey(), to, clientSig, date);
        return prepareServerCallOutput(mockMvc, prepareHalfSignTO);
    }

    static PrepareHalfSignTO prepareServerCallOutput(
            MockMvc mockMvc, PrepareHalfSignTO prepareHalfSignTO) throws Exception {
        MvcResult res = mockMvc.perform(post("/p/p").secure(true)
                .contentType(MediaType.APPLICATION_JSON)
                .content(SerializeUtils.GSON.toJson(prepareHalfSignTO)))
                .andExpect(status().isOk()).andReturn();
        return SerializeUtils.GSON.fromJson(res.getResponse().getContentAsString(), PrepareHalfSignTO.class);
    }

    static PrepareHalfSignTO prepareServerCallInput(Coin amountToRequest,
            ECKey client, Address to, TxSig clientSig, Date date) throws Exception {
        PrepareHalfSignTO prepareHalfSignTO = new PrepareHalfSignTO()
                .amountToSpend(amountToRequest.longValue())
                .clientPublicKey(client.getPubKey())
                .p2shAddressTo(to.toString())
                .messageSig(clientSig)
                .currentDate(date.getTime());
        if (prepareHalfSignTO.messageSig() == null) {
            SerializeUtils.sign(prepareHalfSignTO, client);
        }
        return prepareHalfSignTO;
    }

    static Transaction sendFakeCoins(NetworkParameters params, Coin amount, Address to, BlockChain... chains) 
            throws VerificationException, PrunedException, BlockStoreException, InterruptedException {
        Transaction tx = FakeTxBuilder.createFakeTx(params, amount, to);
        for(BlockChain chain:chains) {
            Block block = FakeTxBuilder.makeSolvedTestBlock(
                chain.getBlockStore().getChainHead().getHeader(), tx);
            chain.add(block);
        }
        Thread.sleep(250);
        return tx;
    }

}
