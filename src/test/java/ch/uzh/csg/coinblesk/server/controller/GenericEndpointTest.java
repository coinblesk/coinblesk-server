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
import com.coinblesk.json.RefundTO;
import com.coinblesk.json.SignTO;
import com.coinblesk.json.TxSig;
import com.coinblesk.json.Type;
import com.coinblesk.json.VerifyTO;
import com.coinblesk.util.BitcoinUtils;
import com.coinblesk.util.Pair;
import com.coinblesk.util.SerializeUtils;
import com.github.springtestdbunit.DbUnitTestExecutionListener;
import com.github.springtestdbunit.annotation.DatabaseOperation;
import com.github.springtestdbunit.annotation.DatabaseTearDown;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.Block;
import org.bitcoinj.core.BlockChain;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.PrunedException;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionOutPoint;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.core.VerificationException;
import org.bitcoinj.crypto.TransactionSignature;
import org.bitcoinj.store.BlockStoreException;
import org.bitcoinj.testing.FakeTxBuilder;
import org.bitcoinj.uri.BitcoinURIParseException;
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
public class GenericEndpointTest {

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
        mockMvc = MockMvcBuilders.webAppContextSetup(webAppContext).addFilter(springSecurityFilterChain).build();
        walletService.init();
        params = appConfig.getNetworkParameters();
    }

    @Test
    public void testOldTime() throws Exception {
        Client client = new Client(params, mockMvc);
        Transaction t = sendFakeCoins(Coin.valueOf(123450), client.p2shAddress());
        Coin amountToRequest = Coin.valueOf(9876);
        Date now = new Date(1);
        Address merchantAddress = new ECKey().toAddress(params);
        Transaction tx = BitcoinUtils.createTx(params, t.getOutputs(), client.p2shAddress(), 
                merchantAddress, amountToRequest.getValue());
        // test /prepare
        SignTO statusPrepare = signServerCall(tx, client, null, now);
        Assert.assertFalse(statusPrepare.isSuccess());
        Assert.assertEquals(Type.TIME_MISMATCH, statusPrepare.type());
        // test /refund-p2sh
        RefundTO statusRefund = refundServerCall(mockMvc, client.ecKey(), Collections.emptyList(), Collections.emptyList(), now);
        Assert.assertFalse(statusRefund.isSuccess());
        Assert.assertEquals(Type.TIME_MISMATCH, statusRefund.type());
        // test /complete-sign
        VerifyTO statusSign = verifyServerCall(mockMvc,client.ecKey(), client.p2shAddress(), new Transaction(params), now);
        Assert.assertFalse(statusSign.isSuccess());
        Assert.assertEquals(Type.TIME_MISMATCH, statusSign.type());

    }

    @Test
    public void testNewTime() throws Exception {
        Client client = new Client(params, mockMvc);
        Transaction t = sendFakeCoins(Coin.valueOf(123450), client.p2shAddress());
        Coin amountToRequest = Coin.valueOf(9876);
        Date now = new Date(Long.MAX_VALUE / 2);
        Address merchantAddress = new ECKey().toAddress(params);
        Transaction tx = BitcoinUtils.createTx(params, t.getOutputs(), client.p2shAddress(), 
                merchantAddress, amountToRequest.getValue());
        // test /prepare
        SignTO status = signServerCall(tx, client, null, now);
        Assert.assertFalse(status.isSuccess());
        Assert.assertEquals(Type.TIME_MISMATCH, status.type());
        // test /refund-p2sh
        RefundTO statusRefund = refundServerCall(mockMvc, client.ecKey(), Collections.emptyList(), Collections.emptyList(), now);
        Assert.assertFalse(statusRefund.isSuccess());
        Assert.assertEquals(Type.TIME_MISMATCH, statusRefund.type());
        // test /complete-sign
        VerifyTO statusSign = verifyServerCall(mockMvc,client.ecKey(), client.p2shAddress(), new Transaction(params), now);
        Assert.assertFalse(statusSign.isSuccess());
        Assert.assertEquals(Type.TIME_MISMATCH, statusSign.type());
    }

    @Test
    public void testWrongSignature() throws Exception {
        Client client = new Client(params, mockMvc);
        Transaction t = sendFakeCoins(Coin.valueOf(123450), client.p2shAddress());
        Coin amountToRequest = Coin.valueOf(9876);
        Date now = new Date();
        Address merchantAddress = new ECKey().toAddress(params);
        Transaction tx = BitcoinUtils.createTx(params, t.getOutputs(), client.p2shAddress(), 
                merchantAddress, amountToRequest.getValue());
        // test /prepare
        SignTO prepareHalfSignTO = signServerCallInput(tx, client.ecKey(), null, now);
        SerializeUtils.sign(prepareHalfSignTO, new ECKey());
        SignTO statusPrepare = signServerCallOutput(prepareHalfSignTO);
        Assert.assertFalse(statusPrepare.isSuccess());
        Assert.assertEquals(Type.JSON_SIGNATURE_ERROR, statusPrepare.type());
        // test /refund-p2sh
        RefundTO refundP2shTO = refundServerCallInput(client.ecKey(),Collections.emptyList(), Collections.emptyList(), now);
        SerializeUtils.sign(refundP2shTO, new ECKey());
        RefundTO statusRefund = refundServerCallOutput(mockMvc, refundP2shTO);
        Assert.assertFalse(statusRefund.isSuccess());
        Assert.assertEquals(Type.JSON_SIGNATURE_ERROR, statusRefund.type());
        // test /complete-sign
        VerifyTO completeSignTO = verifyServerCallInput(client.ecKey(), client.p2shAddress(), new Transaction(params), now);
        SerializeUtils.sign(completeSignTO, new ECKey());
        VerifyTO statusSign = verifyServerCallOutput(mockMvc,completeSignTO);
        Assert.assertFalse(statusSign.isSuccess());
        Assert.assertEquals(Type.JSON_SIGNATURE_ERROR, statusSign.type());
        
    }
    
    @Test
    public void testNotRegistered() throws Exception {
        Client client = new Client(params, mockMvc);
        Transaction t = sendFakeCoins(Coin.valueOf(123450), client.p2shAddress());
        Coin amountToRequest = Coin.valueOf(9876);
        Date now = new Date();
        Address merchantAddress = new ECKey().toAddress(params);
        Transaction tx = BitcoinUtils.createTx(params, t.getOutputs(), client.p2shAddress(), 
                merchantAddress, amountToRequest.getValue());
        // test /prepare
        ECKey key = new ECKey();
        SignTO prepareHalfSignTO = signServerCallInput(tx, key, null, now);
        SerializeUtils.sign(prepareHalfSignTO, key);
        SignTO status = signServerCallOutput(prepareHalfSignTO);
        Assert.assertFalse(status.isSuccess());
        Assert.assertEquals(Type.KEYS_NOT_FOUND, status.type());
        // test /refund-p2sh
        RefundTO refundP2shTO = refundServerCallInput(key, Collections.emptyList(), Collections.emptyList(), now);
        SerializeUtils.sign(refundP2shTO, key);
        RefundTO statusRefund = refundServerCallOutput(mockMvc, refundP2shTO);
        Assert.assertFalse(statusRefund.isSuccess());
        Assert.assertEquals(Type.KEYS_NOT_FOUND, statusRefund.type());
        // test /complete-sign
        VerifyTO completeSignTO = verifyServerCallInput(key, client.p2shAddress(), new Transaction(params), now);
        SerializeUtils.sign(completeSignTO, key);
        VerifyTO statusSign = verifyServerCallOutput(mockMvc,completeSignTO);
        Assert.assertFalse(statusSign.isSuccess());
        Assert.assertEquals(Type.KEYS_NOT_FOUND, statusSign.type());
    }

    @Test
    @DatabaseTearDown(value = {"classpath:DbUnitFiles/emptyDB.xml"}, type = DatabaseOperation.DELETE_ALL)
    public void testCache() throws Exception {
        Client client = new Client(params, mockMvc);
        Transaction t = sendFakeCoins(Coin.valueOf(123450), client.p2shAddress());
        Coin amountToRequest = Coin.valueOf(9876);
        Date now = new Date();
        Address merchantAddress = new ECKey().toAddress(params);
        
        Transaction tx = BitcoinUtils.createTx(params, t.getOutputs(), client.p2shAddress(), 
                merchantAddress, amountToRequest.getValue());
        // test /prepare
        SignTO statusPrepare1 = signServerCall(tx, client, null, now);
        Assert.assertTrue(statusPrepare1.isSuccess());
        // again -> results in same output        
        SignTO statusPrepare2 = signServerCall(tx, client, null, now);
        Assert.assertTrue(statusPrepare2.isSuccess());
        Assert.assertEquals(SerializeUtils.GSON.toJson(statusPrepare1), SerializeUtils.GSON.toJson(statusPrepare2));
        // option 2
        List<Pair<byte[], Long>> outpointCoinPair1 = convert(t.getOutputs());
        SignTO statusPrepare3 = signServerCall(outpointCoinPair1, merchantAddress, amountToRequest.getValue(), client, null, now);
        Assert.assertTrue(statusPrepare3.isSuccess());
        // again -> results in same output
        List<Pair<byte[], Long>> outpointCoinPair2 = convert(t.getOutputs());
        SignTO statusPrepare4 = signServerCall(outpointCoinPair2, merchantAddress, amountToRequest.getValue(), client, null, now);
        Assert.assertTrue(statusPrepare4.isSuccess());
        Assert.assertEquals(SerializeUtils.GSON.toJson(statusPrepare3), SerializeUtils.GSON.toJson(statusPrepare4));
        // test /refund-p2sh
        
        /*List<TransactionSignature> serverSigs = SerializeUtils.deserializeSignatures(statusPrepare1.signatures());
        Transaction txClient = BitcoinUtils.createTx(
                params, t.getOutputs(), client.p2shAddress(), merchantAddress,amountToRequest.value);
        
        RefundInput refundInput = createInputForRefund(
                      params, client, merchantAddress, serverSigs, walletService.refundLockTime(), txClient);
        RefundP2shTO statusRefund1 = refundServerCall(mockMvc, client.ecKey(), refundInput.clientOutpoint(), refundInput.clientSinatures(), now);
        Assert.assertTrue(statusRefund1.isSuccess());
        RefundP2shTO statusRefund2 = refundServerCall(mockMvc, client.ecKey(), refundInput.clientOutpoint(), refundInput.clientSinatures(), now);
        Assert.assertTrue(statusRefund2.isSuccess());
        Assert.assertEquals(SerializeUtils.GSON.toJson(statusRefund1), SerializeUtils.GSON.toJson(statusRefund2));
        // test /complete-sign
        /*Transaction tx = createTx(client, merchantAddress, amountToRequest, serverSigs, t.getOutputs());
        CompleteSignTO statusSign1 = completeSignServerCall(mockMvc,client.ecKey(), merchantAddress, tx, now);
        Assert.assertTrue(statusSign1.isSuccess());
        Assert.assertEquals(Type.SUCCESS, statusSign1.type());
        CompleteSignTO statusSign2 = completeSignServerCall(mockMvc,client.ecKey(), merchantAddress, tx, now);
        Assert.assertTrue(statusSign2.isSuccess());
        Assert.assertEquals(SerializeUtils.GSON.toJson(statusSign1), SerializeUtils.GSON.toJson(statusSign2));*/
    }
    
    /*private Transaction createTx() {
        Transaction txClient = BitcoinUtils.createTx(params,outputs, p2shAddressFrom, p2shAddressTo,
                amount);
        List<TransactionSignature> clientSigs = BitcoinUtils.partiallySign(txClient, client.redeemScript(), client.ecKey());
        BitcoinUtils.applySignatures(txClient, client.redeemScript(), clientSigs, serverSigs, client.clientFirst());
        return txClient;
    }*/
    
    static VerifyTO verifyServerCall(
           MockMvc mockMvc,  ECKey client, Address p2shAddressTo, Transaction fullTx, Date now) throws UnsupportedEncodingException, Exception {
        VerifyTO cs = verifyServerCallInput(client, p2shAddressTo, fullTx, now);
        return verifyServerCallOutput(mockMvc, cs);
    }

    static VerifyTO verifyServerCallInput(
            ECKey client, Address p2shAddressTo, Transaction fullTx, Date now) throws UnsupportedEncodingException, Exception {
        VerifyTO cs = new VerifyTO()
                .clientPublicKey(client.getPubKey())
                //.p2shAddressTo(p2shAddressTo.toString())
                .fullSignedTransaction(fullTx.unsafeBitcoinSerialize())
                .currentDate(now.getTime());
        if (cs.messageSig() == null) {
            SerializeUtils.sign(cs, client);
        }
        return cs;
    }
    
    static VerifyTO verifyServerCallOutput(MockMvc mockMvc, VerifyTO cs) throws Exception {
        MvcResult res = mockMvc.perform(post("/v2/p/v").secure(true).
                contentType(MediaType.APPLICATION_JSON).content(SerializeUtils.GSON.toJson(cs))).andExpect(status().isOk()).andReturn();
        return SerializeUtils.GSON.fromJson(res.getResponse().getContentAsString(), VerifyTO.class);
    }
    
    static RefundTO refundServerCall(MockMvc mockMvc, ECKey client, List<Pair<TransactionOutPoint, Coin>> refundClientOutpoints,
            List<TransactionSignature> partiallySignedRefundClient, Date date) throws Exception {
        RefundTO refundP2shTO = refundServerCallInput(client, refundClientOutpoints, partiallySignedRefundClient, date);
        return refundServerCallOutput(mockMvc, refundP2shTO);
    }

    static RefundTO refundServerCallInput(ECKey client, List<Pair<TransactionOutPoint, Coin>> refundClientOutpoints,
            List<TransactionSignature> partiallySignedRefundClient, Date date) throws Exception {
        RefundTO refundP2shTO = new RefundTO();
        refundP2shTO.clientPublicKey(client.getPubKey());
        //refundP2shTO.refundClientOutpointsCoinPair(SerializeUtils.serializeOutPointsCoin(refundClientOutpoints));
        //refundP2shTO.refundSignaturesClient(SerializeUtils.serializeSignatures(partiallySignedRefundClient));
        refundP2shTO.currentDate(date.getTime());
        if (refundP2shTO.messageSig() == null) {
            SerializeUtils.sign(refundP2shTO, client);
        }
        return refundP2shTO;
    }
    
    static RefundTO refundServerCallOutput(MockMvc mockMvc, RefundTO refundP2shTO) throws Exception {
        MvcResult res = mockMvc.perform(post("/p/r").secure(true).
                contentType(MediaType.APPLICATION_JSON).content(SerializeUtils.GSON.toJson(refundP2shTO))).andExpect(status().isOk()).andReturn();
        return SerializeUtils.GSON.fromJson(res.getResponse().getContentAsString(), RefundTO.class);
    }
    
    private SignTO signServerCall(List<Pair<byte[], Long>> outpointCoinPair, Address to, long amount, Client client, TxSig clientSig, Date date) throws Exception {
        SignTO prepareHalfSignTO = signServerCallInput(outpointCoinPair, to, amount, client.ecKey(), clientSig, date);
        return signServerCallOutput(prepareHalfSignTO);
    }

    private SignTO signServerCallInput(List<Pair<byte[], Long>> outpointCoinPair, Address to, long amount, ECKey client, TxSig clientSig, Date date) throws Exception {
        SignTO prepareHalfSignTO = new SignTO()
                .outpointsCoinPair(outpointCoinPair)
                .amountToSpend(amount)
                .p2shAddressTo(to.toString())
                .clientPublicKey(client.getPubKey())
                .messageSig(clientSig)
                .currentDate(date.getTime());
        if (prepareHalfSignTO.messageSig() == null) {
            SerializeUtils.sign(prepareHalfSignTO, client);
        }
        return prepareHalfSignTO;
    }

    private SignTO signServerCall(Transaction tx, Client client, TxSig clientSig, Date date) throws Exception {
        SignTO prepareHalfSignTO = signServerCallInput(tx, client.ecKey(), clientSig, date);
        return signServerCallOutput(prepareHalfSignTO);
    }

    private SignTO signServerCallInput(Transaction tx, ECKey client, TxSig clientSig, Date date) throws Exception {
        SignTO prepareHalfSignTO = new SignTO()
                .transaction(tx.unsafeBitcoinSerialize())
                .clientPublicKey(client.getPubKey())
                .messageSig(clientSig)
                .currentDate(date.getTime());
        if (prepareHalfSignTO.messageSig() == null) {
            SerializeUtils.sign(prepareHalfSignTO, client);
        }
        return prepareHalfSignTO;
    }
    
    private SignTO signServerCallOutput(SignTO prepareHalfSignTO) throws Exception {
        MvcResult res = mockMvc.perform(post("/v2/p/s").secure(true).
                contentType(MediaType.APPLICATION_JSON).content(SerializeUtils.GSON.toJson(prepareHalfSignTO))).andExpect(status().isOk()).andReturn();
        return SerializeUtils.GSON.fromJson(res.getResponse().getContentAsString(), SignTO.class);
    }

    private Transaction sendFakeCoins(Coin amount, Address to) throws VerificationException, PrunedException, BlockStoreException, InterruptedException {
        Transaction tx = FakeTxBuilder.createFakeTx(params, amount, to);
        BlockChain chain = walletService.blockChain();
        Block block = FakeTxBuilder.makeSolvedTestBlock(chain.getBlockStore().getChainHead().getHeader(), tx);
        chain.add(block);
        Thread.sleep(250);
        return tx;
    }
    
    /*private Transaction createTx(Client client, Address p2shAddress, Coin amountToRequest, List<TransactionSignature> serverSigs, 
            List<TransactionOutput> clientWalletOutputs) {
        Transaction txClient = BitcoinUtils.createTx(params,
                clientWalletOutputs, client.p2shAddress(), p2shAddress,
                amountToRequest.value);
        List<TransactionSignature> clientSigs = BitcoinUtils.partiallySign(txClient, client.redeemScript(), client.ecKey());
        BitcoinUtils.applySignatures(txClient, client.redeemScript(), clientSigs, serverSigs, client.clientFirst());
        return txClient;
    }*/
    
    /*Transaction txClient = BitcoinUtils.createTx(params,
                clientWalletOutputs, client.p2shAddress(), p2shAddress,
                amountToRequest.value);*/

    /*static RefundInput createInputForRefund(NetworkParameters params,
            Client client, Address p2shAddressTo, List<TransactionSignature> serverSigs, 
            int lockTime, Transaction txClient, TransactionOutput... outputs) {
        
        //**********Client**********
        //Client rebuilds the tx and adds the signatures from the Server, making it a full transaction
        
        
        List<TransactionSignature> clientSigs = BitcoinUtils.partiallySign(txClient, client.redeemScript(), client.ecKey());
        BitcoinUtils.applySignatures(txClient, client.redeemScript(), clientSigs, serverSigs, client.clientFirst());
        System.out.println("client tx client: "+txClient);
        //Client now has the full tx, based on that, Client creates the refund tx
        //Client uses the outputs of the tx as all the outputs are in that tx, no
        //need to merge
        List<TransactionOutput> clientMergedOutputs = BitcoinUtils.myOutputs(
                params, txClient.getOutputs(), client.p2shAddress());
        
        for(TransactionOutput output: outputs) {
            clientMergedOutputs.add(output);
        }
        
        Transaction unsignedRefundTx = BitcoinUtils.generateUnsignedRefundTx(
                params, clientMergedOutputs, null,
                client.ecKey().toAddress(params), client.redeemScript(), lockTime);
        if (unsignedRefundTx == null) {
            throw new RuntimeException("not enough funds");
        }
        //The refund is currently only signed by the Client (half)
        List<TransactionSignature> partiallySignedRefundClient = BitcoinUtils.partiallySign(
                unsignedRefundTx, client.redeemScript(), client.ecKey());
        
        System.out.println("client checks client sigs with1: "+unsignedRefundTx);
        System.out.println("client checks client sigs with2: "+partiallySignedRefundClient);
        
        List<Pair<TransactionOutPoint, Coin>> refundClientOutpoints = BitcoinUtils.outpointsFromInput(unsignedRefundTx);
        for(Pair<TransactionOutPoint, Coin> p: refundClientOutpoints) {
            System.out.println("aeu"+p.element0()+"/"+p.element1());
        }
        Assert.assertEquals(1 + outputs.length, refundClientOutpoints.size());
        //The client also needs to create the outpoits for the refund for the merchant, as the client
        //is the only one that knows that full tx at the moment
        //but this is only! for the output of the current tx
        List<Pair<TransactionOutPoint, Coin>> refundMerchantOutpoints = BitcoinUtils.outpointsFromOutputFor(params, txClient, p2shAddressTo);
        //The Client sends the refund signatures and the transaction outpoints (client, merchant) to the Merchant
        return new RefundInput()
                .clientOutpoint(refundClientOutpoints)
                .merchantOutpoint(refundMerchantOutpoints)
                .clientSinatures(partiallySignedRefundClient)
                .fullTx(txClient);
        
    }*/

    private List<Pair<byte[], Long>> convert(List<TransactionOutput> outputs) {
        List<Pair<byte[], Long>> retVal = new ArrayList<>(outputs.size());
        for(TransactionOutput output: outputs) {
            retVal.add(new Pair<>(output.getOutPointFor().unsafeBitcoinSerialize(), output.getValue().getValue()));
        }
        return retVal;
    }
}
