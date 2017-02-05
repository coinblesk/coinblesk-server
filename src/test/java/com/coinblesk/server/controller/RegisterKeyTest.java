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

import com.coinblesk.json.v1.KeyTO;
import com.coinblesk.json.v1.Type;
import com.coinblesk.server.utilTest.CoinbleskTest;
import com.coinblesk.util.SerializeUtils;
import com.github.springtestdbunit.annotation.DatabaseSetup;
import com.github.springtestdbunit.annotation.DatabaseTearDown;
import org.bitcoinj.core.ECKey;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 *
 * @author draft
 */
public class RegisterKeyTest extends CoinbleskTest {

    @Autowired
    private WebApplicationContext webAppContext;

    private static MockMvc mockMvc;
    
    @Before
    public void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webAppContext).build();
    }

    @Test
    @DatabaseSetup("/EmptyDatabase.xml")
    @DatabaseTearDown("/EmptyDatabase.xml")
    public void testRegister() throws Exception {
        //no object
        mockMvc.perform(post("/p/x").secure(true)).andExpect(status().is4xxClientError());
        //with object, but no public key
        KeyTO keyTO = new KeyTO();
        keyTO.currentDate(System.currentTimeMillis());
        MvcResult res = mockMvc.perform(post("/p/x").secure(true).contentType(MediaType.APPLICATION_JSON)
                .content(SerializeUtils.GSON.toJson(keyTO))).andExpect(status().isOk()).andReturn();
        KeyTO status = SerializeUtils.GSON.fromJson(res.getResponse().getContentAsString(), KeyTO.class);
        Assert.assertEquals(false, status.isSuccess());
        Assert.assertEquals(Type.INPUT_MISMATCH, status.type());
        Assert.assertNull(status.publicKey());
        //with bogus key
        keyTO = new KeyTO().publicKey("bogus=======".getBytes());
        res = mockMvc.perform(post("/p/x").secure(true).contentType(MediaType.APPLICATION_JSON).content(
                SerializeUtils.GSON.toJson(keyTO))).andExpect(status().isOk()).andReturn();
        status = SerializeUtils.GSON.fromJson(res.getResponse().getContentAsString(), KeyTO.class);
        Assert.assertEquals(false, status.isSuccess());
        Assert.assertEquals(Type.INPUT_MISMATCH, status.type());
        Assert.assertNull(status.publicKey());
        //with good pubilc key
        ECKey ecKeyClient = new ECKey();
        keyTO = new KeyTO().publicKey(ecKeyClient.getPubKey());
        res = mockMvc.perform(post("/p/x").secure(true).contentType(MediaType.APPLICATION_JSON).content(
                SerializeUtils.GSON.toJson(keyTO))).andExpect(status().isOk()).andReturn();
        status = SerializeUtils.GSON.fromJson(res.getResponse().getContentAsString(), KeyTO.class);
        Assert.assertEquals(true, status.isSuccess());
        Assert.assertNotNull(status.publicKey());
    }
}
