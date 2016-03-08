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
import com.coinblesk.json.BalanceTO;
import com.coinblesk.json.CompleteSignTO;
import com.coinblesk.json.KeyTO;
import com.coinblesk.json.PrepareHalfSignTO;
import com.coinblesk.json.RefundP2shTO;
import com.coinblesk.json.RefundTO;
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
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Block;
import org.bitcoinj.core.BlockChain;
import org.bitcoinj.core.BloomFilter;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.PrunedException;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutPoint;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.core.VerificationException;
import org.bitcoinj.crypto.TransactionSignature;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import org.springframework.web.client.RestTemplate;

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
public class IntegrationTest {

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

    @Before
    public void setUp() throws Exception {
        walletService.shutdown();
        mockMvc = MockMvcBuilders.webAppContextSetup(webAppContext).addFilter(springSecurityFilterChain).build();
        walletService.init();
    }

    @Test
    public void testTopup() throws Exception {
        //register with good pubilc key
        ECKey ecKeyClient = new ECKey();

        KeyTO keyTO = new KeyTO().publicKey(ecKeyClient.getPubKey());
        MvcResult res = mockMvc.perform(post("/p/x").secure(true).contentType(MediaType.APPLICATION_JSON).content(SerializeUtils.GSON.toJson(keyTO))).andExpect(status().isOk()).andReturn();
        KeyTO status = SerializeUtils.GSON.fromJson(res.getResponse().getContentAsString(), KeyTO.class);
        Assert.assertEquals(true, status.isSuccess());
        Assert.assertNotNull(status.publicKey());

        res = mockMvc.perform(get("/p/b").secure(true).contentType(MediaType.APPLICATION_JSON).content(SerializeUtils.GSON.toJson(keyTO))).andExpect(status().isOk()).andReturn();
        BalanceTO balance = SerializeUtils.GSON.fromJson(res.getResponse().getContentAsString(), BalanceTO.class);
        Assert.assertEquals(0, balance.balance());

        //
        final List<ECKey> keys = new ArrayList<>();
        keys.add(ecKeyClient);
        keys.add(ECKey.fromPublicOnly(status.publicKey()));
        final Script script = ScriptBuilder.createP2SHOutputScript(2, keys);
        sendFakeCoins(Coin.MICROCOIN, script.getToAddress(appConfig.getNetworkParameters()));

        res = mockMvc.perform(get("/p/b").secure(true).contentType(MediaType.APPLICATION_JSON).content(SerializeUtils.GSON.toJson(keyTO))).andExpect(status().isOk()).andReturn();
        balance = SerializeUtils.GSON.fromJson(res.getResponse().getContentAsString(), BalanceTO.class);
        Assert.assertEquals(Coin.MICROCOIN.value, balance.balance());

        //pay to multisig address in unittest
    }

    @Test
    public void testRefund() throws Exception {
        //register with good pubilc key
        ECKey ecKeyClient = new ECKey();
        KeyTO keyTO = new KeyTO().publicKey(ecKeyClient.getPubKey());
        MvcResult res = mockMvc.perform(post("/p/x").secure(true).contentType(MediaType.APPLICATION_JSON).content(SerializeUtils.GSON.toJson(keyTO))).andExpect(status().isOk()).andReturn();
        KeyTO status = SerializeUtils.GSON.fromJson(res.getResponse().getContentAsString(), KeyTO.class);
        Assert.assertEquals(true, status.isSuccess());
        Assert.assertNotNull(status.publicKey());

        //create funding/toput tx
        final List<ECKey> keys = new ArrayList<>();
        keys.add(ecKeyClient);
        keys.add(ECKey.fromPublicOnly(status.publicKey()));
        final Script redeemScript = ScriptBuilder.createP2SHOutputScript(2, keys);
        Transaction tx = FakeTxBuilder.createFakeTx(appConfig.getNetworkParameters(), Coin.COIN, redeemScript.getToAddress(appConfig.getNetworkParameters()));
        Assert.assertTrue(tx.getOutputs().get(0).getScriptPubKey().isPayToScriptHash());
        Assert.assertTrue(tx.getOutputs().get(1).getScriptPubKey().isSentToAddress());
        //create refund tx based on the topup
        ECKey refundAddress = new ECKey();
        Transaction refund = BitcoinUtils.generateUnsignedRefundTx(
                appConfig.getNetworkParameters(), tx.getOutputs(), null,
                refundAddress.toAddress(appConfig.getNetworkParameters()), redeemScript, walletService.refundLockTime());
        List<TransactionSignature> tss = BitcoinUtils.partiallySign(refund, redeemScript, ecKeyClient);
        RefundTO rto = new RefundTO();
        rto.clientPublicKey(ecKeyClient.getPubKey());
        rto.refundTransaction(refund.unsafeBitcoinSerialize());
        rto.clientSignatures(SerializeUtils.serializeSignatures(tss));
        res = mockMvc.perform(post("/p/r").secure(true).contentType(MediaType.APPLICATION_JSON).content(SerializeUtils.GSON.toJson(rto))).andExpect(status().isOk()).andReturn();
        RefundTO refundRet = SerializeUtils.GSON.fromJson(res.getResponse().getContentAsString(), RefundTO.class);
        Assert.assertTrue(refundRet.isSuccess());
        Transaction fullRefund = new Transaction(appConfig.getNetworkParameters(), refundRet.refundTransaction());
        //todo test tx
    }

