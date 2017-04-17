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

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;

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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.util.StringUtils;
import org.springframework.web.context.WebApplicationContext;

import com.coinblesk.json.v1.Type;
import com.coinblesk.json.v1.UserAccountStatusTO;
import com.coinblesk.json.v1.UserAccountTO;
import com.coinblesk.server.config.AppConfig;
import com.coinblesk.server.dao.UserAccountRepository;
import com.coinblesk.server.entity.UserAccount;
import com.coinblesk.server.service.MailService;
import com.coinblesk.server.service.UserAccountService;
import com.coinblesk.server.utilTest.CoinbleskTest;
import com.coinblesk.util.SerializeUtils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;

/**
 * @author Thomas Bocek
 * @author Sebastian Stephan
 */

public class AuthTest extends CoinbleskTest {
	private static MockMvc mockMvc;
	@Autowired
	private WebApplicationContext webAppContext;
	@Autowired
	private UserAccountService userAccountService;
	@Autowired
	private UserAccountRepository userAccountRepository;
	@Autowired
	private AppConfig appConfig;
	@MockBean
	private MailService mailService;

	@Before
	public void setUp() {
		MockitoAnnotations.initMocks(this);
		mockMvc = MockMvcBuilders.webAppContextSetup(webAppContext).apply(springSecurity()).build();
	}

	@Test
	public void createFailsWithNoContent() throws Exception {
		mockMvc.perform(get("/auth/common/user-account")).andExpect(status().is4xxClientError());
	}

	@Test
	public void createFailsWithNoEmail() throws Exception {
		UserAccountTO userAccountTO = new UserAccountTO();
		MvcResult res = mockMvc.perform(post("/user-account/create").contentType(MediaType.APPLICATION_JSON).content
			(SerializeUtils.GSON.toJson(userAccountTO))).andExpect(status().isOk()).andReturn();
		UserAccountStatusTO status = SerializeUtils.GSON.fromJson(res.getResponse().getContentAsString(),
			UserAccountStatusTO.class);
		Assert.assertEquals(Type.NO_EMAIL.nr(), status.type().nr());
	}

	@Test
	public void createFailsWithNoPassword() throws Exception {
		UserAccountTO userAccountTO = new UserAccountTO();
		userAccountTO.email("test@test.test");
		MvcResult res = mockMvc.perform(post("/user-account/create").contentType(MediaType.APPLICATION_JSON).content
			(SerializeUtils.GSON.toJson(userAccountTO))).andExpect(status().isOk()).andReturn();
		UserAccountStatusTO status = SerializeUtils.GSON.fromJson(res.getResponse().getContentAsString(),
			UserAccountStatusTO.class);
		Assert.assertEquals(Type.PASSWORD_TOO_SHORT.nr(), status.type().nr());
	}

	@Test
	public void createFailsWithInvalidEmail() throws Exception {
		UserAccountStatusTO status = createUser("test-test.test", "12345678");
		Assert.assertEquals(Type.INVALID_EMAIL.nr(), status.type().nr());
	}

	@Test
	public void createFailsWithPasswordTooShort() throws Exception {
		UserAccountStatusTO status = createUser("test@test.test", "short");
		Assert.assertEquals(Type.PASSWORD_TOO_SHORT.nr(), status.type().nr());
	}

	@Test
	public void createSendsEmailToUser() throws Exception {
		UserAccountStatusTO status = createUser("test@test.test", "12345678");
		Assert.assertTrue(status.isSuccess());
		Mockito.verify(mailService, Mockito.times(1)).sendUserMail(Mockito.matches("test@test.test"), Mockito.contains
			("Activation"), Mockito.contains("click here to activate"));
	}

	@Test
	public void createSendsActivationEmailAgainWhenRegisteringWithSameEmail() throws Exception {
		UserAccountStatusTO status = createUser("test@test.test", "12345678");
		Assert.assertEquals(Type.SUCCESS, status.type());

		UserAccountStatusTO status2 = createUser("test@test.test", "12345678");
		Assert.assertEquals(Type.SUCCESS_BUT_EMAIL_ALREADY_EXISTS_NOT_ACTIVATED, status2.type());

		Mockito.verify(mailService, Mockito.times(2)).sendUserMail(Mockito.matches("test@test.test"), Mockito.contains
			("Activation"), Mockito.contains("click here to activate"));
	}

