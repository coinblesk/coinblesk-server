package ch.uzh.csg.coinblesk.server.controller;

import static org.junit.Assert.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.bitcoinj.core.ECKey;
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
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.coinblesk.bitcoin.TimeLockedAddress;
import com.coinblesk.json.BaseTO;
import com.coinblesk.json.KeyTO;
import com.coinblesk.json.TimeLockedAddressTO;
import com.coinblesk.json.Type;
import com.coinblesk.util.SerializeUtils;
import com.github.springtestdbunit.DbUnitTestExecutionListener;
import com.github.springtestdbunit.annotation.DatabaseSetup;
import com.github.springtestdbunit.annotation.DatabaseTearDown;

import ch.uzh.csg.coinblesk.server.config.BeanConfig;
import ch.uzh.csg.coinblesk.server.config.SecurityConfig;
import ch.uzh.csg.coinblesk.server.controller.KeyTest.KeyTestUtil;
import ch.uzh.csg.coinblesk.server.entity.Keys;
import ch.uzh.csg.coinblesk.server.service.KeyService;
import ch.uzh.csg.coinblesk.server.utilTest.TestBean;

/**
 * 
 * @author Andreas Albrecht
 */
@RunWith(SpringJUnit4ClassRunner.class)
@TestExecutionListeners({
	DependencyInjectionTestExecutionListener.class, 
	TransactionalTestExecutionListener.class,
    DbUnitTestExecutionListener.class})
@ContextConfiguration(classes = {
		TestBean.class, 
		BeanConfig.class, 
		SecurityConfig.class})
@WebAppConfiguration
public class PaymentControllerTest {
	
	private static final String URL_CREATE_TIME_LOCKED_ADDRESS = "/payment/createTimeLockedAddress";
	
	@Autowired
    private WebApplicationContext webAppContext;

    @Autowired
    private FilterChainProxy springSecurityFilterChain;
    
    @Autowired
    private KeyService keyService;
    
    private static MockMvc mockMvc;
    
    @Before
    public void setUp() {
         mockMvc = MockMvcBuilders.webAppContextSetup(webAppContext).addFilter(springSecurityFilterChain).build();   
    }
    
	@Test
	public void testCreateTimeLockedAddress_NoContent() throws Exception {
		mockMvc
			.perform(post(URL_CREATE_TIME_LOCKED_ADDRESS).secure(true))
			.andExpect(status().is4xxClientError());
	}
    
    @Test
    public void testCreateTimeLockedAddress_NewAddress_ClientUnknown() throws Exception {
        ECKey clientKey = new ECKey();
        assertNull( keyService.getByClientPublicKey(clientKey.getPubKey()) );
        
        KeyTO keyTO = new KeyTO().publicKey(clientKey.getPubKey());
        String jsonKeyTO = SerializeUtils.GSON.toJson(keyTO);
        
        MvcResult res = mockMvc
        					.perform(post(URL_CREATE_TIME_LOCKED_ADDRESS).secure(true).contentType(MediaType.APPLICATION_JSON).content(jsonKeyTO))
        					.andExpect(status().isOk())
        					.andReturn();
        TimeLockedAddressTO responseTO = SerializeUtils.GSON.fromJson(res.getResponse().getContentAsString(), TimeLockedAddressTO.class);
        assertTrue(responseTO.isSuccess());
    	TimeLockedAddress addressResponse = responseTO.timeLockedAddress();
    	assertNotNull(addressResponse);
    	assertNotNull(addressResponse.getAddressHash());
    	assertNotNull(addressResponse.getUserPubKey());
    	assertNotNull(addressResponse.getServicePubKey());
    	
    	assertArrayEquals(addressResponse.getUserPubKey(), clientKey.getPubKey());
    	assertTrue(addressResponse.getLockTime() > 0);
    	
    	// check DB
    	Keys keys = keyService.getByClientPublicKey(clientKey.getPubKey());
    	assertNotNull(keys);
    	assertArrayEquals(keys.clientPublicKey(), clientKey.getPubKey());
    	assertNotNull(keys.serverPrivateKey());
    	assertNotNull(keys.serverPublicKey());
    	assertTrue(keys.addresses().size() == 1);
    	assertArrayEquals(keys.addresses().iterator().next().getAddressHash(), addressResponse.getAddressHash());
    	
    	// check sig ok
    	assertServerSig(responseTO, keys);
    }
    