    @Test
    public void testSameRefundTx() throws Exception {
        final Transaction tx1 = new Transaction(appConfig.getNetworkParameters());
        final long unixTime1 = System.currentTimeMillis() / 1000L;
        final long lockTime1 = unixTime1 + (LOCK_TIME_MONTHS * UNIX_TIME_MONTH);
        tx1.setLockTime(lockTime1);

        Thread.sleep(1100);

        final Transaction tx2 = new Transaction(appConfig.getNetworkParameters());
        final long unixTime2 = System.currentTimeMillis() / 1000L;
        final long lockTime2 = unixTime2 + (LOCK_TIME_MONTHS * UNIX_TIME_MONTH);
        tx2.setLockTime(lockTime2);

        Assert.assertFalse(tx1.equals(tx2));
    }

    @Test
    public void testSameRefundTx2() throws Exception {
        final Transaction tx1 = new Transaction(appConfig.getNetworkParameters());
        final long unixTime1 = ((System.currentTimeMillis() / 1000L) / (10 * 60)) * (10 * 60);
        final long lockTime1 = unixTime1 + (LOCK_TIME_MONTHS * UNIX_TIME_MONTH);
        tx1.setLockTime(lockTime1);

        Thread.sleep(1100);

        final Transaction tx2 = new Transaction(appConfig.getNetworkParameters());
        final long unixTime2 = ((System.currentTimeMillis() / 1000L) / (10 * 60)) * (10 * 60);
        final long lockTime2 = unixTime2 + (LOCK_TIME_MONTHS * UNIX_TIME_MONTH);
        tx2.setLockTime(lockTime2);

        Assert.assertTrue(tx1.equals(tx2));
    }

    @Test
    public void testOldTime() throws Exception {
        Client client = new Client(appConfig.getNetworkParameters(), mockMvc);
        sendFakeCoins(Coin.valueOf(123450), client.p2shAddress());
        Coin amountToRequest = Coin.valueOf(9876);
        Date now = new Date(1);
        //
        PrepareHalfSignTO statusPrepare = prepareServerCall(amountToRequest, client, new ECKey().toAddress(appConfig.getNetworkParameters()), null, now);
        Assert.assertFalse(statusPrepare.isSuccess());
        Assert.assertEquals(Type.TIME_MISMATCH, statusPrepare.type());
        //
        RefundP2shTO statusRefund = refundServerCall(client, Collections.emptyList(), Collections.emptyList(), now);
        Assert.assertFalse(statusRefund.isSuccess());
        Assert.assertEquals(Type.TIME_MISMATCH, statusRefund.type());
        //
        CompleteSignTO statusSign = completeSignServerCall(client, client.p2shAddress(), new Transaction(appConfig.getNetworkParameters()), now);
        Assert.assertFalse(statusSign.isSuccess());
        Assert.assertEquals(Type.TIME_MISMATCH, statusSign.type());
        
    }
    
    @Test
    public void testNewTime() throws Exception {
        Client client = new Client(appConfig.getNetworkParameters(), mockMvc);
        sendFakeCoins(Coin.valueOf(123450), client.p2shAddress());
        Coin amountToRequest = Coin.valueOf(9876);
        Date now = new Date(Long.MAX_VALUE/2);
        //
        PrepareHalfSignTO status = prepareServerCall(amountToRequest, client, new ECKey().toAddress(appConfig.getNetworkParameters()), null, now);
        Assert.assertFalse(status.isSuccess());
        Assert.assertEquals(Type.TIME_MISMATCH, status.type());
        //
        RefundP2shTO statusRefund = refundServerCall(client, Collections.emptyList(), Collections.emptyList(), now);
        Assert.assertFalse(statusRefund.isSuccess());
        Assert.assertEquals(Type.TIME_MISMATCH, statusRefund.type());
        //
        CompleteSignTO statusSign = completeSignServerCall(client, client.p2shAddress(), new Transaction(appConfig.getNetworkParameters()), now);
        Assert.assertFalse(statusSign.isSuccess());
        Assert.assertEquals(Type.TIME_MISMATCH, statusSign.type());
    }
    