	@Test
	public void activatingWithWrongTokenSendsAdminEmail() throws Exception {
		createUser("test@test.test", "12345678");

		mockMvc.perform(get("/user-account/verify/test@test.test/wroohoong")).andExpect(status().is5xxServerError());
		Mockito.verify(mailService, Mockito.times(1)).sendAdminMail(Mockito.anyString(), Mockito.contains("Someone " +
			"tried a link with an invalid token"));
	}

	@Test
	public void activateWithCorrectTokenSucceeds() throws Exception {
		createUser("test@test.test", "12345678");
		String token = userAccountService.getByEmail("test@test.test").getEmailToken();
		Assert.assertNotNull(token);
		mockMvc.perform(get("/user-account/verify/test@test.test/" + token)).andExpect(status().isOk());
	}

	@Test
	public void loginWithWrongPasswordFails() throws Exception {
		final String mail = "log_me_in@valid-email.test";
		createUser(mail, "12345678");
		activateUser(mail);
		loginUser(mail, "1234wroooohng5678").andExpect(status().is4xxClientError());
	}

	@Test
	public void loginReturnsValidTokenInHeader() throws Exception {
		final String mail = "log_me_in@valid-email.test";
		final String password = "lsdj=231lkjXsdlkj";
		createUser(mail, password);
		activateUser(mail);

		// Correct login returns a token
		String authorizationToken = loginUser(mail, password).andExpect(status().isOk()).andExpect(header().string
			("Authorization", Matchers.not(Matchers.isEmptyOrNullString()))).andReturn().getResponse().getHeader
			("Authorization");

		// Token is valid
		Assert.assertTrue(StringUtils.hasText(authorizationToken));
		Assert.assertTrue(authorizationToken.startsWith("Bearer "));
		String jwt = authorizationToken.substring(7, authorizationToken.length());

		// Check claims
		String serverSigningKey = appConfig.getJwtSecret();
		Jws<Claims> claims = Jwts.parser().setSigningKey(serverSigningKey.getBytes()).parseClaimsJws(jwt);
		Assert.assertEquals(claims.getBody().getSubject(), mail);
		Assert.assertTrue(claims.getBody().getExpiration().after(new Date()));
		Assert.assertEquals(claims.getBody().get("auth", String.class), "ROLE_USER");
	}

	@Test
	public void getProfileFailsWithoutJWT() throws Exception {
		mockMvc.perform(get("/auth/common/user-account")).andExpect(status().is4xxClientError());
	}

	@Test
	public void getProfileSucceedsWithValidToken() throws Exception {
		final String mail = "log_me_in@valid-email.test";
		final String password = "lsdj=231lkjXsdlkj";
		createUser(mail, password);
		activateUser(mail);

		String jwt = Jwts.builder().setSubject(mail).claim("auth", "ROLE_USER").signWith(SignatureAlgorithm.HS256,
			appConfig.getJwtSecret().getBytes()).setExpiration(Date.from(Instant.now().plus(Duration.ofHours(1))))
			.compact();

		MvcResult res = mockMvc.perform(get("/auth/common/user-account").header("Authorization", "Bearer " + jwt)).andExpect
			(status().isOk()).andReturn();
		UserAccountTO userAccountTO = SerializeUtils.GSON.fromJson(res.getResponse().getContentAsString(),
			UserAccountTO.class);
		Assert.assertEquals(mail, userAccountTO.email());
	}

	private UserAccountStatusTO createUser(String username, String password) throws Exception {
		UserAccountTO userAccountTO = new UserAccountTO();
		userAccountTO.email(username);
		userAccountTO.password(password);
		MvcResult res = mockMvc.perform(post("/user-account/create").contentType(MediaType.APPLICATION_JSON).content
			(SerializeUtils.GSON.toJson(userAccountTO))).andExpect(status().isOk()).andReturn();
		return SerializeUtils.GSON.fromJson(res.getResponse().getContentAsString(), UserAccountStatusTO.class);
	}

	private void activateUser(String username) {
		UserAccount account = userAccountRepository.findByEmail(username);
		account.setEmailToken(null);
		userAccountRepository.save(account);
	}

	private ResultActions loginUser(String username, String password) throws Exception {
		return mockMvc.perform(post("/user-account/login").contentType(MediaType.APPLICATION_JSON).content("{" +
			"\"username\":\"" + username + "\"," + "\"password\":\"" + password + "\"" + "}"));
	}
}
