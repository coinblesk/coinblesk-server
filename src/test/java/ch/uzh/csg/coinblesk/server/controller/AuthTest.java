/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.uzh.csg.coinblesk.server.controller;

import ch.uzh.csg.coinblesk.server.config.AdminEmail;
import ch.uzh.csg.coinblesk.server.config.BeanConfig;
import ch.uzh.csg.coinblesk.server.config.SecurityConfig;
import ch.uzh.csg.coinblesk.server.service.UserAccountService;
import com.coinblesk.json.UserAccountStatusTO;
import com.coinblesk.json.UserAccountTO;
import com.github.springtestdbunit.DbUnitTestExecutionListener;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import javax.servlet.http.HttpSession;
import org.junit.Assert;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
import ch.uzh.csg.coinblesk.server.utilTest.*;
import org.springframework.mock.web.MockHttpSession;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;

/**
 *
 * @author draft
 */

@RunWith(SpringJUnit4ClassRunner.class)
@TestExecutionListeners({DependencyInjectionTestExecutionListener.class, TransactionalTestExecutionListener.class,
    DbUnitTestExecutionListener.class})
@ContextConfiguration(classes = {TestBean.class, BeanConfig.class, SecurityConfig.class})
@WebAppConfiguration
public class AuthTest {

    @Autowired
    private WebApplicationContext webAppContext;

    @Autowired
    private FilterChainProxy springSecurityFilterChain;
    
    @Autowired
    private UserAccountService userAccountService;
    
    @Autowired
    private AdminEmail adminEmail;

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
    public void testCreateActivate() throws Exception {
	mockMvc.perform(get("/u/a/g").secure(true)).andExpect(status().is3xxRedirection());
        UserAccountTO userAccountTO = new UserAccountTO();
        MvcResult res = mockMvc.perform(post("/u/c").secure(true).contentType(MediaType.APPLICATION_JSON).content(GSON.toJson(userAccountTO))).andExpect(status().isOk()).andReturn();
        UserAccountStatusTO status = GSON.fromJson(res.getResponse().getContentAsString(), UserAccountStatusTO.class);
        Assert.assertEquals(UserAccountStatusTO.Reason.NO_EMAIL.nr(), status.reason().nr());
        
        userAccountTO.email("test-test.test");
        res = mockMvc.perform(post("/u/c").secure(true).contentType(MediaType.APPLICATION_JSON).content(GSON.toJson(userAccountTO))).andExpect(status().isOk()).andReturn();
        status = GSON.fromJson(res.getResponse().getContentAsString(), UserAccountStatusTO.class);
        Assert.assertEquals(UserAccountStatusTO.Reason.INVALID_EMAIL.nr(), status.reason().nr());
        
        userAccountTO.email("test@test.test");
        res = mockMvc.perform(post("/u/c").secure(true).contentType(MediaType.APPLICATION_JSON).content(GSON.toJson(userAccountTO))).andExpect(status().isOk()).andReturn();
        status = GSON.fromJson(res.getResponse().getContentAsString(), UserAccountStatusTO.class);
        Assert.assertEquals(UserAccountStatusTO.Reason.PASSWORD_TOO_SHORT.nr(), status.reason().nr());
        
        userAccountTO.password("1234");
        res = mockMvc.perform(post("/u/c").secure(true).contentType(MediaType.APPLICATION_JSON).content(GSON.toJson(userAccountTO))).andExpect(status().isOk()).andReturn();
        status = GSON.fromJson(res.getResponse().getContentAsString(), UserAccountStatusTO.class);
        Assert.assertEquals(UserAccountStatusTO.Reason.PASSWORD_TOO_SHORT.nr(), status.reason().nr());
        
        userAccountTO.password("123456");
        res = mockMvc.perform(post("/u/c").secure(true).contentType(MediaType.APPLICATION_JSON).content(GSON.toJson(userAccountTO))).andExpect(status().isOk()).andReturn();
        status = GSON.fromJson(res.getResponse().getContentAsString(), UserAccountStatusTO.class);
        Assert.assertTrue(status.isSuccess());
        Assert.assertEquals(1, adminEmail.sentEmails());
        
        res = mockMvc.perform(post("/u/c").secure(true).contentType(MediaType.APPLICATION_JSON).content(GSON.toJson(userAccountTO))).andExpect(status().isOk()).andReturn();
        status = GSON.fromJson(res.getResponse().getContentAsString(), UserAccountStatusTO.class);
        Assert.assertEquals(UserAccountStatusTO.Reason.EMAIL_ALREADY_EXISTS_NOT_ACTIVATED.nr(), status.reason().nr());
        Assert.assertEquals(2, adminEmail.sentEmails());
        
        //activate
        mockMvc.perform(patch("/u/v/test@test.test/blub").secure(true)).andExpect(status().is5xxServerError());
        Assert.assertEquals(3, adminEmail.sentEmails());
        
        //get correct token
        String token = userAccountService.getToken("test@test.test");
        Assert.assertNotNull(token);
        mockMvc.perform(patch("/u/v/test@test.test/"+token).secure(true)).andExpect(status().isOk());
        Assert.assertEquals(3, adminEmail.sentEmails());
        
        mockMvc.perform(get("/u/a/g").secure(true).with(csrf())).andExpect(status().is3xxRedirection());
        
        loginAndGetSessionFail("test@test.test", "12345");
        
        HttpSession session = loginAndGetSession("test@test.test", "123456");
        res = mockMvc.perform(get("/u/a/g").secure(true).with(csrf()).session((MockHttpSession) session)).andExpect(status().isOk()).andReturn();
        UserAccountTO uato = GSON.fromJson(res.getResponse().getContentAsString(), UserAccountTO.class);
        Assert.assertEquals("test@test.test", uato.email());
    }
    
    private HttpSession loginAndGetSession(String username, String password) throws Exception {
	HttpSession session = mockMvc.perform(post("/login").secure(true)
                .param("username", username)
                .param("password", password)
                .with(csrf()))
                .andExpect(header().string("Location", "/login?success"))
		.andReturn()
		.getRequest()
		.getSession();
        return session;
    }
    
    private HttpSession loginAndGetSessionFail(String username, String password) throws Exception {
	HttpSession session = mockMvc.perform(post("/login").secure(true)
                .param("username", username)
                .param("password", password)
                .with(csrf()))
                .andExpect(header().string("Location", "/login?error"))
		.andReturn()
		.getRequest()
		.getSession();
        return session;
    }
}
