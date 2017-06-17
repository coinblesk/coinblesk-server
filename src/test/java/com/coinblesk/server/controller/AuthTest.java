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

import static com.coinblesk.server.config.UserRole.USER;
import static com.coinblesk.util.SerializeUtils.GSON;
import static org.springframework.http.MediaType.APPLICATION_JSON_UTF8;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.util.StringUtils;
import org.springframework.web.context.WebApplicationContext;

import com.coinblesk.dto.LoginDTO;
import com.coinblesk.dto.UserAccountCreateDTO;
import com.coinblesk.dto.UserAccountCreateVerifyDTO;
import com.coinblesk.dto.UserAccountForgotDTO;
import com.coinblesk.dto.UserAccountForgotVerifyDTO;
import com.coinblesk.json.v1.UserAccountTO;
import com.coinblesk.server.config.AppConfig;
import com.coinblesk.server.dao.UserAccountRepository;
import com.coinblesk.server.entity.UserAccount;
import com.coinblesk.server.service.MailService;
import com.coinblesk.server.service.UserAccountService;
import com.coinblesk.server.utilTest.CoinbleskTest;

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

	@MockBean
	private MailService mailService;

	@Autowired
	private WebApplicationContext webAppContext;

	@Autowired
	private UserAccountService userAccountService;

	@Autowired
	private UserAccountRepository userAccountRepository;

	@Autowired
	PasswordEncoder passwordEncoder;

	@Autowired
	private AppConfig appConfig;

	@Before
	public void setUp() {
		MockitoAnnotations.initMocks(this);
		mockMvc = MockMvcBuilders.webAppContextSetup(webAppContext).apply(springSecurity()).build();
	}

	@Test
	public void loginWithNoContentFails() throws Exception {
		loginUser(null, null).andExpect(status().is4xxClientError());
	}
	@Test
	public void loginWithoutPasswordFails() throws Exception {
		loginUser("peter.griffin@csg.uzh.ch", null).andExpect(status().is4xxClientError());
	}
	@Test
	public void loginWithoutEmailFails() throws Exception {
		loginUser(null, "test123").andExpect(status().is4xxClientError());
	}
	@Test
	public void loginWithWrongPasswordFails() throws Exception {
		final String email = "log_me_in@valid-email.test";
		createUserHelper(email, "12345678");
		loginUser(email, "1234wroooohng5678").andExpect(status().is4xxClientError());
	}
	@Test
	public void loginWithCorrectPasswordSucceeds() throws Exception {
		final String email = "log_me_in@valid-email.test";
		final String password = "12345678";
		createUserHelper(email, password);
		loginUser(email, password).andExpect(status().is2xxSuccessful());
	}
	@Test
	public void loginReturnsValidTokenInHeader() throws Exception {
		final String email = "log_me_in@valid-email.test";
		final String password = "lsdj=231lkjXsdlkj";
		createUserHelper(email, password);

		// Correct login returns a token
		String authorizationToken = loginUser(email, password).andExpect(status().isOk()).andExpect(header().string
				("Authorization", Matchers.not(Matchers.isEmptyOrNullString()))).andReturn().getResponse().getHeader
				("Authorization");

		// Token is valid
		Assert.assertTrue(StringUtils.hasText(authorizationToken));
		Assert.assertTrue(authorizationToken.startsWith("Bearer "));
		String jwt = authorizationToken.substring(7, authorizationToken.length());

		// Check claims
		String serverSigningKey = appConfig.getJwtSecret();
		Jws<Claims> claims = Jwts.parser().setSigningKey(serverSigningKey.getBytes()).parseClaimsJws(jwt);
		Assert.assertEquals(claims.getBody().getSubject(), email);
		Assert.assertTrue(claims.getBody().getExpiration().after(new Date()));
		Assert.assertEquals(claims.getBody().get("auth", String.class), "ROLE_USER");
	}


	@Test
	public void createFailsWithNoContent() throws Exception {
		createUser(null, null).andExpect(status().is4xxClientError());
	}
	@Test
	public void createFailsWithoutPassword() throws Exception {
		createUser("peter.griffin@csg.uzh.ch", null).andExpect(status().is4xxClientError());
	}
	@Test
	public void createFailsWithoutEmail() throws Exception {
		createUser(null, "test123").andExpect(status().is4xxClientError());
	}
	@Test
	public void createFailsWithShortPassword() throws Exception {
		createUser("peter.griffin@csg.uzh.ch", "a").andExpect(status().is4xxClientError());
	}
	@Test
	public void createFailsWithWrongEmail() throws Exception {
		createUser("test@test@test@test", "test123").andExpect(status().is4xxClientError());
	}
	@Test
	public void createFailsWhenEmailAddressIsAlreadyUsed() throws Exception {
		createUser("test@test.test", "12345678").andExpect(status().is2xxSuccessful());
		createUser("test@test.test", "123456789").andExpect(status().is4xxClientError());
	}
	@Test
	public void createSendsEmailToUser() throws Exception {
		String email = "test@test.test";
		createUser(email, "12345678");
		Mockito.verify(mailService, Mockito.times(1)).sendUserMail(Mockito.matches(email), Mockito.contains("Activation"), Mockito.contains("click here to activate"));
	}

	@Test
	public void activateWithCorrectTokenSucceeds() throws Exception {
		String email = "test@test.test";
		createUser(email, "12345678").andExpect(status().is2xxSuccessful());
		String token = userAccountService.getByEmail(email).getActivationEmailToken();
		Assert.assertNotNull(token);

		UserAccountCreateVerifyDTO createVerifyDTO = new UserAccountCreateVerifyDTO();
		createVerifyDTO.setEmail(email);
		createVerifyDTO.setToken(token);

		mockMvc
			.perform(post("/user-account/create-verify")
					.contentType(APPLICATION_JSON_UTF8)
					.content(GSON.toJson(createVerifyDTO)))
			.andExpect(status().is2xxSuccessful());
	}
	@Test
	public void activateFailsWhenUserIsDeleted() throws Exception {
		String email = "test@test.test";
		createUser(email, "12345678").andExpect(status().is2xxSuccessful());
		String token = userAccountService.getByEmail(email).getActivationEmailToken();
		Assert.assertNotNull(token);
		userAccountService.delete(email);

		UserAccountCreateVerifyDTO createVerifyDTO = new UserAccountCreateVerifyDTO();
		createVerifyDTO.setEmail(email);
		createVerifyDTO.setToken(token);

		mockMvc
			.perform(post("/user-account/create-verify")
					.contentType(APPLICATION_JSON_UTF8)
					.content(GSON.toJson(createVerifyDTO)))
			.andExpect(status().is4xxClientError());
	}

	@Test
	public void forgotWorksWhenUserIsNotActivated() throws Exception {
		String email = "test@test.test";
		createUser(email, "12345678").andExpect(status().is2xxSuccessful());

		String activationToken = userAccountService.getByEmail(email).getActivationEmailToken();
		Assert.assertNotNull(activationToken);
		String forgotToken = userAccountService.getByEmail(email).getForgotEmailToken();
		Assert.assertNull(forgotToken);

		forgotPassword(email).andExpect(status().is2xxSuccessful());

		String activationTokenAfter = userAccountService.getByEmail(email).getActivationEmailToken();
		Assert.assertNotNull(activationTokenAfter);
		String forgotTokenAfter = userAccountService.getByEmail(email).getForgotEmailToken();
		Assert.assertNull(forgotTokenAfter);
	}
	@Test
	public void forgotFailsWhenUserIsDeleted() throws Exception {
		String email = "test@test.test";
		createUser(email, "12345678").andExpect(status().is2xxSuccessful());
		activateUserHelper(email);
		userAccountService.getByEmail(email).setDeleted(true);
		forgotPassword(email).andExpect(status().is4xxClientError());
	}
	@Test
	public void forgotVerify() throws Exception {
		String email = "test@test.test";
		createUser(email, "12345678").andExpect(status().is2xxSuccessful());
		activateUserHelper(email);
		forgotPassword(email).andExpect(status().is2xxSuccessful());

		String token = userAccountService.getByEmail(email).getForgotEmailToken();
		Assert.assertNotNull(token);

		UserAccountForgotVerifyDTO forgotVerifyDTO = new UserAccountForgotVerifyDTO();
		forgotVerifyDTO.setEmail(email);
		forgotVerifyDTO.setNewPassword("87654321");
		forgotVerifyDTO.setToken(token);

		mockMvc.perform(post("/user-account/forgot-verify")
				.contentType(APPLICATION_JSON_UTF8)
				.content(GSON.toJson(forgotVerifyDTO)))
		.andExpect(status().is2xxSuccessful());
	}

	@Test
	public void forgotVerifyFailsWhenUserIsDeleted() throws Exception {
		String email = "test@test.test";
		createUser(email, "12345678").andExpect(status().is2xxSuccessful());
		activateUserHelper(email);
		forgotPassword(email).andExpect(status().is2xxSuccessful());

		String token = userAccountService.getByEmail(email).getForgotEmailToken();
		Assert.assertNotNull(token);

		userAccountService.delete(email);

		UserAccountForgotVerifyDTO forgotVerifyDTO = new UserAccountForgotVerifyDTO();
		forgotVerifyDTO.setEmail(email);
		forgotVerifyDTO.setNewPassword("87654321");
		forgotVerifyDTO.setToken(token);

		mockMvc.perform(post("/user-account/forgot-verify")
				.contentType(APPLICATION_JSON_UTF8)
				.content(GSON.toJson(forgotVerifyDTO)))
		.andExpect(status().is4xxClientError());
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
		activateUserHelper(mail);

		String jwt = Jwts.builder().setSubject(mail).claim("auth", "ROLE_USER").signWith(SignatureAlgorithm.HS256,
			appConfig.getJwtSecret().getBytes()).setExpiration(Date.from(Instant.now().plus(Duration.ofHours(1))))
			.compact();

		MvcResult res = mockMvc.perform(get("/auth/common/user-account").header("Authorization", "Bearer " + jwt)).andExpect
			(status().isOk()).andReturn();
		UserAccountTO userAccountTO = GSON.fromJson(res.getResponse().getContentAsString(),
			UserAccountTO.class);
		Assert.assertEquals(mail, userAccountTO.email());
	}

	public void createUserHelper(String email, String password) {
		UserAccount userAccount = new UserAccount();
		userAccount.setBalance(BigDecimal.valueOf(0.0));
		userAccount.setCreationDate(new Date());
		userAccount.setDeleted(false);
		userAccount.setEmail(email);
		userAccount.setActivationEmailToken(null);
		userAccount.setForgotEmailToken(null);
		userAccount.setPassword(passwordEncoder.encode(password));
		userAccount.setUserRole(USER);
		userAccountRepository.save(userAccount);
	}

	private void activateUserHelper(String email) {
		UserAccount account = userAccountRepository.findByEmail(email);
		account.setActivationEmailToken(null);
		userAccountRepository.save(account);
	}

	private ResultActions loginUser(String email, String password) throws Exception {
		final LoginDTO loginDTO = new LoginDTO();
		loginDTO.setEmail(email);
		loginDTO.setPassword(password);

		return mockMvc
				.perform(put("/user-account/login")
						.contentType(APPLICATION_JSON_UTF8)
						.content(GSON.toJson(loginDTO)));
	}

	private ResultActions createUser(String email, String password) throws Exception {
		UserAccountCreateDTO userAccountCreateDTO = new UserAccountCreateDTO();
		userAccountCreateDTO.setEmail(email);
		userAccountCreateDTO.setPassword(password);
		userAccountCreateDTO.setClientPrivateKeyEncrypted("eyJpdiI6InBRK1dycitoQkNVdzJkMXVxSGQwT0E9PSIsInYiOjEsIml0ZXIiOjEwMDAwLCJrcyI6MTI4LCJ0cyI6NjQsIm1vZGUiOiJjY20iLCJhZGF0YSI6IiIsImNpcGhlciI6ImFlcyIsInNhbHQiOiJlTVNrZ1E2N3B0dz0iLCJjdCI6ImFpU09WTEVaU1pJSEljMndCUi92UHUrSUsxc2JEVWtjejdoKzBtaG5zR08xckxZYTUvTXR0amc2QjhCVUhnS1czeldwSEtRQ3VST2R5LzBlIn0=");
		userAccountCreateDTO.setClientPublicKey("030ad4737d72bb652054a87471c042d614b8ea055e75b67f112f9b8e5441f9b13a");
		userAccountCreateDTO.setLockTime(1506352444L);

		return mockMvc
				.perform(post("/user-account/create")
						.contentType(APPLICATION_JSON_UTF8)
						.content(GSON.toJson(userAccountCreateDTO)));
	}

	private ResultActions forgotPassword(String email) throws Exception {
		UserAccountForgotDTO forgotDTO = new UserAccountForgotDTO();
		forgotDTO.setEmail(email);

		return mockMvc.perform(post("/user-account/forgot")
			.contentType(APPLICATION_JSON_UTF8)
			.content(GSON.toJson(forgotDTO)));
	}
}