    @Test
    public void testMissingInputCoin() throws Exception {
        Client client = new Client(appConfig.getNetworkParameters(), mockMvc);
        sendFakeCoins(Coin.valueOf(123450), client.p2shAddress());
        Date now = new Date();
        PrepareHalfSignTO status = prepareServerCall(Coin.valueOf(0), client, new ECKey().toAddress(appConfig.getNetworkParameters()), null, now);
        Assert.assertFalse(status.isSuccess());
        Assert.assertEquals(Type.INPUT_MISMATCH, status.type());
    }
    
    @Test
    public void testMissingInputDate() throws Exception {
        Client client = new Client(appConfig.getNetworkParameters(), mockMvc);
        sendFakeCoins(Coin.valueOf(123450), client.p2shAddress());
        Coin amountToRequest = Coin.valueOf(9876);
        PrepareHalfSignTO status = prepareServerCall(amountToRequest, client, new ECKey().toAddress(appConfig.getNetworkParameters()), null, null);
        Assert.assertFalse(status.isSuccess());
        Assert.assertEquals(Type.INPUT_MISMATCH, status.type());
    }
    
    @Test
    public void testWrongSignature() throws Exception {
        Client client = new Client(appConfig.getNetworkParameters(), mockMvc);
        sendFakeCoins(Coin.valueOf(123450), client.p2shAddress());
        Coin amountToRequest = Coin.valueOf(9876);
        Date now = new Date();
        PrepareHalfSignTO prepareHalfSignTO = prepareServerCallInput(amountToRequest, client.ecKey(), new ECKey().toAddress(appConfig.getNetworkParameters()), null, now);
        SerializeUtils.sign(prepareHalfSignTO, new ECKey());
        PrepareHalfSignTO status = prepareServerCallOutput(prepareHalfSignTO);
        Assert.assertFalse(status.isSuccess());
        Assert.assertEquals(Type.JSON_SIGNATURE_ERROR, status.type());
    }
    
    @Test
    @DatabaseTearDown(value={"classpath:DbUnitFiles/emptyDB.xml"}, type = DatabaseOperation.DELETE_ALL)
    public void testCache() throws Exception {
        Client client = new Client(appConfig.getNetworkParameters(), mockMvc);
        sendFakeCoins(Coin.valueOf(123450), client.p2shAddress());
        Coin amountToRequest = Coin.valueOf(9876);
        Date now = new Date();
        PrepareHalfSignTO status1 = prepareServerCall(amountToRequest, client, new ECKey().toAddress(appConfig.getNetworkParameters()), null, now);
        Assert.assertTrue(status1.isSuccess());
        
        PrepareHalfSignTO status2 = prepareServerCall(amountToRequest, client, new ECKey().toAddress(appConfig.getNetworkParameters()), null, now);
        Assert.assertTrue(status2.isSuccess());
        
        Assert.assertEquals(SerializeUtils.GSON.toJson(status1), SerializeUtils.GSON.toJson(status2));
    }
    
    @Test
    public void testNotRegistered() throws Exception {
        Client client = new Client(appConfig.getNetworkParameters(), mockMvc);
        sendFakeCoins(Coin.valueOf(123450), client.p2shAddress());
        Coin amountToRequest = Coin.valueOf(9876);
        Date now = new Date();
        
        ECKey key = new ECKey();
        PrepareHalfSignTO prepareHalfSignTO = prepareServerCallInput(amountToRequest, key, new ECKey().toAddress(appConfig.getNetworkParameters()), null, now);
        SerializeUtils.sign(prepareHalfSignTO, key);
        PrepareHalfSignTO status = prepareServerCallOutput(prepareHalfSignTO);
        
        Assert.assertFalse(status.isSuccess());
        Assert.assertEquals(Type.KEYS_NOT_FOUND, status.type());
    }
    
    @Test
    public void testAddressEmpty() throws Exception {
        Client client = new Client(appConfig.getNetworkParameters(), mockMvc);
        sendFakeCoins(Coin.valueOf(123450), client.p2shAddress());
        Coin amountToRequest = Coin.valueOf(9876);
        Date now = new Date();
        PrepareHalfSignTO prepareHalfSignTO = prepareServerCallInput(amountToRequest, client.ecKey(), new ECKey().toAddress(appConfig.getNetworkParameters()), null, now);
        prepareHalfSignTO.p2shAddressTo("1");
        SerializeUtils.sign(prepareHalfSignTO, client.ecKey());
        PrepareHalfSignTO status = prepareServerCallOutput(prepareHalfSignTO);
        Assert.assertFalse(status.isSuccess());
        Assert.assertEquals(Type.ADDRESS_EMPTY, status.type());
    }
    
