/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.uzh.csg.coinblesk.server.controller;

import ch.uzh.csg.coinblesk.server.config.BeanConfig;
import ch.uzh.csg.coinblesk.server.config.SecurityConfig;
import ch.uzh.csg.coinblesk.server.utilTest.TestBean;
import com.coinblesk.json.KeyTO;
import com.coinblesk.json.Type;
import com.coinblesk.util.SerializeUtils;
import com.github.springtestdbunit.DbUnitTestExecutionListener;
import org.bitcoinj.core.ECKey;
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

/**
 *
 * @author draft
 */
@RunWith(SpringJUnit4ClassRunner.class)
@TestExecutionListeners({DependencyInjectionTestExecutionListener.class, TransactionalTestExecutionListener.class,
    DbUnitTestExecutionListener.class})
@ContextConfiguration(classes = {TestBean.class, BeanConfig.class, SecurityConfig.class})
@WebAppConfiguration
public class KeyTest {
    
    @Autowired
    private WebApplicationContext webAppContext;

    @Autowired
    private FilterChainProxy springSecurityFilterChain;
    
    private static MockMvc mockMvc;
    
    @Before
    public void setUp() {
         mockMvc = MockMvcBuilders.webAppContextSetup(webAppContext).addFilter(springSecurityFilterChain).build();   
    }
    
    @Test
    public void testRegister() throws Exception {
        //no object
        mockMvc.perform(post("/p/x").secure(true)).andExpect(status().is4xxClientError());
        //with object, but no public key
        KeyTO keyTO = new KeyTO();
        MvcResult res = mockMvc.perform(post("/p/x").secure(true).contentType(MediaType.APPLICATION_JSON).content(SerializeUtils.GSON.toJson(keyTO))).andExpect(status().isOk()).andReturn();
        KeyTO status = SerializeUtils.GSON.fromJson(res.getResponse().getContentAsString(), KeyTO.class);
        Assert.assertEquals(false, status.isSuccess());
        Assert.assertEquals(Type.SERVER_ERROR, status.type());
        Assert.assertNull(status.publicKey());
        //with bogus key
        keyTO = new KeyTO().publicKey("bogus=======".getBytes());
        res = mockMvc.perform(post("/p/x").secure(true).contentType(MediaType.APPLICATION_JSON).content(SerializeUtils.GSON.toJson(keyTO))).andExpect(status().isOk()).andReturn();
        status = SerializeUtils.GSON.fromJson(res.getResponse().getContentAsString(), KeyTO.class);
        Assert.assertEquals(false, status.isSuccess());
        Assert.assertEquals(Type.SERVER_ERROR, status.type());
        Assert.assertNull(status.publicKey());
        //with good pubilc key
        ECKey ecKeyClient = new ECKey();
        keyTO = new KeyTO().publicKey(ecKeyClient.getPubKey());
        res = mockMvc.perform(post("/p/x").secure(true).contentType(MediaType.APPLICATION_JSON).content(SerializeUtils.GSON.toJson(keyTO))).andExpect(status().isOk()).andReturn();
        status = SerializeUtils.GSON.fromJson(res.getResponse().getContentAsString(), KeyTO.class);
        Assert.assertEquals(true, status.isSuccess());
        Assert.assertNotNull(status.publicKey());
    }
}
