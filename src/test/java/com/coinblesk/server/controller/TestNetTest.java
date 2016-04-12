/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.coinblesk.server.controller;

import com.coinblesk.server.config.AppConfig;
import com.coinblesk.server.config.BeanConfig;
import com.coinblesk.server.config.SecurityConfig;
import com.coinblesk.server.service.WalletService;
import com.coinblesk.server.utilTest.TestBean;
import com.coinblesk.json.BalanceTO;
import com.coinblesk.json.KeyTO;
import com.coinblesk.json.TxSig;
import com.coinblesk.util.BitcoinUtils;
import com.coinblesk.util.Pair;
import com.coinblesk.util.SerializeUtils;
import com.github.springtestdbunit.DbUnitTestExecutionListener;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Block;
import org.bitcoinj.core.BlockChain;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.PrunedException;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionOutPoint;
import org.bitcoinj.core.VerificationException;
import org.bitcoinj.crypto.TransactionSignature;
import org.bitcoinj.script.Script;
import org.bitcoinj.store.BlockStoreException;
import org.bitcoinj.testing.FakeTxBuilder;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.GsonHttpMessageConverter;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;
import org.springframework.test.context.transaction.TransactionalTestExecutionListener;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.web.context.WebApplicationContext;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import org.springframework.web.client.RestTemplate;

//http://www.soroushjp.com/2014/12/20/bitcoin-multisig-the-hard-way-understanding-raw-multisignature-bitcoin-transactions/
/**
 *
 * @author draft
 */
/*@RunWith(SpringJUnit4ClassRunner.class)
@TestExecutionListeners({DependencyInjectionTestExecutionListener.class, TransactionalTestExecutionListener.class,
    DbUnitTestExecutionListener.class})
@ContextConfiguration(classes = {TestBean.class, BeanConfig.class, SecurityConfig.class})
@WebAppConfiguration
public class TestNetTest {

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
        walletService.init();
    }

    
    
    @Test
    public void testReal() throws IOException, Exception {
        final String uri = "http://bitcoin2-test.csg.uzh.ch/coinblesk-server/p/x";
        ECKey client = getKey("client");
        //2NDHTmZ9LvZ8kMtTXrNaM4WfH96pimZo3UU
        ECKey merchant = getKey("merchant");
        
        //register client
        KeyTO keyTO = new KeyTO().publicKey(client.getPubKey());
        RestTemplate restTemplate = createRest();
        KeyTO result = restTemplate.postForObject(uri, keyTO, KeyTO.class);
        ECKey serverPub = ECKey.fromPublicOnly(result.publicKey());
        Client clientFull = new Client(appConfig.getNetworkParameters(), client, serverPub);
        //register merchant
        keyTO = new KeyTO().publicKey(merchant.getPubKey());
        restTemplate = createRest();
        result = restTemplate.postForObject(uri, keyTO, KeyTO.class);
        serverPub = ECKey.fromPublicOnly(result.publicKey());
        Client merchantFull = new Client(appConfig.getNetworkParameters(), merchant, serverPub);
        
        System.out.println("client p2sh: "+clientFull.p2shAddress());
        System.out.println("merchant p2sh: "+merchantFull.p2shAddress());
    }

    private RestTemplate createRest() {
        RestTemplate restTemplate = new RestTemplate();
        GsonHttpMessageConverter gsonHttpMessageConverter = new GsonHttpMessageConverter();
        gsonHttpMessageConverter.setGson(SerializeUtils.GSON);
        List<HttpMessageConverter<?>> msgConvert = new ArrayList<>();
        msgConvert.add(gsonHttpMessageConverter);
        restTemplate.setMessageConverters(msgConvert);
        return restTemplate;
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

    public static long balanceServerCall(MockMvc mockMvc, Client client) throws Exception {
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
        final Script redeemScript = BitcoinUtils.createP2SHOutputScript(2, keys);
        return redeemScript;
    }

    private Script createRedeemScript(ECKey ecKeyClient, ECKey ecKeyServer) {
        final List<ECKey> keys = new ArrayList<>();
        keys.add(ecKeyClient);
        keys.add(ecKeyServer);
        final Script redeemScript = BitcoinUtils.createRedeemScript(2, keys);
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
*/