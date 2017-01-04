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

import com.coinblesk.json.v1.Type;
import com.coinblesk.json.v1.UserAccountStatusTO;
import com.coinblesk.json.v1.UserAccountTO;
import com.coinblesk.server.config.AdminEmail;
import com.coinblesk.server.service.UserAccountService;
import com.coinblesk.util.SerializeUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import javax.servlet.http.HttpSession;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 *
 * @author draft
 */

@SpringBootTest
@RunWith(SpringRunner.class)
public class AuthTest {

    @Autowired
    private WebApplicationContext webAppContext;

    @Autowired
    private UserAccountService userAccountService;
    
    @Autowired
    private AdminEmail adminEmail;

    private static MockMvc mockMvc;

    @Before
    public void setUp() {
         mockMvc = MockMvcBuilders
                 .webAppContextSetup(webAppContext)
                 .apply(springSecurity())
                 .build();
    }
    
    @Test
    public void testCreateActivate() throws Exception {
	mockMvc.perform(get("/v1/u/a/g").secure(true)).andExpect(status().is3xxRedirection());
        UserAccountTO userAccountTO = new UserAccountTO();
        MvcResult res = mockMvc.perform(post("/v1/u/c").secure(true).contentType(MediaType.APPLICATION_JSON).content(SerializeUtils.GSON.toJson(userAccountTO))).andExpect(status().isOk()).andReturn();
        UserAccountStatusTO status = SerializeUtils.GSON.fromJson(res.getResponse().getContentAsString(), UserAccountStatusTO.class);
        Assert.assertEquals(Type.NO_EMAIL.nr(), status.type().nr());
        
        userAccountTO.email("test-test.test");
        res = mockMvc.perform(post("/v1/u/c").secure(true).contentType(MediaType.APPLICATION_JSON).content(SerializeUtils.GSON.toJson(userAccountTO))).andExpect(status().isOk()).andReturn();
        status = SerializeUtils.GSON.fromJson(res.getResponse().getContentAsString(), UserAccountStatusTO.class);
        Assert.assertEquals(Type.INVALID_EMAIL.nr(), status.type().nr());
        
        userAccountTO.email("test@test.test");
        res = mockMvc.perform(post("/v1/u/c").secure(true).contentType(MediaType.APPLICATION_JSON).content(SerializeUtils.GSON.toJson(userAccountTO))).andExpect(status().isOk()).andReturn();
        status = SerializeUtils.GSON.fromJson(res.getResponse().getContentAsString(), UserAccountStatusTO.class);
        Assert.assertEquals(Type.PASSWORD_TOO_SHORT.nr(), status.type().nr());
        
        userAccountTO.password("1234");
        res = mockMvc.perform(post("/v1/u/c").secure(true).contentType(MediaType.APPLICATION_JSON).content(SerializeUtils.GSON.toJson(userAccountTO))).andExpect(status().isOk()).andReturn();
        status = SerializeUtils.GSON.fromJson(res.getResponse().getContentAsString(), UserAccountStatusTO.class);
        Assert.assertEquals(Type.PASSWORD_TOO_SHORT.nr(), status.type().nr());
        
        userAccountTO.password("123456");
        res = mockMvc.perform(post("/v1/u/c").secure(true).contentType(MediaType.APPLICATION_JSON).content(SerializeUtils.GSON.toJson(userAccountTO))).andExpect(status().isOk()).andReturn();
        status = SerializeUtils.GSON.fromJson(res.getResponse().getContentAsString(), UserAccountStatusTO.class);
        Assert.assertTrue(status.isSuccess());
        Assert.assertEquals(1, adminEmail.sentEmails());
        
        res = mockMvc.perform(post("/v1/u/c").secure(true).contentType(MediaType.APPLICATION_JSON).content(SerializeUtils.GSON.toJson(userAccountTO))).andExpect(status().isOk()).andReturn();
        status = SerializeUtils.GSON.fromJson(res.getResponse().getContentAsString(), UserAccountStatusTO.class);
        Assert.assertEquals(Type.SUCCESS_BUT_EMAIL_ALREADY_EXISTS_NOT_ACTIVATED.nr(), status.type().nr());
        Assert.assertEquals(2, adminEmail.sentEmails());
        
        //activate
        mockMvc.perform(get("/v1/u/v/test@test.test/blub").secure(true)).andExpect(status().is5xxServerError());
        Assert.assertEquals(3, adminEmail.sentEmails());
        
        //get correct token
        String token = userAccountService.getToken("test@test.test");
        Assert.assertNotNull(token);
        mockMvc.perform(get("/v1/u/v/test@test.test/"+token).secure(true)).andExpect(status().isOk());
        Assert.assertEquals(3, adminEmail.sentEmails());
        
        mockMvc.perform(get("/v1/u/a/g").secure(true).with(csrf())).andExpect(status().is3xxRedirection());
        
        loginAndGetSessionFail("test@test.test", "12345");
        
        HttpSession session = loginAndGetSession("test@test.test", "123456");
        res = mockMvc.perform(get("/v1/u/a/g").secure(true).with(csrf()).session((MockHttpSession) session)).andExpect(status().isOk()).andReturn();
        UserAccountTO uato = SerializeUtils.GSON.fromJson(res.getResponse().getContentAsString(), UserAccountTO.class);
        Assert.assertEquals("test@test.test", uato.email());
    }
    
    private HttpSession loginAndGetSession(String username, String password) throws Exception {
	HttpSession session = mockMvc.perform(post("/login").secure(true)
                .param("username", username)
                .param("password", password)
                .with(csrf()))
                .andExpect(status().isOk())
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
                .andExpect(status().isUnauthorized())
		.andReturn()
		.getRequest()
		.getSession();
        return session;
    }
}
