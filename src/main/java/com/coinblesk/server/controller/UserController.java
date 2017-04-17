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

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.MediaType.APPLICATION_JSON_UTF8_VALUE;
import static org.springframework.web.bind.annotation.RequestMethod.GET;
import static org.springframework.web.bind.annotation.RequestMethod.POST;

import java.net.URLEncoder;
import java.util.Collections;
import java.util.Locale;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.coinblesk.json.v1.Type;
import com.coinblesk.json.v1.UserAccountStatusTO;
import com.coinblesk.json.v1.UserAccountTO;
import com.coinblesk.server.auth.JWTConfigurer;
import com.coinblesk.server.auth.TokenProvider;
import com.coinblesk.server.config.AppConfig;
import com.coinblesk.server.dto.LoginDTO;
import com.coinblesk.server.entity.UserAccount;
import com.coinblesk.server.service.MailService;
import com.coinblesk.server.service.UserAccountService;
import com.coinblesk.server.utils.ApiVersion;
import com.coinblesk.util.Pair;

/**
 * @author Thomas Bocek
 */
@RestController
@RequestMapping(value = "/user")
@ApiVersion({"v1", ""})
public class UserController {

	private final static Logger LOG = LoggerFactory.getLogger(UserController.class);

	private final UserAccountService userAccountService;
	private final MailService mailService;
	private final MessageSource messageSource;
	private final AppConfig cfg;
	private final TokenProvider tokenProvider;
	private final AuthenticationManager authenticationManager;

	@Autowired
	public UserController(UserAccountService userAccountService, MailService mailService, MessageSource messageSource,
						  AppConfig cfg, TokenProvider tokenProvider, AuthenticationManager authenticationManager) {
		this.userAccountService = userAccountService;
		this.mailService = mailService;
		this.messageSource = messageSource;
		this.cfg = cfg;
		this.tokenProvider = tokenProvider;
		this.authenticationManager = authenticationManager;
	}

	@RequestMapping(value = "/login", method = POST, consumes = APPLICATION_JSON_UTF8_VALUE, produces =
		APPLICATION_JSON_UTF8_VALUE)
	public ResponseEntity<?> login(@Valid @RequestBody LoginDTO loginDTO, HttpServletResponse response) {

		UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(loginDTO
			.getUsername().toLowerCase(Locale.ENGLISH), loginDTO.getPassword());

		try {
			Authentication authentication = this.authenticationManager.authenticate(authenticationToken);
			SecurityContextHolder.getContext().setAuthentication(authentication);

			String jwt = tokenProvider.createToken(authentication);
			response.addHeader(JWTConfigurer.AUTHORIZATION_HEADER, "Bearer " + jwt);

			return ResponseEntity.ok(Collections.singletonMap("token", jwt));
		} catch (AuthenticationException exception) {
			return new ResponseEntity<>(Collections.singletonMap("AuthenticationException", exception
				.getLocalizedMessage()), HttpStatus.UNAUTHORIZED);
		}
	}

	// CRUD for the user
	@RequestMapping(value = "/create", method = POST, consumes = APPLICATION_JSON_UTF8_VALUE, produces =
		APPLICATION_JSON_UTF8_VALUE)
	@ResponseBody
	public UserAccountStatusTO createAccount(Locale locale, @RequestBody UserAccountTO userAccount) {
		LOG.debug("Create account for {}", userAccount.email());
		try {
			// TODO: reactived if deleted flag is set
			Pair<UserAccountStatusTO, UserAccount> pair = userAccountService.create(userAccount);
			if ((pair.element0().isSuccess() || pair.element0().type() == Type
				.SUCCESS_BUT_EMAIL_ALREADY_EXISTS_NOT_ACTIVATED) && pair.element1() != null && pair.element1()
				.getEmailToken() != null) {

				try {
					LOG.debug("send email to {}", pair.element1().getEmail());
					final String path = "v1/user/verify/" + URLEncoder.encode(pair.element1().getEmail(), "UTF-8") +
						"/" + pair.element1().getEmailToken();
					final String url = cfg.getUrl() + path;
					mailService.sendUserMail(pair.element1().getEmail(), messageSource.getMessage("activation.email" +
						".title", null, locale), messageSource.getMessage("activation.email.text", new String[]{url},
						locale));
				} catch (Exception e) {
					LOG.error("Mail send error", e);
					mailService.sendAdminMail("Coinblesk Error", "Unexpected Error: " + e);
				}
			}
			return pair.element0();
		} catch (Exception e) {
			LOG.error("User create error", e);
			return new UserAccountStatusTO().type(Type.SERVER_ERROR).message(e.getMessage());
		}
	}