    @Test
    public void testAddressNotEnoughFunds() throws Exception {
        Client client = new Client(appConfig.getNetworkParameters(), mockMvc);
        sendFakeCoins(Coin.valueOf(1), client.p2shAddress());
        Coin amountToRequest = Coin.valueOf(9876);
        Date now = new Date();
        PrepareHalfSignTO status = prepareServerCall(amountToRequest, client, new ECKey().toAddress(appConfig.getNetworkParameters()), null, now);
        Assert.assertFalse(status.isSuccess());
        Assert.assertEquals(Type.NOT_ENOUGH_COINS, status.type());
    }
    
    @Test
    public void testAddressOnlyDust() throws Exception {
        Client client = new Client(appConfig.getNetworkParameters(), mockMvc);
        sendFakeCoins(Coin.valueOf(700), client.p2shAddress());
        Coin amountToRequest = Coin.valueOf(100);
        Date now = new Date();
        PrepareHalfSignTO status = prepareServerCall(amountToRequest, client, new ECKey().toAddress(appConfig.getNetworkParameters()), null, now);
        Assert.assertFalse(status.isSuccess());
        Assert.assertEquals(Type.NOT_ENOUGH_COINS, status.type());
    }
    
    @Test
    @DatabaseTearDown(value={"classpath:DbUnitFiles/emptyDB.xml"}, type = DatabaseOperation.DELETE_ALL)
    @ExpectedDatabase(value = "classpath:DbUnitFiles/burnedOutputs.xml", assertionMode = DatabaseAssertionMode.NON_STRICT_UNORDERED)
    public void testBurnOutputsTwice() throws Exception {
        Client client = new Client(appConfig.getNetworkParameters(), mockMvc);
        sendFakeCoins(Coin.valueOf(123450), client.p2shAddress());
        Date now = new Date();
        PrepareHalfSignTO status = prepareServerCall(Coin.valueOf(9876), client, new ECKey().toAddress(appConfig.getNetworkParameters()), null, now);
        Assert.assertTrue(status.isSuccess());
        Date now2 = new Date(now.getTime() + 5000L);
        status = prepareServerCall(Coin.valueOf(9876), client, new ECKey().toAddress(appConfig.getNetworkParameters()), null, now2);
        Assert.assertTrue(status.isSuccess());
    }
    
    @Test
    @DatabaseTearDown(value={"classpath:DbUnitFiles/emptyDB.xml"}, type = DatabaseOperation.DELETE_ALL)
    public void testServerSignatures() throws Exception {
        Client client = new Client(appConfig.getNetworkParameters(), mockMvc);
        sendFakeCoins(Coin.valueOf(123450), client.p2shAddress());
        Date now = new Date();
        PrepareHalfSignTO status = prepareServerCall(Coin.valueOf(9876), client, new ECKey().toAddress(appConfig.getNetworkParameters()), null, now);
        Assert.assertTrue(status.isSuccess());
        Transaction tx = new Transaction(appConfig.getNetworkParameters(), status.unsignedTransaction());
        List<TransactionSignature> sigs = SerializeUtils.deserializeSignatures(status.signatures());
        Assert.assertTrue(SerializeUtils.verifyTxSignatures(tx, sigs, client.redeemScript(), client.ecKeyServer()));
        Assert.assertFalse(SerializeUtils.verifyTxSignatures(tx, sigs, client.redeemScript(), client.ecKey()));
    }
    
    @Test
    @DatabaseTearDown(value={"classpath:DbUnitFiles/emptyDB.xml"}, type = DatabaseOperation.DELETE_ALL)
    public void testBloomfilterFiltered() throws Exception {
        Client client = new Client(appConfig.getNetworkParameters(), mockMvc);
        Transaction t1 = sendFakeCoins(Coin.valueOf(123450), client.p2shAddress());
        sendFakeCoins(Coin.valueOf(234560), client.p2shAddress());
        Date now = new Date();
        PrepareHalfSignTO prepareHalfSignTO = prepareServerCallInput(Coin.valueOf(9876), client.ecKey(), new ECKey().toAddress(appConfig.getNetworkParameters()), null, now);
        BloomFilter bf = new BloomFilter(2, 0.001, 42L);
        bf.insert(t1.getOutput(0).unsafeBitcoinSerialize());
        prepareHalfSignTO.bloomFilter(bf.unsafeBitcoinSerialize());
        SerializeUtils.sign(prepareHalfSignTO, client.ecKey());
        PrepareHalfSignTO status = prepareServerCallOutput(prepareHalfSignTO);
        Assert.assertEquals(Type.SUCCESS_FILTERED, status.type());
    }
    
