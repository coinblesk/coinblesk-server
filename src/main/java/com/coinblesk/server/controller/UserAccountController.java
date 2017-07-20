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
import static com.coinblesk.server.enumerator.EventType.USER_ACCOUNT_COULD_NOT_BE_ACTIVATED_WITH_REGISTRATION_TOKEN;
import static com.coinblesk.server.enumerator.EventType.USER_ACCOUNT_COULD_NOT_BE_ACTIVATED_WRONG_LINK;
import static com.coinblesk.server.enumerator.EventType.USER_ACCOUNT_COULD_NOT_CREATE_USER;
import static com.coinblesk.server.enumerator.EventType.USER_ACCOUNT_COULD_NOT_HANDLE_FORGET_REQUEST;
import static com.coinblesk.server.enumerator.EventType.USER_ACCOUNT_COULD_NOT_SEND_FORGET_EMAIL;
import static com.coinblesk.server.enumerator.EventType.USER_ACCOUNT_COULD_NOT_VERIFY_FORGOT_WRONG_LINK;
import static com.coinblesk.server.enumerator.EventType.USER_ACCOUNT_CREATE_TOKEN_COULD_NOT_BE_SENT;
import static com.coinblesk.server.enumerator.EventType.USER_ACCOUNT_LOGIN_FAILED;
import static com.coinblesk.server.enumerator.EventType.USER_ACCOUNT_LOGIN_FAILED_WITH_DELETED_ACCOUNT;
import static com.coinblesk.server.enumerator.EventType.USER_ACCOUNT_LOGIN_FAILED_WITH_UNREGISTERED_ACCOUNT;
import static java.util.Locale.ENGLISH;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.MediaType.APPLICATION_JSON_UTF8_VALUE;
import static org.springframework.web.bind.annotation.RequestMethod.POST;
import static org.springframework.web.bind.annotation.RequestMethod.PUT;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Locale;

import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.coinblesk.dto.LoginDTO;
import com.coinblesk.dto.TokenDTO;
import com.coinblesk.dto.UserAccountCreateDTO;
import com.coinblesk.dto.UserAccountCreateVerifyDTO;
import com.coinblesk.dto.UserAccountCreateWithTokenDTO;
import com.coinblesk.dto.UserAccountForgotDTO;
import com.coinblesk.dto.UserAccountForgotVerifyDTO;
import com.coinblesk.server.auth.TokenProvider;
import com.coinblesk.server.config.AppConfig;
import com.coinblesk.server.entity.UserAccount;
import com.coinblesk.server.exceptions.BusinessException;
import com.coinblesk.server.exceptions.CoinbleskInternalError;
import com.coinblesk.server.exceptions.UserAccountDeletedException;
import com.coinblesk.server.exceptions.UserAccountHasUnregisteredToken;
import com.coinblesk.server.exceptions.UserAccountNotFoundException;
import com.coinblesk.server.service.EventService;
import com.coinblesk.server.service.MailService;
import com.coinblesk.server.service.UserAccountService;

/**
 * @author Thomas Bocek
 */
@RestController
@RequestMapping(value = "/user-account")
public class UserAccountController {

	private final static Logger LOG = LoggerFactory.getLogger(UserAccountController.class);

	private final UserAccountService userAccountService;
	private final MailService mailService;
	private final MessageSource messageSource;
	private final AppConfig cfg;
	private final TokenProvider tokenProvider;
	private final AuthenticationManager authenticationManager;
	private final EventService eventService;

	@Autowired
	public UserAccountController(UserAccountService userAccountService, MailService mailService,
			MessageSource messageSource, AppConfig cfg, TokenProvider tokenProvider,
			AuthenticationManager authenticationManager, EventService eventService) {
		this.userAccountService = userAccountService;
		this.mailService = mailService;
		this.messageSource = messageSource;
		this.cfg = cfg;
		this.tokenProvider = tokenProvider;
		this.authenticationManager = authenticationManager;
		this.eventService = eventService;
	}