    @Test
    @DatabaseSetup("classpath:DbUnitFiles/keys.xml")
    @DatabaseTearDown("classpath:DbUnitFiles/emptyAddresses.xml")
    @DatabaseTearDown("classpath:DbUnitFiles/emptyKeys.xml")
    public void testCreateTimeLockedAddress_NewAddress_ClientKnown() throws Exception {
        ECKey clientKey = KeyTestUtil.ALICE_CLIENT;
		ECKey serverKey = KeyTestUtil.ALICE_SERVER; // key is already stored in DB
        
        KeyTO keyTO = new KeyTO().publicKey(clientKey.getPubKey());
        String jsonKeyTO = SerializeUtils.GSON.toJson(keyTO);
        
        MvcResult res = mockMvc
        					.perform(post(URL_CREATE_TIME_LOCKED_ADDRESS).secure(true).contentType(MediaType.APPLICATION_JSON).content(jsonKeyTO))
        					.andExpect(status().isOk())
        					.andReturn();
        TimeLockedAddressTO responseTO = SerializeUtils.GSON.fromJson(res.getResponse().getContentAsString(), TimeLockedAddressTO.class);
        assertTrue(responseTO.isSuccess());
    	TimeLockedAddress addressResponse = responseTO.timeLockedAddress();
    	assertNotNull(addressResponse);
    	assertNotNull(addressResponse.getAddressHash());
    	assertNotNull(addressResponse.getUserPubKey());
    	assertNotNull(addressResponse.getServicePubKey());
    	assertArrayEquals(addressResponse.getUserPubKey(), clientKey.getPubKey());
    	assertArrayEquals(addressResponse.getServicePubKey(), serverKey.getPubKey());
    	assertTrue(addressResponse.getLockTime() > 0);
    	
    	// check sig ok
    	assertVerifySig(responseTO, serverKey);
    }
    
    @Test
    public void testCreateTimeLockedAddress_WrongECKey() throws Exception {
        KeyTO keyTO = new KeyTO().publicKey("helloworld".getBytes());
        String jsonKeyTO = SerializeUtils.GSON.toJson(keyTO);
        
        MvcResult res = mockMvc
        					.perform(post(URL_CREATE_TIME_LOCKED_ADDRESS).secure(true).contentType(MediaType.APPLICATION_JSON).content(jsonKeyTO))
        					.andExpect(status().isOk())
        					.andReturn();
        TimeLockedAddressTO response = SerializeUtils.GSON.fromJson(res.getResponse().getContentAsString(), TimeLockedAddressTO.class);
        assertFalse(response.isSuccess());
        assertEquals(response.type(), Type.SERVER_ERROR);
    	assertNull(response.timeLockedAddress());
    }
    
    @Test
    public void testCreateTimeLockedAddress_EmptyRequest() throws Exception {
        KeyTO keyTO = new KeyTO();
        String jsonKeyTO = SerializeUtils.GSON.toJson(keyTO);
        
        MvcResult res = mockMvc
        					.perform(post(URL_CREATE_TIME_LOCKED_ADDRESS).secure(true).contentType(MediaType.APPLICATION_JSON).content(jsonKeyTO))
        					.andExpect(status().isOk())
        					.andReturn();
        TimeLockedAddressTO response = SerializeUtils.GSON.fromJson(res.getResponse().getContentAsString(), TimeLockedAddressTO.class);
        assertFalse(response.isSuccess());
        assertEquals(response.type(), Type.SERVER_ERROR);
    	assertNull(response.timeLockedAddress());
    }

	private static <K extends BaseTO<?>> boolean assertServerSig(K k, Keys keys) {
		ECKey eckey = ECKey.fromPrivateAndPrecalculatedPublic(keys.serverPrivateKey(), keys.serverPublicKey());
		return assertVerifySig(k, eckey);
	}

	private static <K extends BaseTO<?>> boolean assertVerifySig(K k, ECKey key) {
		boolean result = SerializeUtils.verifySig(k, key);
		assertTrue(result);
		return result;
	}
}