    @Test
    @DatabaseTearDown(value={"classpath:DbUnitFiles/emptyDB.xml"}, type = DatabaseOperation.DELETE_ALL)
    public void testBloomfilterFilteredAll() throws Exception {
        Client client = new Client(appConfig.getNetworkParameters(), mockMvc);
        Date now = new Date();
        PrepareHalfSignTO prepareHalfSignTO = prepareServerCallInput(Coin.valueOf(9876), client.ecKey(), new ECKey().toAddress(appConfig.getNetworkParameters()), null, now);
        BloomFilter bf = new BloomFilter(2, 0.001, 42L);
        prepareHalfSignTO.bloomFilter(bf.unsafeBitcoinSerialize());
        SerializeUtils.sign(prepareHalfSignTO, client.ecKey());
        PrepareHalfSignTO status = prepareServerCallOutput(prepareHalfSignTO);
        Assert.assertEquals(Type.NOT_ENOUGH_COINS, status.type());
    }
    
    @Test
    @DatabaseTearDown(value={"classpath:DbUnitFiles/emptyDB.xml"}, type = DatabaseOperation.DELETE_ALL)
    public void testBloomfilterNotFiltered() throws Exception {
        Client client = new Client(appConfig.getNetworkParameters(), mockMvc);
        Transaction t1 = sendFakeCoins(Coin.valueOf(123450), client.p2shAddress());
        Transaction t2 = sendFakeCoins(Coin.valueOf(234560), client.p2shAddress());
        Date now = new Date();
        PrepareHalfSignTO prepareHalfSignTO = prepareServerCallInput(Coin.valueOf(9876), client.ecKey(), new ECKey().toAddress(appConfig.getNetworkParameters()), null, now);
        BloomFilter bf = new BloomFilter(2, 0.001, 42L);
        bf.insert(t1.getOutput(0).unsafeBitcoinSerialize());
        bf.insert(t2.getOutput(0).unsafeBitcoinSerialize());
        prepareHalfSignTO.bloomFilter(bf.unsafeBitcoinSerialize());
        SerializeUtils.sign(prepareHalfSignTO, client.ecKey());
        PrepareHalfSignTO status = prepareServerCallOutput(prepareHalfSignTO);
        Assert.assertEquals(Type.SUCCESS, status.type());
    }
    
    @Test
    public void testReal() throws IOException, Exception {
        final String uri = "http://bitcoin2-test.csg.uzh.ch/coinblesk-server/p/x";
        ECKey client = getKey("client");
        //2NDHTmZ9LvZ8kMtTXrNaM4WfH96pimZo3UU
        ECKey merchant = getKey("merchant");
        
        //register client
        KeyTO keyTO = new KeyTO().publicKey(client.getPubKey());
        RestTemplate restTemplate = new RestTemplate();
        KeyTO result = restTemplate.postForObject(uri, keyTO, KeyTO.class);
        ECKey serverPub = ECKey.fromPublicOnly(result.publicKey());
        Client clientFull = new Client(appConfig.getNetworkParameters(), client, serverPub);
        //register merchant
        keyTO = new KeyTO().publicKey(merchant.getPubKey());
        restTemplate = new RestTemplate();
        result = restTemplate.postForObject(uri, keyTO, KeyTO.class);
        serverPub = ECKey.fromPublicOnly(result.publicKey());
        Client merchantFull = new Client(appConfig.getNetworkParameters(), merchant, serverPub);
        
        System.out.println("client p2sh: "+clientFull.p2shAddress());
        System.out.println("merchant p2sh: "+merchantFull.p2shAddress());
    }
    
    private ECKey getKey(String name) throws IOException {
        Path pathPub = Paths.get("/tmp/"+name+"_key.pub");
        Path pathPriv = Paths.get("/tmp/"+name+"_key.priv");
        final ECKey ecKey;
        if(pathPub.toFile().exists() && pathPriv.toFile().exists()) {
            byte[] pub = Files.readAllBytes(pathPub);
            byte[] priv = Files.readAllBytes(pathPriv);
            ecKey = ECKey.fromPrivateAndPrecalculatedPublic(priv, pub);
        } else {
            ecKey = new ECKey();
            Files.write(pathPub, ecKey.getPubKey());
            Files.write(pathPriv, ecKey.getPrivKeyBytes());
        }
        return ecKey;
    }