	@RequestMapping(value = "/login", method = PUT, consumes = APPLICATION_JSON_UTF8_VALUE, produces = APPLICATION_JSON_UTF8_VALUE)
	@ResponseBody
	public TokenDTO login(@Valid @RequestBody LoginDTO loginDTO, HttpServletResponse response) throws BusinessException {
		LOG.debug("Login trial of user {}", loginDTO.getEmail());

		if (userAccountService.userExists(loginDTO.getEmail())) {
			UserAccount userAccount = userAccountService.getByEmail(loginDTO.getEmail());
			if (userAccount.isDeleted()) {
				eventService.error(USER_ACCOUNT_LOGIN_FAILED_WITH_DELETED_ACCOUNT, "Failed login with e-mail '" + loginDTO.getEmail()+ "'");
				throw new UserAccountDeletedException();
			}
			if (userAccount.hasUnregisteredToken()) {
				eventService.error(USER_ACCOUNT_LOGIN_FAILED_WITH_UNREGISTERED_ACCOUNT, "Failed login with e-mail '" + loginDTO.getEmail() + "'");
				throw new UserAccountHasUnregisteredToken();
			}
		}

		UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(
				loginDTO.getEmail().toLowerCase(ENGLISH), loginDTO.getPassword());

		try {
			Authentication authentication = this.authenticationManager.authenticate(authenticationToken);
			SecurityContextHolder.getContext().setAuthentication(authentication);
			String jwt = tokenProvider.createToken(authentication);

			LOG.debug("Successful login of user {}", loginDTO.getEmail());

			TokenDTO token = new TokenDTO();
			token.setToken(jwt);

			response.addHeader(AUTHORIZATION_HEADER, "Bearer " + jwt);
			return token;

		} catch (AuthenticationException exception) {
			eventService.warn(USER_ACCOUNT_LOGIN_FAILED, "Failed login with e-mail '" + loginDTO.getEmail()+ "'");
			throw exception;
		}
	}

	@RequestMapping(value = "/create", method = POST, consumes = APPLICATION_JSON_UTF8_VALUE)
	@ResponseStatus(code = CREATED)
	public void createAccount(Locale locale, @Valid @RequestBody UserAccountCreateDTO createDTO) throws BusinessException {
		LOG.debug("Create account for {}", createDTO.getEmail());

		UserAccount userAccount;
		try {
			userAccount = userAccountService.create(createDTO);

		} catch(BusinessException exception) {
			LOG.warn("An exception occured during the creation of a user", exception);
			eventService.warn(USER_ACCOUNT_COULD_NOT_CREATE_USER, "An exception occured during the creation of the account: " + exception.getClass().getSimpleName());
			throw exception;
		}

		try {
			sendActivationEmailToUser(userAccount, locale);
		} catch (Exception e) {
			LOG.error("Mail send error", e);
			eventService.error(USER_ACCOUNT_CREATE_TOKEN_COULD_NOT_BE_SENT, "Token of '"+createDTO.getEmail() + "' could not be sent.");
			throw new CoinbleskInternalError("An error happend while sending an e-mail.");
		}
	}
	private void sendActivationEmailToUser(UserAccount userAccount, Locale locale) {
		LOG.debug("send email to {}", userAccount.getEmail());
		String path = "activation/"
				+ encodeURL(userAccount.getEmail())
				+ "/"
				+ userAccount.getActivationEmailToken();
		String url = cfg.getFrontendUrl() + path;

		mailService.sendUserMail(userAccount.getEmail(),
				messageSource.getMessage("activation.email" + ".title", null, locale),
				messageSource.getMessage("activation.email.text", new String[] { url }, locale));
	}

	@RequestMapping(value = "/create-verify", method = POST, consumes = APPLICATION_JSON_UTF8_VALUE)
	public void verifyEmail(@Valid @RequestBody UserAccountCreateVerifyDTO createVerifyDTO) throws BusinessException {
		LOG.debug("Activate account for {}", createVerifyDTO.getEmail());

		try {
			userAccountService.activate(createVerifyDTO);

		} catch (BusinessException exception) {
			LOG.warn("Someone tried a link with an invalid token: {}/{} - {}", createVerifyDTO.getEmail(), createVerifyDTO.getToken(),
					exception.getClass().getSimpleName());

			eventService.warn(USER_ACCOUNT_COULD_NOT_BE_ACTIVATED_WRONG_LINK, "Someone tried a link with an invalid token: "
					+ createVerifyDTO.getEmail()
					+ " / "
					+ createVerifyDTO.getToken()
					+ " - "
					+ exception.getClass().getSimpleName());
			throw exception;
		}

		LOG.debug("Activate account success for {}", createVerifyDTO.getEmail());
	}

