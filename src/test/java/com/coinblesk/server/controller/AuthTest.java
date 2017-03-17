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
import com.coinblesk.server.service.MailService;
import com.coinblesk.server.service.UserAccountService;
import com.coinblesk.server.utilTest.CoinbleskTest;
import com.coinblesk.util.SerializeUtils;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import org.hamcrest.Matchers;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.util.StringUtils;
import org.springframework.web.context.WebApplicationContext;

import java.util.Date;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 *
 * @author draft
 */

public class AuthTest extends CoinbleskTest {
	@Autowired
	private WebApplicationContext webAppContext;

	@Autowired
	private UserAccountService userAccountService;

	@MockBean
	private MailService mailService;

	private static MockMvc mockMvc;

	@Before
	public void setUp() {
		MockitoAnnotations.initMocks(this);
		mockMvc = MockMvcBuilders.webAppContextSetup(webAppContext).apply(springSecurity()).build();
	}

	@Test
	public void testCreateActivate() throws Exception {
		mockMvc.perform(get("/v1/u/a/g")).andExpect(status().is4xxClientError());
		UserAccountTO userAccountTO = new UserAccountTO();
		MvcResult res = mockMvc	.perform(post("/v1/u/c").contentType(MediaType.APPLICATION_JSON)
								.content(SerializeUtils.GSON.toJson(userAccountTO)))
								.andExpect(status().isOk())
								.andReturn();
		UserAccountStatusTO status = SerializeUtils.GSON.fromJson(res.getResponse().getContentAsString(),
				UserAccountStatusTO.class);
		Assert.assertEquals(Type.NO_EMAIL.nr(), status.type().nr());

		userAccountTO.email("test-test.test");
		res = mockMvc	.perform(post("/v1/u/c").contentType(MediaType.APPLICATION_JSON)
						.content(SerializeUtils.GSON.toJson(userAccountTO)))
						.andExpect(status().isOk())
						.andReturn();
		status = SerializeUtils.GSON.fromJson(res.getResponse().getContentAsString(), UserAccountStatusTO.class);
		Assert.assertEquals(Type.INVALID_EMAIL.nr(), status.type().nr());

		userAccountTO.email("test@test.test");
		res = mockMvc	.perform(post("/v1/u/c").contentType(MediaType.APPLICATION_JSON)
						.content(SerializeUtils.GSON.toJson(userAccountTO)))
						.andExpect(status().isOk())
						.andReturn();
		status = SerializeUtils.GSON.fromJson(res.getResponse().getContentAsString(), UserAccountStatusTO.class);
		Assert.assertEquals(Type.PASSWORD_TOO_SHORT.nr(), status.type().nr());

		userAccountTO.password("1234");
		res = mockMvc	.perform(post("/v1/u/c").contentType(MediaType.APPLICATION_JSON)
						.content(SerializeUtils.GSON.toJson(userAccountTO)))
						.andExpect(status().isOk())
						.andReturn();
		status = SerializeUtils.GSON.fromJson(res.getResponse().getContentAsString(), UserAccountStatusTO.class);
		Assert.assertEquals(Type.PASSWORD_TOO_SHORT.nr(), status.type().nr());

		userAccountTO.password("123456");
		res = mockMvc	.perform(post("/v1/u/c").contentType(MediaType.APPLICATION_JSON)
						.content(SerializeUtils.GSON.toJson(userAccountTO)))
						.andExpect(status().isOk())
						.andReturn();
		status = SerializeUtils.GSON.fromJson(res.getResponse().getContentAsString(), UserAccountStatusTO.class);
		Assert.assertTrue(status.isSuccess());
		Mockito.verify(mailService, Mockito.times(1)).sendUserMail(Mockito.anyString(), Mockito.anyString(),
				Mockito.anyString());
		Mockito.verify(mailService, Mockito.times(0)).sendAdminMail(Mockito.anyString(), Mockito.anyString());

		res = mockMvc	.perform(post("/v1/u/c").contentType(MediaType.APPLICATION_JSON)
						.content(SerializeUtils.GSON.toJson(userAccountTO)))
						.andExpect(status().isOk())
						.andReturn();
		status = SerializeUtils.GSON.fromJson(res.getResponse().getContentAsString(), UserAccountStatusTO.class);
		Assert.assertEquals(Type.SUCCESS_BUT_EMAIL_ALREADY_EXISTS_NOT_ACTIVATED.nr(), status.type().nr());
		Mockito.verify(mailService, Mockito.times(2)).sendUserMail(Mockito.anyString(), Mockito.anyString(),
				Mockito.anyString());
		Mockito.verify(mailService, Mockito.times(0)).sendAdminMail(Mockito.anyString(), Mockito.anyString());

		// activate with wrong token sends admin an email
		mockMvc.perform(get("/v1/u/v/test@test.test/blub")).andExpect(status().is5xxServerError());
		Mockito.verify(mailService, Mockito.times(2)).sendUserMail(Mockito.anyString(), Mockito.anyString(),
				Mockito.anyString());
		Mockito.verify(mailService, Mockito.times(1)).sendAdminMail(Mockito.anyString(), Mockito.anyString());

		// get correct token
		String token = userAccountService.getToken("test@test.test");
		Assert.assertNotNull(token);
		mockMvc.perform(get("/v1/u/v/test@test.test/" + token)).andExpect(status().isOk());
		Mockito.verify(mailService, Mockito.times(2)).sendUserMail(Mockito.anyString(), Mockito.anyString(),
				Mockito.anyString());
		Mockito.verify(mailService, Mockito.times(1)).sendAdminMail(Mockito.anyString(), Mockito.anyString());

		mockMvc.perform(get("/v1/u/a/g")).andExpect(status().is4xxClientError());

		// Wrong password fails
		mockMvc.perform(post("/user/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"username\":\"test@test.test\",\"password\":\"12345\"}"))
				.andExpect(status().is4xxClientError());

		// Correct login returns a token
		String authorizationToken = loginAndGetToken("test@test.test", "123456");

		// Token is valid
		Assert.assertTrue(StringUtils.hasText(authorizationToken));
		Assert.assertTrue(authorizationToken.startsWith("Bearer "));
		String jwt = authorizationToken.substring(7, authorizationToken.length());

		// Check claims
		Jws<Claims> claims = Jwts.parser().setSigningKey("bitcoin".getBytes()).parseClaimsJws(jwt);
		Assert.assertEquals(claims.getBody().getSubject(), "test@test.test");
		Assert.assertTrue(claims.getBody().getExpiration().after(new Date()));
		Assert.assertEquals(claims.getBody().get("auth", String.class), "ROLE_USER");

		// Get user profile with valid JWT
		res = mockMvc	.perform(get("/v1/u/a/g").header("Authorization", authorizationToken))
						.andExpect(status().isOk())
						.andReturn();
		UserAccountTO uato = SerializeUtils.GSON.fromJson(res.getResponse().getContentAsString(), UserAccountTO.class);
		Assert.assertEquals("test@test.test", uato.email());
	}

	private String loginAndGetToken(String username, String password) throws Exception {
		return mockMvc.perform(post("/user/login").contentType(MediaType.APPLICATION_JSON).content(
				"{\"username\":\"test@test.test\",\"password\":\"123456\"}"))
						.andExpect(status().isOk())
						.andExpect(header().string("Authorization", Matchers.not(Matchers.isEmptyOrNullString())))
						.andReturn()
						.getResponse()
						.getHeader("Authorization");
	}
}