    @Test
    @DatabaseTearDown(value={"classpath:DbUnitFiles/emptyDB.xml"}, type = DatabaseOperation.DELETE_ALL)
    public void testRequestBTC() throws Exception {
        //***********Merchant/Client******* Setup
        Client merchant = new Client(appConfig.getNetworkParameters(), mockMvc);
        Client client = new Client(appConfig.getNetworkParameters(), mockMvc);
        //**************************

        //preload client
        Transaction funding = sendFakeCoins(Coin.valueOf(123450), client.p2shAddress());
        //*************************

        //***********Merchant******* 
        //Merchant requests 9876s to address p2shAddressMerchant, sends to Client
        Coin amountToRequest = Coin.valueOf(9876);
        //*************************

        //**********Client**********
        Date now = new Date();
        PrepareHalfSignTO createSig = new PrepareHalfSignTO()
                .amountToSpend(amountToRequest.value)
                .clientPublicKey(client.ecKey().getPubKey())
                .p2shAddressTo(merchant.p2shAddress().toString())
                .currentDate(now.getTime());
        SerializeUtils.sign(createSig, client.ecKey());
        TxSig clientSig = createSig.messageSig();
        //Client sends ok, publickey (and client signs/encrypts it) + amount/p2shAddressMerchant
        
        //*************************
        //TODO: signatures or encryption
        //***********Merchant******* 
        PrepareHalfSignTO status = prepareServerCall(amountToRequest, client, merchant.p2shAddress(), clientSig, now);
        //Merchant get back a half signed transaction from Server
        Transaction txServer = new Transaction(appConfig.getNetworkParameters(), status.unsignedTransaction());
        List<TransactionSignature> serverSigs = SerializeUtils.deserializeSignatures(status.signatures());
        //Merchant only gives the serverSigs to the Client
        //*************************

        //**********Client**********
        //Client rebuilds the tx and adds the signatures from the Server, making it a full transaction
        List<TransactionOutput> clientWalletOutputs = funding.getOutputs();
        Transaction txClient = BitcoinUtils.createTx(appConfig.getNetworkParameters(),
                clientWalletOutputs, client.p2shAddress(), merchant.p2shAddress(),
                amountToRequest.value);
        List<TransactionSignature> clientSigs = BitcoinUtils.partiallySign(txClient, client.redeemScript(), client.ecKey());
        BitcoinUtils.applySignatures(txClient, client.redeemScript(), clientSigs, serverSigs, client.clientFirst());
        //Client now has the full tx, based on that, Client creates the refund tx
        //Client uses the outputs of the tx as all the outputs are in that tx, no
        //need to merge
        List<TransactionOutput> clientMergedOutputs = BitcoinUtils.myOutputs(
                appConfig.getNetworkParameters(), txClient.getOutputs(), client.p2shAddress());
        
        Transaction unsignedRefundTx = BitcoinUtils.generateUnsignedRefundTx(
                appConfig.getNetworkParameters(), clientMergedOutputs, null,
                client.ecKey().toAddress(appConfig.getNetworkParameters()), client.redeemScript(), walletService.refundLockTime());
        if (unsignedRefundTx == null) {
            throw new RuntimeException("not enough funds");
        }
        //The refund is currently only signed by the Client (half)
        List<TransactionSignature> partiallySignedRefundClient = BitcoinUtils.partiallySign(
                unsignedRefundTx, client.redeemScript(), client.ecKey());
        List<Pair<TransactionOutPoint, Coin>> refundClientOutpoints = BitcoinUtils.outpointsFromInput(unsignedRefundTx);
        //The client also needs to create the outpoits for the refund for the merchant, as the client
        //is the only one that knows that full tx at the moment
        //but this is only! for the output of the current tx
        List<Pair<TransactionOutPoint, Coin>> refundMerchantOutpoints = BitcoinUtils.outpointsFromOutputFor(appConfig.getNetworkParameters(), txClient, merchant.p2shAddress());
        //The Client sends the refund signatures and the transaction outpoints (client, merchant) to the Merchant
        //*************************

        //***********Merchant******* 
        //The Merchant forwards it to the server, adds its refund signature as well, and the half signed transaction from previous (state)
        RefundP2shTO statusClient = refundServerCall(client, refundClientOutpoints, partiallySignedRefundClient, new Date());
        Assert.assertTrue(statusClient.isSuccess());
        //now the merchant has the full refund for the client

        //This goes from the Client to the Merhchant, which recreates the refund tx and adds its sigs
        //first get the inputs
        List<TransactionInput> preBuiltInupts = BitcoinUtils.convertPointsToInputs(
                appConfig.getNetworkParameters(), refundMerchantOutpoints, merchant.redeemScript());
        List<TransactionOutput> merchantWalletOutputs = walletService.getOutputs(
                appConfig.getNetworkParameters(), merchant.p2shAddress());
        //add/remove pending, approved, remove burned
        Transaction unsignedRefundMerchant = BitcoinUtils.generateUnsignedRefundTx(
                appConfig.getNetworkParameters(), merchantWalletOutputs, preBuiltInupts,
                merchant.ecKey().toAddress(appConfig.getNetworkParameters()), merchant.redeemScript(), walletService.refundLockTime());
        if (unsignedRefundMerchant == null) {
            throw new RuntimeException("not enough funds");
        }
        List<TransactionSignature> partiallySignedRefundMerchant = BitcoinUtils.partiallySign(
                unsignedRefundMerchant, merchant.redeemScript(), merchant.ecKey());
        RefundP2shTO statusMerchant = refundServerCall(merchant, refundMerchantOutpoints, partiallySignedRefundMerchant, new Date());
        Assert.assertTrue(statusMerchant.isSuccess());
        //Server sends back the full refund tx and including the sigs, for convenincie, the sigs are in the json as well
        //Server sends sigs to Merchant, Merchant has now the refund as well
        Transaction refundMerchant = new Transaction(appConfig.getNetworkParameters(), statusMerchant.fullRefundTransaction());
        Assert.assertNotNull(refundMerchant);
        Assert.assertEquals(1, refundMerchant.getInputs().size());
        Assert.assertEquals(1, refundMerchant.getOutputs().size());
        //now, Merchant to Client, only the  sigs
        //*************************

        //**********Client**********
        //Now, Client applies the signatures and gets the complete Refund TX
        List<TransactionSignature> refundServerSigs = SerializeUtils.deserializeSignatures(statusClient.refundSignaturesServer());
        BitcoinUtils.applySignatures(unsignedRefundTx,
                client.redeemScript(), partiallySignedRefundClient, refundServerSigs, client.clientFirst());
        Transaction refundClient = unsignedRefundTx;
        //client does not have this, just for testing:
        Transaction refundServer = new Transaction(appConfig.getNetworkParameters(), statusClient.fullRefundTransaction());
        //test both refund tx, as built by the Client and by the Server
        System.err.println("rclient:" + refundClient);
        System.err.println("rserver:" + refundServer);
        Assert.assertEquals(refundClient, refundServer);
        Assert.assertEquals(1, refundClient.getInputs().size());
        Assert.assertEquals(1, refundClient.getOutputs().size());
        //Client is now to send the sigs in order that the server can make a full tx. 
        //In fact also the Merchant has the information to build a full tx. The Merchant sends the full tx to the server
        //*************************

        //***********Merchant******* 
        BitcoinUtils.applySignatures(txServer, client.redeemScript(), clientSigs, serverSigs, client.clientFirst());
        Transaction fullTxMerchant = txServer;
        //server call
        CompleteSignTO status3 = completeSignServerCall(client, merchant.p2shAddress(), fullTxMerchant, new Date());

        //success!
        Assert.assertTrue(status3.isSuccess());
        //Server or Merchant can broadcast the full tx
        Assert.assertEquals(fullTxMerchant, txClient);
        sendFakeBroadcast(fullTxMerchant, walletService.blockChain());
        //*************************

        //*****CHECKS********
        Assert.assertEquals(108574, balanceServerCall(client)); //check balance on Client
        Assert.assertEquals(9876, balanceServerCall(merchant)); //check balance on Merchant
    }