	@RequestMapping(value = "/forgot", method = POST, consumes = APPLICATION_JSON_UTF8_VALUE)
	public void forgot(Locale locale, @Valid @RequestBody UserAccountForgotDTO forgotDTO) throws BusinessException {

		LOG.debug("Forgot password for {}", forgotDTO.getEmail());
		UserAccount userAccount = userAccountService.getByEmail(forgotDTO.getEmail());

		if(userAccount == null) {
			throw new UserAccountNotFoundException();
		}
		if(userAccount.isDeleted()) {
			throw new UserAccountDeletedException();
		}
		if(userAccount.hasUnregisteredToken()) {
			throw new UserAccountHasUnregisteredToken();
		}

		// user is not activated
		if(!userAccount.isActivationVerified()) {
			try {
				sendActivationEmailToUser(userAccount, locale);
			} catch (Exception e) {
				eventService.error(USER_ACCOUNT_COULD_NOT_HANDLE_FORGET_REQUEST, "The activation email during forget-password (user is not yet activated) could not be sent to '" + forgotDTO.getEmail() + "'.");
				LOG.error("Mail send error", e);
				throw new CoinbleskInternalError("An error happend while sending an e-mail.");
			}
		}
		else {
			try {
				userAccountService.forgot(forgotDTO.getEmail());
			} catch (BusinessException exception) {
				LOG.warn("Could not handle forgot request", exception);
				eventService.warn(USER_ACCOUNT_COULD_NOT_HANDLE_FORGET_REQUEST, "An exception occured during the forgot request - "+exception.getClass().getSimpleName());
				throw exception;
			}

			try {
				LOG.debug("Send forgot email to {}", forgotDTO.getEmail());
				String forgotToken = userAccount.getForgotEmailToken();
				String path = "password-forgotten-verification/" + encodeURL(forgotDTO.getEmail()) + "/" + forgotToken;
				String url = cfg.getFrontendUrl() + path;

				mailService.sendUserMail(forgotDTO.getEmail(), messageSource.getMessage("forgot.email.title", null, locale),
						messageSource.getMessage("forgot.email.text", new String[] { url }, locale));

			} catch (Exception e) {
				eventService.error(USER_ACCOUNT_COULD_NOT_SEND_FORGET_EMAIL, "The forgot email could not be sent to '" + forgotDTO.getEmail() + "'.");
				LOG.error("Mail send error", e);
				throw new CoinbleskInternalError("An error happend while sending an e-mail.");
			}
		}
	}

	@RequestMapping(value = "/forgot-verify", method = POST)
	public void forgotVerifyEmail(@Valid @RequestBody UserAccountForgotVerifyDTO forgotVerifyDTO) throws BusinessException {
		LOG.debug("Verify password forget for account {}", forgotVerifyDTO.getEmail());

		try {
			userAccountService.activateForgot(forgotVerifyDTO);
		} catch(BusinessException exception) {
			LOG.error("Someone tried a link with an invalid forget token: {}/{} - {}", forgotVerifyDTO.getEmail(), forgotVerifyDTO.getToken(),
					exception.getClass().getSimpleName());
			eventService.warn(USER_ACCOUNT_COULD_NOT_VERIFY_FORGOT_WRONG_LINK, "Someone tried a link with an invalid token: "
					+ forgotVerifyDTO.getEmail()
					+ " / "
					+ forgotVerifyDTO.getToken()
					+ " - "
					+ exception.getClass().getSimpleName());
			throw exception;
		}

		LOG.debug("Activate forgot password success for {}", forgotVerifyDTO.getEmail());
	}

	@RequestMapping(value = "/create-with-token", method = POST, consumes = APPLICATION_JSON_UTF8_VALUE)
	public void createAccountWithToken(Locale locale, @Valid @RequestBody UserAccountCreateWithTokenDTO createDTO) throws BusinessException {
		LOG.debug("Create user account with token (update existing template user)");

		try {
			userAccountService.activateWithRegistrationToken(createDTO);
		} catch(BusinessException exception) {
			LOG.warn("An exception occurred during the creation of a user (from token)", exception);
			eventService.warn(USER_ACCOUNT_COULD_NOT_BE_ACTIVATED_WITH_REGISTRATION_TOKEN, "An exception occurred during the activation of the user account (with registration token): " + exception.getClass().getSimpleName());
			throw exception;
		}
	}

	private String encodeURL(String string) {
		try {
			return URLEncoder.encode(string, "UTF-8");
		} catch (UnsupportedEncodingException e) {
			throw new CoinbleskInternalError("An internal error occured.");
		}
	}
}