	@RequestMapping(value = "/verify/{email}/{token}", method = GET)
	@ResponseBody
	public String verifyEmail(@PathVariable(value = "email") String email, @PathVariable(value = "token") String
		token, HttpServletRequest request) {
		LOG.debug("Activate account for {}", email);
		try {
			UserAccountStatusTO status = userAccountService.activate(email, token);
			if (!status.isSuccess()) {
				LOG.error("Someone tried a link with an invalid token: {}/{}/{}", email, token, status.type().name());
				mailService.sendAdminMail("Wrong Link?", "Someone tried a link with an invalid token: " + email +
					" / " + token + "/" + status.type().name());
				throw new BadRequestException("Wrong Link");
			}
			LOG.debug("Activate account success for {}", email);
			// TODO: text/layout
			return "Activate account success";
		} catch (Exception e) {
			LOG.error("User create error", e);
			throw new InternalServerErrorException(e);
		}
	}

	// http://stackoverflow.com/questions/16332092/spring-mvc-pathvariable-with-dot-is-getting-truncated
	@RequestMapping(value = "/forgot/{email:.+}", method = GET)
	@ResponseBody
	public UserAccountStatusTO forgot(Locale locale, @PathVariable(value = "email") String email, HttpServletRequest
		request) {
		LOG.debug("Forgot password for {}", email);
		try {
			Pair<UserAccountStatusTO, UserAccountTO> pair = userAccountService.forgot(email);
			if (pair.element0().isSuccess()) {
				try {
					LOG.debug("send forgot email to {}", email);
					String forgotToken = pair.element1().message();
					String password = pair.element1().password();
					final String path = "v1/user/forgot-verify/" + URLEncoder.encode(email, "UTF-8") + "/" +
						forgotToken;
					final String url = cfg.getUrl() + path;
					mailService.sendUserMail(email, messageSource.getMessage("forgot.email.title", null, locale),
						messageSource.getMessage("forgot.email.text", new String[]{url, password}, locale));
				} catch (Exception e) {
					LOG.error("Mail send error", e);
					mailService.sendAdminMail("Coinblesk Error", "Unexpected Error: " + e);
				}
			}
			return pair.element0();
		} catch (Exception e) {
			LOG.error("Forget password error", e);
			return new UserAccountStatusTO().type(Type.SERVER_ERROR).message(e.getMessage());
		}
	}

	@RequestMapping(value = "/forgot-verify/{email}/{forgot-token}", method = GET)
	@ResponseBody
	public String forgotVerifyEmail(@PathVariable(value = "email") String email, @PathVariable(value = "forgot-token")
		String forgetToken, HttpServletRequest request) {
		LOG.debug("Activate account for {}", email);
		try {
			UserAccountStatusTO status = userAccountService.activateForgot(email, forgetToken);
			if (!status.isSuccess()) {
				LOG.error("Someone tried a link with an invalid forget token: {}/{}/{}", email, forgetToken, status
					.type().name());
				mailService.sendAdminMail("Wrong Link?", "Someone tried a link with an invalid forget token: " + email
					+ " / " + forgetToken + "/" + status.type().name());
				throw new BadRequestException("Wrong Link");
			}
			LOG.debug("Activate forgot password success for {}", email);
			// TODO: text/layout
			return "Password reset verify success";
		} catch (Exception e) {
			LOG.error("Password reset verify error", e);
			throw new InternalServerErrorException(e);
		}
	}

	@SuppressWarnings("serial")
	@ResponseStatus(value = BAD_REQUEST)
	public class BadRequestException extends RuntimeException {

		public BadRequestException(String reason) {
			super(reason);
		}
	}

	@SuppressWarnings("serial")
	@ResponseStatus(value = INTERNAL_SERVER_ERROR)
	public class InternalServerErrorException extends RuntimeException {

		public InternalServerErrorException(Throwable t) {
			super(t);
		}
	}
}
