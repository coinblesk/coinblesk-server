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

import static com.coinblesk.server.auth.JWTConfigurer.AUTHORIZATION_HEADER;
import static java.util.Locale.ENGLISH;
import static org.springframework.http.MediaType.APPLICATION_JSON_UTF8_VALUE;
import static org.springframework.http.ResponseEntity.ok;
import static org.springframework.web.bind.annotation.RequestMethod.POST;
import static org.springframework.web.bind.annotation.RequestMethod.PUT;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Collections;
import java.util.Locale;

import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import com.coinblesk.server.auth.TokenProvider;
import com.coinblesk.server.config.AppConfig;
import com.coinblesk.server.dto.LoginDTO;
import com.coinblesk.server.dto.UserAccountCreateDTO;
import com.coinblesk.server.entity.UserAccount;
import com.coinblesk.server.exceptions.BusinessException;
import com.coinblesk.server.exceptions.CoinbleskAuthenticationException;
import com.coinblesk.server.exceptions.CoinbleskInternalError;
import com.coinblesk.server.service.MailService;
import com.coinblesk.server.service.UserAccountService;
import com.coinblesk.server.utils.ApiVersion;

/**
 * @author Thomas Bocek
 */
@RestController
@RequestMapping(value = "/user-account")
@ApiVersion({ "v1", "" })
public class UserAccountController {

	private final static Logger LOG = LoggerFactory.getLogger(UserAccountController.class);

	private final UserAccountService userAccountService;
	private final MailService mailService;
	private final MessageSource messageSource;
	private final AppConfig cfg;
	private final TokenProvider tokenProvider;
	private final AuthenticationManager authenticationManager;

	@Autowired
	public UserAccountController(UserAccountService userAccountService, MailService mailService,
			MessageSource messageSource, AppConfig cfg, TokenProvider tokenProvider,
			AuthenticationManager authenticationManager) {
		this.userAccountService = userAccountService;
		this.mailService = mailService;
		this.messageSource = messageSource;
		this.cfg = cfg;
		this.tokenProvider = tokenProvider;
		this.authenticationManager = authenticationManager;
	}

	@RequestMapping(value = "/login", method = PUT, consumes = APPLICATION_JSON_UTF8_VALUE, produces = APPLICATION_JSON_UTF8_VALUE)
	public ResponseEntity<?> login(@Valid @RequestBody LoginDTO loginDTO, HttpServletResponse response) throws BusinessException {

		UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(
				loginDTO.getEmail().toLowerCase(ENGLISH), loginDTO.getPassword());

		try {
			Authentication authentication = this.authenticationManager.authenticate(authenticationToken);
			SecurityContextHolder.getContext().setAuthentication(authentication);

			String jwt = tokenProvider.createToken(authentication);
			response.addHeader(AUTHORIZATION_HEADER, "Bearer " + jwt);

			return ok(Collections.singletonMap("token", jwt));

		} catch (AuthenticationException exception) {
			throw new CoinbleskAuthenticationException();
		}
	}

	@RequestMapping(value = "/create", method = POST, consumes = APPLICATION_JSON_UTF8_VALUE)
	public void createAccount(Locale locale, @Valid @RequestBody UserAccountCreateDTO userAccountCreateDTO)
			throws BusinessException {
		LOG.debug("Create account for {}", userAccountCreateDTO.getEmail());
		// TODO: reactived if deleted flag is set

		UserAccount userAccount;
		if (!userAccountService.userExists(userAccountCreateDTO.getEmail())) {
			userAccount = userAccountService.create(userAccountCreateDTO);
		} else {
			userAccount = userAccountService.getByEmail(userAccountCreateDTO.getEmail());
		}

		if (userAccount.getEmailToken() != null) {
			try {
				LOG.debug("send email to {}", userAccount.getEmail());
				String path = "user-account/create-verify/"
						+ URLEncoder.encode(userAccount.getEmail(), "UTF-8")
						+ "/"
						+ userAccount.getEmailToken();
				String url = cfg.getUrl() + path;

				mailService.sendUserMail(userAccount.getEmail(),
						messageSource.getMessage("activation.email" + ".title", null, locale),
						messageSource.getMessage("activation.email.text", new String[] { url }, locale));

			} catch (Exception e) {
				LOG.error("Mail send error", e);
				mailService.sendAdminMail("Coinblesk Error", "Unexpected Error: " + e);
				throw new CoinbleskInternalError("An error happend while sending an e-mail.");
			}
		}
	}

