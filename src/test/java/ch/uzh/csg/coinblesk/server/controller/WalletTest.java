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
import com.coinblesk.json.Type;
import com.coinblesk.util.BitcoinUtils;
import com.coinblesk.util.Pair;
import com.coinblesk.util.SerializeUtils;
import com.github.springtestdbunit.DbUnitTestExecutionListener;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.Block;
import org.bitcoinj.core.BlockChain;
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
        
        KeyTO keyTO = new KeyTO().publicKey(ecKeyClient.getPubKey());
        MvcResult res = mockMvc.perform(post("/p/x").secure(true).contentType(MediaType.APPLICATION_JSON).content(GSON.toJson(keyTO))).andExpect(status().isOk()).andReturn();
        KeyTO status = GSON.fromJson(res.getResponse().getContentAsString(), KeyTO.class);
        Assert.assertEquals(true, status.isSuccess());
        Assert.assertNotNull(status.publicKey());
        
        res = mockMvc.perform(get("/p/b").secure(true).contentType(MediaType.APPLICATION_JSON).content(GSON.toJson(keyTO))).andExpect(status().isOk()).andReturn();
        BalanceTO balance = GSON.fromJson(res.getResponse().getContentAsString(), BalanceTO.class);
        Assert.assertEquals(0, balance.balance());
        
        //
        final List<ECKey> keys = new ArrayList<>();
        keys.add(ecKeyClient);
        keys.add(ECKey.fromPublicOnly(status.publicKey()));
        final Script script = ScriptBuilder.createP2SHOutputScript(2, keys);
        sendFakeCoins(Coin.MICROCOIN, script.getToAddress(appConfig.getNetworkParameters()));
        
        res = mockMvc.perform(get("/p/b").secure(true).contentType(MediaType.APPLICATION_JSON).content(GSON.toJson(keyTO))).andExpect(status().isOk()).andReturn();
        balance = GSON.fromJson(res.getResponse().getContentAsString(), BalanceTO.class);
        Assert.assertEquals(Coin.MICROCOIN.value, balance.balance());
        
        //pay to multisig address in unittest
    }
    
    @Test
    public void testRefund() throws Exception {
        //register with good pubilc key
        ECKey ecKeyClient = new ECKey();
        KeyTO keyTO = new KeyTO().publicKey(ecKeyClient.getPubKey());
        MvcResult res = mockMvc.perform(post("/p/x").secure(true).contentType(MediaType.APPLICATION_JSON).content(GSON.toJson(keyTO))).andExpect(status().isOk()).andReturn();
        KeyTO status = GSON.fromJson(res.getResponse().getContentAsString(), KeyTO.class);
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
        int lockTime = BitcoinUtils.lockTimeBlock(2, walletService.currentBlock());
        Transaction refund = BitcoinUtils.generateUnsignedRefundTx(
                appConfig.getNetworkParameters(), tx.getOutputs(), null,
                refundAddress.toAddress(appConfig.getNetworkParameters()), redeemScript, lockTime);
        List<TransactionSignature> tss = BitcoinUtils.partiallySign(refund, redeemScript, ecKeyClient);
        RefundTO rto = new RefundTO();
        rto.clientPublicKey(ecKeyClient.getPubKey());
        rto.refundTransaction(refund.unsafeBitcoinSerialize());
        rto.clientSignatures(SerializeUtils.serializeSignatures(tss));
        res = mockMvc.perform(post("/p/r").secure(true).contentType(MediaType.APPLICATION_JSON).content(GSON.toJson(rto))).andExpect(status().isOk()).andReturn();
        RefundTO refundRet = GSON.fromJson(res.getResponse().getContentAsString(), RefundTO.class);
        Transaction fullRefund = new Transaction(appConfig.getNetworkParameters(), refundRet.refundTransaction());
        //todo test tx
    }
    
    @Test
    public void testSameRefundTx() throws Exception {
        final Transaction tx1 = new Transaction(appConfig.getNetworkParameters());
        final long unixTime1 = System.currentTimeMillis()/ 1000L;
        final long lockTime1 = unixTime1 + (LOCK_TIME_MONTHS * UNIX_TIME_MONTH);
        tx1.setLockTime(lockTime1);
        
        Thread.sleep(1100);
        
        final Transaction tx2 = new Transaction(appConfig.getNetworkParameters());
        final long unixTime2 = System.currentTimeMillis()/ 1000L;
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
    public void testBurned() throws Exception {
        Client client = new Client(appConfig.getNetworkParameters(), mockMvc);
        sendFakeCoins(Coin.valueOf(123450), client.p2shAddress());
        Coin amountToRequest = Coin.valueOf(9876);
        PrepareHalfSignTO status = prepare(amountToRequest, client, new ECKey().toAddress(appConfig.getNetworkParameters()));
        Assert.assertTrue(status.isSuccess());
        //send again -> burned counter is increased
        status = prepare(amountToRequest, client, new ECKey().toAddress(appConfig.getNetworkParameters()));
        Assert.assertTrue(status.isSuccess());
    }
    
    private PrepareHalfSignTO prepare(Coin amountToRequest, Client client, Address to) throws Exception {
        PrepareHalfSignTO prepareHalfSignTO = new PrepareHalfSignTO()
                .amountToSpend(amountToRequest.longValue())
                .clientPublicKey(client.ecKey().getPubKey())
                .p2shAddressTo(to.toString());
        //send request to sever
        MvcResult res = mockMvc.perform(post("/p/p").secure(true).
                contentType(MediaType.APPLICATION_JSON).content(GSON.toJson(prepareHalfSignTO))).andExpect(status().isOk()).andReturn();
        return GSON.fromJson(res.getResponse().getContentAsString(), PrepareHalfSignTO.class);
    }
    
    @Test
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
        //Client sends ok, publickey (and client signs/encrypts it) + amount/p2shAddressMerchant
        //*************************
        //TODO: signatures or encryption
        
        //***********Merchant******* 
        PrepareHalfSignTO prepareHalfSignTO = new PrepareHalfSignTO()
                .amountToSpend(amountToRequest.longValue())
                .clientPublicKey(client.ecKey().getPubKey())
                .p2shAddressTo(merchant.p2shAddress().toString());
        //send request to sever
        MvcResult res = mockMvc.perform(post("/p/p").secure(true).
                contentType(MediaType.APPLICATION_JSON).content(GSON.toJson(prepareHalfSignTO))).andExpect(status().isOk()).andReturn();
        PrepareHalfSignTO status = GSON.fromJson(res.getResponse().getContentAsString(), PrepareHalfSignTO.class);
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
        BitcoinUtils.applySignatures(txClient, client.redeemScript(), clientSigs, serverSigs);
        //Client now has the full tx, based on that, Client creates the refund tx
        //Client uses the outputs of the tx as all the outputs are in that tx, no
        //need to merge
        List<TransactionOutput> clientMergedOutputs = BitcoinUtils.myOutputs(
                appConfig.getNetworkParameters(), txClient.getOutputs(), client.p2shAddress());
        int lockTime = BitcoinUtils.lockTimeBlock(2, walletService.currentBlock());
        Transaction unsignedRefundTx = BitcoinUtils.generateUnsignedRefundTx(
                appConfig.getNetworkParameters(), clientMergedOutputs, null,
                client.ecKey().toAddress(appConfig.getNetworkParameters()), client.redeemScript(), lockTime);
        System.err.println("raw refund client:"+unsignedRefundTx);
        System.err.println("redeem: "+client.redeemScript());
        if(unsignedRefundTx == null) {
            throw new RuntimeException("not enough funds");
        }
        //The refund is currently only signed by the Client (half)
        List<TransactionSignature> partiallySignedRefundClient = BitcoinUtils.partiallySign(
                    unsignedRefundTx, client.redeemScript(), client.ecKey());
        List<Pair<TransactionOutPoint,Coin>> refundClientOutpoints = BitcoinUtils.outpointsFromInput(unsignedRefundTx);
        //The client also needs to create the outpoits for the refund for the merchant, as the client
        //is the only one that knows that full tx at the moment
        //but this is only! for the output of the current tx
        List<Pair<TransactionOutPoint, Coin>> refundMerchantOutpoints = BitcoinUtils.outpointsFromOutputFor(appConfig.getNetworkParameters(), txClient, merchant.p2shAddress());
        //The Client sends the refund signatures and the transaction outpoints (client, merchant) to the Merchant
        //*************************
        
        //***********Merchant******* 
        //The Merchant forwards it to the server, adds its refund signature as well, and the half signed transaction from previous (state)
        RefundP2shTO refundP2shTO = new RefundP2shTO();
        refundP2shTO.clientPublicKey(client.ecKey().getPubKey());
        refundP2shTO.refundClientOutpointsCoinPair(SerializeUtils.serializeOutPointsCoin(refundClientOutpoints));
        refundP2shTO.refundSignaturesClient(SerializeUtils.serializeSignatures(partiallySignedRefundClient));
        res = mockMvc.perform(post("/p/f").secure(true).
                contentType(MediaType.APPLICATION_JSON).content(GSON.toJson(refundP2shTO))).andExpect(status().isOk()).andReturn();
        RefundP2shTO statusClient = GSON.fromJson(res.getResponse().getContentAsString(), RefundP2shTO.class);
        System.out.println("cb-message: "+statusClient.message()+"/"+statusClient.type());
        Assert.assertTrue(statusClient.isSuccess());
        //now the merchant has the full refund for the client
        
        //This goes from the Client to the Merhchant, which recreates the refund tx and adds its sigs
        //first get the inputs
        List<TransactionInput> preBuiltInupts = BitcoinUtils.convertPointsToInputs(
                appConfig.getNetworkParameters(), refundMerchantOutpoints, merchant.redeemScript());
        List<TransactionOutput> merchantWalletOutputs = walletService.getOutputs(merchant.p2shAddress());
        //add/remove pending, approved, remove burned
        Transaction unsignedRefundMerchant = BitcoinUtils.generateUnsignedRefundTx(
                    appConfig.getNetworkParameters(), merchantWalletOutputs, preBuiltInupts,
                    merchant.ecKey().toAddress(appConfig.getNetworkParameters()), merchant.redeemScript(), lockTime);
        if(unsignedRefundMerchant == null) {
            throw new RuntimeException("not enough funds");
        }
        List<TransactionSignature> partiallySignedRefundMerchant = BitcoinUtils.partiallySign(
                    unsignedRefundMerchant, merchant.redeemScript(), merchant.ecKey());
        refundP2shTO = new RefundP2shTO();
        refundP2shTO.clientPublicKey(merchant.ecKey().getPubKey());
        refundP2shTO.refundClientOutpointsCoinPair(SerializeUtils.serializeOutPointsCoin(refundMerchantOutpoints));
        refundP2shTO.refundSignaturesClient(SerializeUtils.serializeSignatures(partiallySignedRefundMerchant));
        //Merchant does server call
        res = mockMvc.perform(post("/p/f").secure(true).
                contentType(MediaType.APPLICATION_JSON).content(GSON.toJson(refundP2shTO))).andExpect(status().isOk()).andReturn();
        RefundP2shTO statusMerchant = GSON.fromJson(res.getResponse().getContentAsString(), RefundP2shTO.class);
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
                client.redeemScript(), partiallySignedRefundClient, refundServerSigs);
        Transaction refundClient = unsignedRefundTx;
        //client does not have this, just for testing:
        Transaction refundServer = new Transaction(appConfig.getNetworkParameters(), statusClient.fullRefundTransaction());
        //test both refund tx, as built by the Client and by the Server
        System.err.println("rclient:"+refundClient);
        System.err.println("rserver:"+refundServer);
        Assert.assertEquals(refundClient, refundServer);
        Assert.assertEquals(1, refundClient.getInputs().size());
        Assert.assertEquals(1, refundClient.getOutputs().size());
        //Client is now to send the sigs in order that the server can make a full tx. 
        //In fact also the Merchant has the information to build a full tx. The Merchant sends the full tx to the server
        //*************************
        
        //***********Merchant******* 
        BitcoinUtils.applySignatures(txServer, 
                client.redeemScript(), clientSigs, serverSigs);
        Transaction fullTxMerchant = txServer;
        //server call
        CompleteSignTO cs = new CompleteSignTO()
                .clientPublicKey(client.ecKey().getPubKey())
                .merchantPublicKey(merchant.ecKey().getPubKey())
                .fullSignedTransaction(fullTxMerchant.unsafeBitcoinSerialize());
        res = mockMvc.perform(post("/p/s").secure(true).
                contentType(MediaType.APPLICATION_JSON).content(GSON.toJson(cs))).andExpect(status().isOk()).andReturn();
        CompleteSignTO status3 = GSON.fromJson(res.getResponse().getContentAsString(), CompleteSignTO.class);
        //success!
        Assert.assertTrue(status3.isSuccess());
        //Server or Merchant can broadcast the full tx
        Assert.assertEquals(fullTxMerchant, txClient);
        sendFakeBroadcast(fullTxMerchant);
        //*************************
        
        //*****CHECKS********
        //check balance on Client
        KeyTO keyTO = new KeyTO().publicKey(client.ecKey().getPubKey());
        res = mockMvc.perform(get("/p/b").secure(true).contentType(MediaType.APPLICATION_JSON).content(GSON.toJson(keyTO))).andExpect(status().isOk()).andReturn();
        BalanceTO balance = GSON.fromJson(res.getResponse().getContentAsString(), BalanceTO.class);
        Assert.assertEquals(108574, balance.balance());
        
        //check balance on Merchant
        keyTO = new KeyTO().publicKey(merchant.ecKey().getPubKey());
        res = mockMvc.perform(get("/p/b").secure(true).contentType(MediaType.APPLICATION_JSON).content(GSON.toJson(keyTO))).andExpect(status().isOk()).andReturn();
        balance = GSON.fromJson(res.getResponse().getContentAsString(), BalanceTO.class);
        Assert.assertEquals(9876, balance.balance());
    }
    
    final protected static char[] hexArray = "0123456789ABCDEF".toCharArray();
    public static String bytesToHex(byte[] bytes) {
    char[] hexChars = new char[bytes.length * 2];
    for ( int j = 0; j < bytes.length; j++ ) {
        int v = bytes[j] & 0xFF;
        hexChars[j * 2] = hexArray[v >>> 4];
        hexChars[j * 2 + 1] = hexArray[v & 0x0F];
    }
    return new String(hexChars);
}
    
    
    
    private ECKey register(ECKey ecKeyClient) throws Exception {
        KeyTO keyTO = new KeyTO().publicKey(ecKeyClient.getPubKey());
        MvcResult res = mockMvc.perform(post("/p/x").secure(true).
                contentType(MediaType.APPLICATION_JSON).content(GSON.toJson(keyTO))).andExpect(status().isOk()).andReturn();
        KeyTO status = GSON.fromJson(res.getResponse().getContentAsString(), KeyTO.class);
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
    
    private Transaction sendFakeBroadcast(Transaction tx) throws BlockStoreException, VerificationException, PrunedException, InterruptedException {
        BlockChain chain = walletService.blockChain();
        Block block = FakeTxBuilder.makeSolvedTestBlock(chain.getBlockStore().getChainHead().getHeader(), tx);
        chain.add(block);
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
    
    /*private Pair<Transaction,List<TransactionSignature>> generateRefundTransaction(List<TransactionOutput> outputs, ECKey publicClientKey, Script redeemScript, ECKey ecKeyClient) {
        final Transaction refundTransaction = new Transaction(appConfig.getNetworkParameters());
        final long unixTime = ((System.currentTimeMillis() / 1000L) / (10 * 60)) * (10 * 60);
        final long lockTime = unixTime + (LOCK_TIME_MONTHS * UNIX_TIME_MONTH);
        Coin remainingAmount = Coin.ZERO;
        for(TransactionOutput output:outputs) {
            Address a = output.getAddressFromP2SH(appConfig.getNetworkParameters());
            if(a!=null && a.equals(redeemScript.getToAddress(appConfig.getNetworkParameters()))) {
                refundTransaction.addInput(output);
                remainingAmount = remainingAmount.add(output.getValue());
            }
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
    
    private Pair<Transaction,Pair<List<TransactionOutPoint>,List<TransactionSignature>>> generateRefundTransaction2(List<TransactionOutput> outputs, Address refund, Script redeemScript, ECKey ecKeyClient) {
        final Transaction refundTransaction = new Transaction(appConfig.getNetworkParameters());
        final long unixTime = ((System.currentTimeMillis() / 1000L) / (10 * 60)) * (10 * 60);
        final long lockTime = unixTime + (LOCK_TIME_MONTHS * UNIX_TIME_MONTH);
        Coin remainingAmount = Coin.ZERO;
        for(TransactionOutput output:outputs) {
            Address a = output.getAddressFromP2SH(appConfig.getNetworkParameters());
            if(a!=null && a.equals(redeemScript.getToAddress(appConfig.getNetworkParameters()))) {
                refundTransaction.addInput(output);
                remainingAmount = remainingAmount.add(output.getValue());
            }
        }
        
        remainingAmount = remainingAmount.subtract(Transaction.REFERENCE_DEFAULT_MIN_TX_FEE);
        refundTransaction.addOutput(remainingAmount, refund);
        refundTransaction.setLockTime(lockTime);
        //sign input, they are multisig
        List<TransactionSignature> clientSigs = new ArrayList<>();
        List<TransactionOutPoint> tpoint = new ArrayList<>();
        for(int i=0;i<refundTransaction.getInputs().size();i++) {
            final Sha256Hash sighash = refundTransaction.hashForSignature(i, redeemScript, Transaction.SigHash.ALL, false);
            final TransactionSignature clientSignature = new TransactionSignature(ecKeyClient.sign(sighash), Transaction.SigHash.ALL, false);
            clientSigs.add(clientSignature);
            tpoint.add(refundTransaction.getInputs().get(i).getOutpoint());
        }
        
        storedHalfRefundTx.put(ecKeyClient, refundTransaction);
        return new Pair<>(refundTransaction,new Pair<>(tpoint, clientSigs));
    }
    
     private static Map<ECKey, Transaction> storedHalfRefundTx = new HashMap<>();

    

    private Transaction signFully(Transaction tx, 
            List<TransactionSignature> clientSigs, List<TransactionSignature> serverSigs, Script redeemScript) {
        for(int i=0;i<tx.getInputs().size();i++) {
            List<TransactionSignature> l = new ArrayList<>(2);
            l.add(clientSigs.get(i));
            l.add(serverSigs.get(i));
            final Script refundTransactionInputScript = ScriptBuilder.createP2SHMultiSigInputScript(l, redeemScript);
            tx.getInput(i).setScriptSig(refundTransactionInputScript);
        }    
        return tx;   
    }*/
}
