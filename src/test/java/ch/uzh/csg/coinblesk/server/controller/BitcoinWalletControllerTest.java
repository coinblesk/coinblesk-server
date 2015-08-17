package ch.uzh.csg.coinblesk.server.controller;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.util.Base64;
import java.util.List;
import java.util.Random;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Matchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.GsonHttpMessageConverter;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import ch.uzh.csg.coinblesk.Currency;
import ch.uzh.csg.coinblesk.JsonConverter;
import ch.uzh.csg.coinblesk.bitcoin.BitcoinNet;
import ch.uzh.csg.coinblesk.responseobject.ExchangeRateTransferObject;
import ch.uzh.csg.coinblesk.responseobject.ServerSignatureRequestTransferObject;
import ch.uzh.csg.coinblesk.responseobject.TransferObject;
import ch.uzh.csg.coinblesk.responseobject.WatchingKeyTransferObject;
import ch.uzh.csg.coinblesk.server.bitcoin.InvalidTransactionException;
import ch.uzh.csg.coinblesk.server.service.BitcoinWalletService;

@RunWith(SpringJUnit4ClassRunner.class)
@WebAppConfiguration
@TestPropertySource("classpath:application-test.properties")
public class BitcoinWalletControllerTest {
    
    private final static Random RND = new Random(42L);

    @Autowired
    @InjectMocks
    private BitcoinWalletController bitcoinWalletController;

    @Mock
    private BitcoinWalletService bitcoinWalletService;

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MediaType contentType = new MediaType(MediaType.APPLICATION_JSON.getType(), MediaType.APPLICATION_JSON.getSubtype(), Charset.forName("utf8"));

    private MockMvc mockMvc;

    @Autowired
    public void configureMessageConverters(List<HttpMessageConverter<?>> converters) {
      converters.add(new GsonHttpMessageConverter());
    }
    