    final protected static char[] hexArray = "0123456789ABCDEF".toCharArray();

    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }

    private long balanceServerCall(Client client) throws Exception {
        KeyTO keyTO = new KeyTO().publicKey(client.ecKey().getPubKey());
        MvcResult res = mockMvc.perform(get("/p/b").secure(true).contentType(MediaType.APPLICATION_JSON).content(SerializeUtils.GSON.toJson(keyTO))).andExpect(status().isOk()).andReturn();
        BalanceTO balance = SerializeUtils.GSON.fromJson(res.getResponse().getContentAsString(), BalanceTO.class);
        return balance.balance();
    }

    private CompleteSignTO completeSignServerCall(
            Client client, Address p2shAddressTo, Transaction fullTx, Date now) throws UnsupportedEncodingException, Exception {
        CompleteSignTO cs = new CompleteSignTO()
                .clientPublicKey(client.ecKey().getPubKey())
                .p2shAddressTo(p2shAddressTo.toString())
                .fullSignedTransaction(fullTx.unsafeBitcoinSerialize())
                .currentDate(now.getTime());
        if(cs.messageSig() == null) {
            SerializeUtils.sign(cs, client.ecKey());
        }
        MvcResult res = mockMvc.perform(post("/p/s").secure(true).
                contentType(MediaType.APPLICATION_JSON).content(SerializeUtils.GSON.toJson(cs))).andExpect(status().isOk()).andReturn();
        return SerializeUtils.GSON.fromJson(res.getResponse().getContentAsString(), CompleteSignTO.class);
    }

    private RefundP2shTO refundServerCall(Client client, List<Pair<TransactionOutPoint, Coin>> refundClientOutpoint,
            List<TransactionSignature> partiallySignedRefundClient, Date date) throws Exception {
        RefundP2shTO refundP2shTO = new RefundP2shTO();
        refundP2shTO.clientPublicKey(client.ecKey().getPubKey());
        refundP2shTO.refundClientOutpointsCoinPair(SerializeUtils.serializeOutPointsCoin(refundClientOutpoint));
        refundP2shTO.refundSignaturesClient(SerializeUtils.serializeSignatures(partiallySignedRefundClient));
        refundP2shTO.currentDate(date.getTime());
        if(refundP2shTO.messageSig() == null) {
            SerializeUtils.sign(refundP2shTO, client.ecKey());
        }
        MvcResult res = mockMvc.perform(post("/p/f").secure(true).
                contentType(MediaType.APPLICATION_JSON).content(SerializeUtils.GSON.toJson(refundP2shTO))).andExpect(status().isOk()).andReturn();
        return SerializeUtils.GSON.fromJson(res.getResponse().getContentAsString(), RefundP2shTO.class);
    }

    private PrepareHalfSignTO prepareServerCall(Coin amountToRequest, Client client, Address to, TxSig clientSig, Date date) throws Exception {
        PrepareHalfSignTO prepareHalfSignTO = prepareServerCallInput(amountToRequest, client.ecKey(), to, clientSig, date);
        return prepareServerCallOutput(prepareHalfSignTO);
    }
    
     private PrepareHalfSignTO prepareServerCallOutput(PrepareHalfSignTO prepareHalfSignTO) throws Exception {
         MvcResult res = mockMvc.perform(post("/p/p").secure(true).
                contentType(MediaType.APPLICATION_JSON).content(SerializeUtils.GSON.toJson(prepareHalfSignTO))).andExpect(status().isOk()).andReturn();
        return SerializeUtils.GSON.fromJson(res.getResponse().getContentAsString(), PrepareHalfSignTO.class);
     }
    
    private PrepareHalfSignTO prepareServerCallInput(Coin amountToRequest, ECKey client, Address to, TxSig clientSig, Date date) throws Exception { 
        PrepareHalfSignTO prepareHalfSignTO = new PrepareHalfSignTO()
                .amountToSpend(amountToRequest.longValue())
                .clientPublicKey(client.getPubKey())
                .p2shAddressTo(to.toString())
                .messageSig(clientSig)
                .currentDate(date == null?0:date.getTime());
        if(prepareHalfSignTO.messageSig() == null) {
            SerializeUtils.sign(prepareHalfSignTO, client);
        }
        return prepareHalfSignTO;
    }
    
    
    

    private ECKey registerServerCall(ECKey ecKeyClient) throws Exception {
        KeyTO keyTO = new KeyTO().publicKey(ecKeyClient.getPubKey());
        MvcResult res = mockMvc.perform(post("/p/x").secure(true).
                contentType(MediaType.APPLICATION_JSON).content(SerializeUtils.GSON.toJson(keyTO))).andExpect(status().isOk()).andReturn();
        KeyTO status = SerializeUtils.GSON.fromJson(res.getResponse().getContentAsString(), KeyTO.class);
        return ECKey.fromPublicOnly(status.publicKey());
    }

    private Script createP2SHScript(ECKey ecKeyClient, ECKey ecKeyServer) {
        final List<ECKey> keys = new ArrayList<>();
        keys.add(ecKeyClient);
        keys.add(ecKeyServer);
        final Script redeemScript = ScriptBuilder.createP2SHOutputScript(2, keys);
        return redeemScript;
    }

    private Script createRedeemScript(ECKey ecKeyClient, ECKey ecKeyServer) {
        final List<ECKey> keys = new ArrayList<>();
        keys.add(ecKeyClient);
        keys.add(ecKeyServer);
        final Script redeemScript = ScriptBuilder.createRedeemScript(2, keys);
        return redeemScript;
    }

    static Transaction sendFakeBroadcast(Transaction tx, BlockChain... chains) throws BlockStoreException, VerificationException, PrunedException, InterruptedException {
        for(BlockChain chain:chains) {
            Block block = FakeTxBuilder.makeSolvedTestBlock(chain.getBlockStore().getChainHead().getHeader(), tx);
            chain.add(block);
        }
        Thread.sleep(250);
        return tx;
    }

    private Transaction sendFakeCoins(Coin amount, Address to) throws VerificationException, PrunedException, BlockStoreException, InterruptedException {
        Transaction tx = FakeTxBuilder.createFakeTx(appConfig.getNetworkParameters(), amount, to);
        BlockChain chain = walletService.blockChain();
        Block block = FakeTxBuilder.makeSolvedTestBlock(chain.getBlockStore().getChainHead().getHeader(), tx);
        chain.add(block);
        Thread.sleep(250);
        return tx;
    }

    
}
