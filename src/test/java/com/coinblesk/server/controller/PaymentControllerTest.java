/*
 * Copyright 2016 The Coinblesk team and the CSG Group at University of Zurich
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.coinblesk.server.controller;

import static org.junit.Assert.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.Utils;
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
import com.coinblesk.json.TimeLockedAddressTO;
import com.coinblesk.json.Type;
import com.coinblesk.server.config.BeanConfig;
import com.coinblesk.server.config.SecurityConfig;
import com.coinblesk.server.entity.Keys;
import com.coinblesk.server.service.KeyService;
import com.coinblesk.server.utilTest.KeyTestUtil;
import com.coinblesk.server.utilTest.TestBean;
import com.coinblesk.util.SerializeUtils;
import com.github.springtestdbunit.DbUnitTestExecutionListener;
import com.github.springtestdbunit.annotation.DatabaseSetup;
import com.github.springtestdbunit.annotation.DatabaseTearDown;


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
	
	public static final String URL_CREATE_TIME_LOCKED_ADDRESS = "/v3/payment/createTimeLockedAddress";
	
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
	public void testCreateTimeLockedAddress_EmptyRequest() throws Exception {
	    TimeLockedAddressTO keyTO = new TimeLockedAddressTO();
	    String jsonTO = SerializeUtils.GSON.toJson(keyTO);
	    TimeLockedAddressTO response = performTimeLockedAddressRequest(jsonTO);
	    assertFalse(response.isSuccess());
	    assertEquals(response.type(), Type.INPUT_MISMATCH);
		assertNull(response.timeLockedAddress());
	}
	
	@Test
	public void testCreateTimeLockedAddress_NoPublicKey() throws Exception {
		ECKey clientKey = new ECKey();
	    TimeLockedAddressTO requestTO = new TimeLockedAddressTO()
	    		.currentDate(System.currentTimeMillis());
	    SerializeUtils.signJSON(requestTO, clientKey);
	    String jsonTO = SerializeUtils.GSON.toJson(requestTO);
	    TimeLockedAddressTO response = performTimeLockedAddressRequest(jsonTO);
	    assertFalse(response.isSuccess());
	    assertEquals(response.type(), Type.SERVER_ERROR);
		assertNull(response.timeLockedAddress());
	}

	@Test
	public void testCreateTimeLockedAddress_NoSignature() throws Exception {
        ECKey clientKey = new ECKey();
        TimeLockedAddressTO requestTO = createSignedTimeLockedAddressTO(clientKey);
        requestTO.messageSig(null); // -> Do not sign
        String jsonTO = SerializeUtils.GSON.toJson(requestTO);
        TimeLockedAddressTO responseTO = performTimeLockedAddressRequest(jsonTO);
        assertFalse(responseTO.isSuccess());
        assertEquals(responseTO.type(), Type.INPUT_MISMATCH);
	}
	
	@Test
	public void testCreateTimeLockedAddress_WrongSignature() throws Exception {
        ECKey clientKey = new ECKey();
        ECKey wrongKey = new ECKey();
        TimeLockedAddressTO requestTO = new TimeLockedAddressTO()
        		.currentDate(Utils.currentTimeMillis())
        		.clientPublicKey(clientKey.getPubKey());
        SerializeUtils.signJSON(requestTO, wrongKey);
        String jsonTO = SerializeUtils.GSON.toJson(requestTO);
        TimeLockedAddressTO responseTO = performTimeLockedAddressRequest(jsonTO);
        assertFalse(responseTO.isSuccess());
        assertEquals(responseTO.type(), Type.JSON_SIGNATURE_ERROR);
	}
	
	@Test
	public void testCreateTimeLockedAddress_WrongECKey() throws Exception {
		TimeLockedAddressTO requestTO = new TimeLockedAddressTO()
				.clientPublicKey("helloworld".getBytes())
				.currentDate(System.currentTimeMillis());
		SerializeUtils.signJSON(requestTO, new ECKey());
	    String jsonTO = SerializeUtils.GSON.toJson(requestTO);
	    TimeLockedAddressTO response = performTimeLockedAddressRequest(jsonTO);
	    assertFalse(response.isSuccess());
	    assertEquals(response.type(), Type.SERVER_ERROR);
		assertNull(response.timeLockedAddress());
	}

	@Test
    public void testCreateTimeLockedAddress_NewAddress_ClientUnknown() throws Exception {
        ECKey clientKey = new ECKey();
        assertNull( keyService.getByClientPublicKey(clientKey.getPubKey()) ); // not known yet
        TimeLockedAddressTO requestTO = createSignedTimeLockedAddressTO(clientKey);
        String jsonTO = SerializeUtils.GSON.toJson(requestTO);
        TimeLockedAddressTO responseTO = performTimeLockedAddressRequest(jsonTO);
        
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
        
        TimeLockedAddressTO requestTO = createSignedTimeLockedAddressTO(clientKey);
        String jsonTO = SerializeUtils.GSON.toJson(requestTO);
        TimeLockedAddressTO responseTO = performTimeLockedAddressRequest(jsonTO);
        
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
    
    private TimeLockedAddressTO performTimeLockedAddressRequest(String jsonTO) throws Exception {
    	return performRequest(URL_CREATE_TIME_LOCKED_ADDRESS, jsonTO, TimeLockedAddressTO.class);
    }
    
    private <T> T performRequest(String url, String jsonTO, Class<T> responseClass) throws Exception {
    	final MvcResult res = mockMvc
				.perform(
						post(url)
						.secure(true)
						.contentType(MediaType.APPLICATION_JSON)
						.content(jsonTO))
				.andExpect(status().isOk())
				.andReturn();
    	final T responseTO = SerializeUtils.GSON.fromJson(
    			res.getResponse().getContentAsString(), responseClass);
		return responseTO;
	}

	private TimeLockedAddressTO createSignedTimeLockedAddressTO(ECKey clientKey) {
		TimeLockedAddressTO requestTO = new TimeLockedAddressTO()
	    		.currentDate(Utils.currentTimeMillis())
	    		.clientPublicKey(clientKey.getPubKey());
	    SerializeUtils.signJSON(requestTO, clientKey);
	    return requestTO;
	}

	private static <K extends BaseTO<?>> boolean assertServerSig(K k, Keys keys) {
		ECKey eckey = ECKey.fromPrivateAndPrecalculatedPublic(keys.serverPrivateKey(), keys.serverPublicKey());
		return assertVerifySig(k, eckey);
	}

	private static <K extends BaseTO<?>> boolean assertVerifySig(K k, ECKey key) {
		boolean result = SerializeUtils.verifyJSONSignature(k, key);
		assertTrue(result);
		return result;
	}
}
