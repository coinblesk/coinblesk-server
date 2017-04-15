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

import static org.springframework.http.MediaType.APPLICATION_JSON_UTF8_VALUE;
import static org.springframework.web.bind.annotation.RequestMethod.GET;
import static org.springframework.web.bind.annotation.RequestMethod.PATCH;
import static org.springframework.web.bind.annotation.RequestMethod.POST;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.bitcoinj.core.ECKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import com.coinblesk.json.v1.BaseTO;
import com.coinblesk.json.v1.Type;
import com.coinblesk.json.v1.UserAccountStatusTO;
import com.coinblesk.json.v1.UserAccountTO;
import com.coinblesk.server.service.MailService;
import com.coinblesk.server.service.UserAccountService;
import com.coinblesk.server.utils.ApiVersion;

/**
 *
 * @author Thomas Bocek
 */
@RestController
@RequestMapping(value = "/user/auth")
@ApiVersion({ "v1" })
public class UserControllerAuthenticated {

	private final static Logger LOG = LoggerFactory.getLogger(UserControllerAuthenticated.class);

	private final UserAccountService userAccountService;

	private final MailService mailService;

	@Autowired
	public UserControllerAuthenticated(UserAccountService userAccountService, MailService mailService) {
		this.userAccountService = userAccountService;
		this.mailService = mailService;
	}

	@RequestMapping(
			value = "/delete",
			method = PATCH,
			produces = APPLICATION_JSON_UTF8_VALUE)
	@ResponseBody
	public UserAccountStatusTO deleteAccount() {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		LOG.debug("Delete account for {}", auth.getName());
		try {
			UserAccountStatusTO status = userAccountService.delete(auth.getName());
			if (!status.isSuccess()) {
				LOG.error("Someone tried a delete account with an invalid username: {}/{}", auth, status.type().name());
				mailService.sendAdminMail("Wrong Delete Account?",
						"Someone tried a delete account with an invalid username: "
								+ auth
								+ "/"
								+ status.type().name());
			}
			LOG.debug("Delete account success for {}", auth.getName());
			return status;

		} catch (Exception e) {
			LOG.error("User create error", e);
			return new UserAccountStatusTO().type(Type.SERVER_ERROR).message(e.getMessage());
		}
	}

	@RequestMapping(
			value = "/get",
			method = GET,
			produces = APPLICATION_JSON_UTF8_VALUE)
	@ResponseBody
	public UserAccountTO getAccount() {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		LOG.debug("Get account for {}", auth.getName());
		try {

			UserAccountTO userAccount = userAccountService.get(auth.getName());
			if (userAccount == null) {
				LOG.error("Someone tried to access an account with an invalid username: {}", auth);
				mailService.sendAdminMail("Wrong Account?",
						"Someone tried to access an account with an invalid username: " + auth);
				return null;
			}
			LOG.debug("Get account success for {}", auth.getName());
			return userAccount;
		} catch (Exception e) {
			LOG.error("User create error", e);
			return new UserAccountTO().type(Type.SERVER_ERROR).message(e.getMessage());
		}
	}

	@RequestMapping(
			value = "/transfer-p2sh",
			method = POST,
			produces = APPLICATION_JSON_UTF8_VALUE)
	@ResponseBody
	public UserAccountTO transferToP2SH(@RequestBody BaseTO request) {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		LOG.debug("Get account for {}", auth.getName());
		try {
			final ECKey clientKey = ECKey.fromPublicOnly(request.publicKey());
			UserAccountTO status = userAccountService.transferP2SH(clientKey, auth.getName());
			if (status != null) {
				LOG.debug("Transfer P2SH success for {}, tx:{}", auth.getName(), status.message());
				return status;
			} else {
				return new UserAccountTO().type(Type.ACCOUNT_ERROR);
			}
		} catch (Exception e) {
			LOG.error("User create error", e);
			return new UserAccountTO().type(Type.SERVER_ERROR).message(e.getMessage());
		}
	}

	@RequestMapping(
			value = "/change-password",
			method = POST,
			produces = APPLICATION_JSON_UTF8_VALUE,
			consumes = APPLICATION_JSON_UTF8_VALUE)
	public UserAccountStatusTO changePassword(@RequestBody UserAccountTO to, HttpServletRequest request,
			HttpServletResponse response) {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		LOG.debug("Change password account for {}", auth.getName());
		if (auth != null) {
			UserAccountStatusTO status = userAccountService.changePassword(auth.getName(), to.password());
			return status;
		} else {
			return new UserAccountStatusTO().type(Type.ACCOUNT_ERROR);
		}

	}

}