    @Before
    public void setup() throws Exception {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testGetExchangeRate() throws Exception {
        
        MvcResult res = mockMvc.perform(get("/wallet/exchangeRate/USD"))
                        .andExpect(status().isOk())
                        .andExpect(content().contentType(contentType))
                        .andExpect(jsonPath("$.status", is(TransferObject.Status.REPLY_SUCCESS.toString())))
                        .andReturn();
        
        // check the response
        ExchangeRateTransferObject exchangeRateObj = JsonConverter.fromJson(res.getResponse().getContentAsString(), ExchangeRateTransferObject.class);
        Assert.assertTrue(exchangeRateObj.getExchangeRates().containsKey(Currency.CHF));
        Assert.assertNotNull(exchangeRateObj.getExchangeRate(Currency.CHF));
        BigDecimal rate = new BigDecimal(exchangeRateObj.getExchangeRate(Currency.CHF));
        Assert.assertTrue(rate.signum() == 1); // positive exchange rate
        
        printResponse(res);
    }

    @Test
    public void testGetSetupInfo() throws Exception {
        BitcoinNet bitcoinNet = BitcoinNet.TESTNET;
        String watchingKey = "watching key";

        Mockito.doReturn(watchingKey).when(bitcoinWalletService).getSerializedServerWatchingKey();
        Mockito.doReturn(bitcoinNet).when(bitcoinWalletService).getBitcoinNet();
        
        MvcResult res = mockMvc.perform(get("/wallet/setupInfo/"))
                        .andExpect(status().isOk())
                        .andExpect(content().contentType(contentType))
                        .andExpect(jsonPath("$.serverWatchingKey", is(watchingKey)))
                        .andExpect(jsonPath("$.bitcoinNet", is(bitcoinNet.toString())))
                        .andExpect(jsonPath("$.status", is(TransferObject.Status.REPLY_SUCCESS.toString())))
                        .andReturn();
        printResponse(res);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testCreateRefundTx() throws Exception {

        ServerSignatureRequestTransferObject sigReq = getMockSigRequestObject();
        String signedTx = "signed-tx";
        
        Mockito.doReturn(signedTx).when(bitcoinWalletService).signRefundTx(Mockito.any(String.class), Mockito.any(List.class));
        
        printRequest(sigReq);
        
        System.out.println(this.json(sigReq));
        MvcResult res = mockMvc.perform(post("/wallet/signRefundTx")
                .content(this.json(sigReq))
                .contentType(contentType))
                .andExpect(status().isOk())
                .andExpect(content().contentType(contentType))
                .andExpect(jsonPath("$.refundTx", is(signedTx)))
                .andExpect(jsonPath("$.status", is(TransferObject.Status.REPLY_SUCCESS.toString())))
                .andReturn();
        
        printResponse(res);

    }

    @SuppressWarnings("unchecked")
    @Test
    public void testCreateRefundTx_invalidTx() throws Exception {
        ServerSignatureRequestTransferObject sigReq = getMockSigRequestObject();

        Mockito.doThrow(new InvalidTransactionException()).when(bitcoinWalletService).signRefundTx(Mockito.any(String.class), Mockito.any(List.class));
        
        printRequest(sigReq);

        MvcResult res = mockMvc.perform(post("/wallet/signRefundTx")
                .content(this.json(sigReq))
                .contentType(contentType))
                .andExpect(status().is4xxClientError())
                .andExpect(content().contentType(contentType))
                .andExpect(jsonPath("$.status", is(TransferObject.Status.REPLY_FAILED.toString())))
                .andReturn();
        
        printResponse(res);

    }

    @SuppressWarnings("unchecked")
    @Test
    public void testSignAndBroadcastTx() throws Exception {
        String partialTx = "signed-tx";

        ServerSignatureRequestTransferObject sigReq = getMockSigRequestObject();

        String signedTx = "fullySignedBase64EncodedTx";
        Mockito.doReturn(signedTx).when(bitcoinWalletService).signAndBroadcastTx(Mockito.any(String.class), Mockito.any(List.class));
        
        printRequest(sigReq);
        
        MvcResult res = mockMvc.perform(post("/wallet/signAndBroadcastTx")
                .content(this.json(sigReq))
                .contentType(contentType))
                .andExpect(status().isOk())
                .andExpect(content().contentType(contentType))
                .andExpect(jsonPath("$.status", is(TransferObject.Status.REPLY_SUCCESS.toString())))
                .andExpect(jsonPath("$.signedTx", is(signedTx)))
                .andReturn();

        printResponse(res);

        Mockito.verify(bitcoinWalletService, Mockito.times(1)).signAndBroadcastTx(Matchers.eq(sigReq.getPartialTx()), Matchers.anyList());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testSignAndBroadcastTx_invalidTx() throws Exception {

        ServerSignatureRequestTransferObject sigReq = new ServerSignatureRequestTransferObject();

        Mockito.doReturn(false).when(bitcoinWalletService).signAndBroadcastTx(Mockito.any(String.class), Mockito.any(List.class));
        
        printRequest(sigReq);

        MvcResult res = mockMvc.perform(post("/wallet/signAndBroadcastTx")
                .content(this.json(sigReq))
                .contentType(contentType))
                .andExpect(status().is4xxClientError())
                .andExpect(content().contentType(contentType))
                .andExpect(jsonPath("$.status", is(TransferObject.Status.REPLY_FAILED.toString())))
                .andReturn();

        printResponse(res);

        //Assert.assertFalse(response.isSuccessful());
        Mockito.verify(bitcoinWalletService, Mockito.times(1)).signAndBroadcastTx(Matchers.anyString(), Matchers.anyList());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testSignAndBroadcastTx_invalidTxException() throws Exception {

        ServerSignatureRequestTransferObject sigReq = new ServerSignatureRequestTransferObject();

        Mockito.doThrow(new InvalidTransactionException()).when(bitcoinWalletService).signAndBroadcastTx(Mockito.any(String.class), Mockito.any(List.class));
        
        printRequest(sigReq);

        MvcResult res = mockMvc.perform(post("/wallet/signAndBroadcastTx")
                .content(this.json(sigReq))
                .contentType(contentType))
                .andExpect(status().is4xxClientError())
                .andExpect(content().contentType(contentType))
                .andExpect(jsonPath("$.status", is(TransferObject.Status.REPLY_FAILED.toString())))
                .andReturn();

        printResponse(res);
        
        Mockito.verify(bitcoinWalletService, Mockito.times(1)).signAndBroadcastTx(Matchers.anyString(), Matchers.anyList());
    }
    

    @Test
    public void testSaveWatchingKey() throws Exception {
        WatchingKeyTransferObject watchingKeyTransferObject = new WatchingKeyTransferObject();
        watchingKeyTransferObject.setBitcoinNet(BitcoinNet.TESTNET);
        watchingKeyTransferObject.setWatchingKey("watchingKey12345");
        
        MvcResult res = mockMvc.perform(post("/wallet/saveWatchingKey")
                        .content(this.json(watchingKeyTransferObject))
                        .contentType(contentType))
                        .andExpect(status().isOk())
                        .andExpect(content().contentType(contentType))
                        .andExpect(jsonPath("$.status", is(TransferObject.Status.REPLY_SUCCESS.toString())))
                        .andReturn();
        printResponse(res);
    }

    private String json(Object o)  {
        return JsonConverter.toJson(o);
    }
    
    private void printResponse(MvcResult res) {
        try {
            System.out.println("REPLY:");
            System.out.println("Status: " + res.getResponse().getStatus());
            System.out.println(res.getResponse().getContentAsString());
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
    }
    
    private void printRequest(TransferObject o) {
        System.out.println("REQUEST:");
        System.out.println(o.toJson());
    }
    
    private ServerSignatureRequestTransferObject getMockSigRequestObject() {
        byte[] tx = new byte[1024];
        RND.nextBytes(tx);
        int index = 7;
        int[] path = {1,2,3,4,5};
        String refundTx = Base64.getEncoder().encodeToString(tx);

        ServerSignatureRequestTransferObject sigReq = new ServerSignatureRequestTransferObject();
        sigReq.setPartialTx(refundTx);
        sigReq.addIndexAndDerivationPath(index, path);
        
        return sigReq;
    }
    
    
}