	// http://stackoverflow.com/questions/16332092/spring-mvc-pathvariable-with-dot-is-getting-truncated
	@RequestMapping(value = "/create-verify/{email:.+}/{token}", method = PUT)
	public void verifyEmail(@PathVariable(value = "email") String email, @PathVariable(value = "token") String token)
			throws BusinessException {

		LOG.debug("Activate account for {}", email);

		try {
			userAccountService.activate(email, token);
		} catch (BusinessException exception) {
			LOG.error("Someone tried a link with an invalid token: {}/{} - {}", email, token,
					exception.getClass().getSimpleName());
			mailService.sendAdminMail("Wrong Link?", "Someone tried a link with an invalid token: "
					+ email
					+ " / "
					+ token
					+ " - "
					+ exception.getClass().getSimpleName());
			throw exception;
		}

		LOG.debug("Activate account success for {}", email);
	}

	// http://stackoverflow.com/questions/16332092/spring-mvc-pathvariable-with-dot-is-getting-truncated
	@RequestMapping(value = "/forgot/{email:.+}", method = POST)
	public void forgot(Locale locale, @PathVariable(value = "email") String email) throws BusinessException {

		LOG.debug("Forgot password for {}", email);
		userAccountService.forgot(email);

		LOG.debug("Send forgot email to {}", email);
		UserAccount userAccount = userAccountService.getByEmail(email);
		String forgotToken = userAccount.getForgotEmailToken();
		String url;
		try {
			String path = "user-account/forgot-verify/" + URLEncoder.encode(email, "UTF-8") + "/" + forgotToken;
			url = cfg.getUrl() + path;
		} catch (UnsupportedEncodingException exception) {
			throw new CoinbleskInternalError("An internal error occured.");
		}

		try {
			mailService.sendUserMail(email, messageSource.getMessage("forgot.email.title", null, locale),
					messageSource.getMessage("forgot.email.text", new String[] { url }, locale));

		} catch (Exception e) {
			mailService.sendAdminMail("Coinblesk Error", "Unexpected Error: " + e);
			LOG.error("Mail send error", e);
			throw new CoinbleskInternalError("An error happend while sending an e-mail.");
		}
	}

	// http://stackoverflow.com/questions/16332092/spring-mvc-pathvariable-with-dot-is-getting-truncated
	@RequestMapping(value = "/forgot-verify/{email:.+}/{forgot-token}/{new-password}", method = PUT)
	@ResponseBody
	public void forgotVerifyEmail(@PathVariable(value = "email") String email,
			@PathVariable(value = "forgot-token") String forgetToken,
			@PathVariable(value = "new-password") String newPassword) throws BusinessException {

		LOG.debug("Verify password forget for account {}", email);

		try {
			userAccountService.activateForgot(email, forgetToken, newPassword);
		} catch(BusinessException exception) {
			LOG.error("Someone tried a link with an invalid forget token: {}/{} - {}", email, forgetToken,
					exception.getClass().getSimpleName());
			mailService.sendAdminMail("Wrong Link?", "Someone tried a link with an invalid forget token: "
					+ email
					+ " / "
					+ forgetToken
					+ " - "
					+ exception.getClass().getSimpleName());
			throw exception;
		}

		LOG.debug("Activate forgot password success for {}", email);
	}
}
