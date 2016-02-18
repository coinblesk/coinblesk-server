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
import ch.uzh.csg.coinblesk.server.utils.Pair;
import com.coinblesk.json.BalanceTO;
import com.coinblesk.json.KeyTO;
import com.coinblesk.json.RefundTO;
import com.github.springtestdbunit.DbUnitTestExecutionListener;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import org.bitcoinj.core.Block;
import org.bitcoinj.core.BlockChain;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.PrunedException;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.core.VerificationException;
import org.bitcoinj.core.Wallet;
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
public class WalletTest {
    
    public final static long UNIX_TIME_MONTH = 60*60*24*30;
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
    
    private static final Gson GSON;
    
    static {
         GSON = new GsonBuilder().create();
    }
    
    @Before
    public void setUp() {
         mockMvc = MockMvcBuilders.webAppContextSetup(webAppContext).addFilter(springSecurityFilterChain).build();   
    }
    
    @Test
    public void testTopup() throws Exception {
        //register with good pubilc key
        ECKey ecKeyClient = new ECKey();
        String clientPublicKey = Base64.getEncoder().encodeToString(ecKeyClient.getPubKey());
        KeyTO keyTO = new KeyTO().publicKey(clientPublicKey);
        MvcResult res = mockMvc.perform(post("/p/x").secure(true).contentType(MediaType.APPLICATION_JSON).content(GSON.toJson(keyTO))).andExpect(status().isOk()).andReturn();
        KeyTO status = GSON.fromJson(res.getResponse().getContentAsString(), KeyTO.class);
        Assert.assertEquals(true, status.isSuccess());
        Assert.assertNotNull(status.publicKey());
        
        res = mockMvc.perform(get("/p/b").secure(true).contentType(MediaType.APPLICATION_JSON).content(GSON.toJson(keyTO))).andExpect(status().isOk()).andReturn();
        BalanceTO balance = GSON.fromJson(res.getResponse().getContentAsString(), BalanceTO.class);
        Assert.assertEquals("0", balance.balance());
        
        //
        final List<ECKey> keys = new ArrayList<>();
        keys.add(ecKeyClient);
        keys.add(ECKey.fromPublicOnly(Base64.getDecoder().decode(status.publicKey())));
        final Script script = ScriptBuilder.createP2SHOutputScript(2, keys);
        sendFakeCoins(script);
        
        res = mockMvc.perform(get("/p/b").secure(true).contentType(MediaType.APPLICATION_JSON).content(GSON.toJson(keyTO))).andExpect(status().isOk()).andReturn();
        balance = GSON.fromJson(res.getResponse().getContentAsString(), BalanceTO.class);
        Assert.assertEquals("0.000001", balance.balance());
        
        //pay to multisig address in unittest
    }
    
    @Test
    public void testRefund() throws Exception {
        //register with good pubilc key
        ECKey ecKeyClient = new ECKey();
        String clientPublicKey = Base64.getEncoder().encodeToString(ecKeyClient.getPubKey());
        KeyTO keyTO = new KeyTO().publicKey(clientPublicKey);
        MvcResult res = mockMvc.perform(post("/p/x").secure(true).contentType(MediaType.APPLICATION_JSON).content(GSON.toJson(keyTO))).andExpect(status().isOk()).andReturn();
        KeyTO status = GSON.fromJson(res.getResponse().getContentAsString(), KeyTO.class);
        Assert.assertEquals(true, status.isSuccess());
        Assert.assertNotNull(status.publicKey());
        
        //create funding/toput tx
        final List<ECKey> keys = new ArrayList<>();
        keys.add(ecKeyClient);
        keys.add(ECKey.fromPublicOnly(Base64.getDecoder().decode(status.publicKey())));
        final Script redeemScript = ScriptBuilder.createP2SHOutputScript(2, keys);
        Transaction tx = FakeTxBuilder.createFakeTx(appConfig.getNetworkParameters(), Coin.MICROCOIN, redeemScript.getToAddress(appConfig.getNetworkParameters()));
        Assert.assertTrue(tx.getOutputs().get(0).getScriptPubKey().isPayToScriptHash());
        Assert.assertTrue(tx.getOutputs().get(1).getScriptPubKey().isSentToAddress());
        //create refund tx based on the topup
        ECKey refundAddress = new ECKey();
        Pair<Transaction,List<TransactionSignature>> refund = generateRefundTransaction(tx.getOutputs(), refundAddress, redeemScript, ecKeyClient);
        RefundTO rto = new RefundTO();
        rto.clientPublicKey(clientPublicKey);
        rto.refundTransaction(Base64.getEncoder().encodeToString(refund.element0().unsafeBitcoinSerialize()));
        List<RefundTO.ClientSig> l = new ArrayList<>();
        rto.clientSignatureR(l);
        for(TransactionSignature ts:refund.element1()) {
            l.add(new RefundTO.ClientSig().clientSignatureR(ts.r.toString()).clientSignatureS(ts.s.toString()));
        }
        res = mockMvc.perform(post("/p/r").secure(true).contentType(MediaType.APPLICATION_JSON).content(GSON.toJson(rto))).andExpect(status().isOk()).andReturn();
        RefundTO refundRet = GSON.fromJson(res.getResponse().getContentAsString(), RefundTO.class);
        Transaction fullRefund = new Transaction(appConfig.getNetworkParameters(), Base64.getDecoder().decode(refundRet.refundTransaction()));
        //todo test tx
    }
    
    private Transaction sendFakeCoins(Script script) throws VerificationException, PrunedException, BlockStoreException, InterruptedException {
        Transaction tx = FakeTxBuilder.createFakeTx(appConfig.getNetworkParameters(), Coin.MICROCOIN, script.getToAddress(appConfig.getNetworkParameters()));
        BlockChain chain = walletService.blockChain();
        Block block = FakeTxBuilder.makeSolvedTestBlock(chain.getBlockStore().getChainHead().getHeader(), tx);
        chain.add(block);
        Thread.sleep(250);
        return tx;
    }
    
    private Pair<Transaction,List<TransactionSignature>> generateRefundTransaction(List<TransactionOutput> outputs, ECKey publicClientKey, Script redeemScript, ECKey ecKeyClient) {
        final Transaction refundTransaction = new Transaction(appConfig.getNetworkParameters());
        final long unixTime = System.currentTimeMillis()/ 1000L;
        final long lockTime = unixTime + (LOCK_TIME_MONTHS * UNIX_TIME_MONTH);
        Coin remainingAmount = Coin.ZERO;
        for(TransactionOutput output:outputs) {
            refundTransaction.addInput(output);
            remainingAmount = remainingAmount.add(output.getValue());
        }
        
        remainingAmount = remainingAmount.subtract(Transaction.REFERENCE_DEFAULT_MIN_TX_FEE);
        refundTransaction.addOutput(remainingAmount, publicClientKey);
        refundTransaction.setLockTime(lockTime);
        //sign input, they are multisig
        List<TransactionSignature> clientSigs = new ArrayList<>();
        for(int i=0;i<refundTransaction.getInputs().size();i++) {
            final Sha256Hash sighash = refundTransaction.hashForSignature(i, redeemScript, Transaction.SigHash.ALL, false);
            final TransactionSignature clientSignature = new TransactionSignature(ecKeyClient.sign(sighash), Transaction.SigHash.ALL, false);
            clientSigs.add(clientSignature);
        }
        

        return new Pair<>(refundTransaction, clientSigs);
    }
}
