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

import static com.coinblesk.json.v1.Type.SERVER_ERROR;
import static com.coinblesk.server.config.UserRole.ROLE_ADMIN;
import static com.coinblesk.server.config.UserRole.ROLE_USER;
import static org.springframework.http.MediaType.APPLICATION_JSON_UTF8_VALUE;
import static org.springframework.web.bind.annotation.RequestMethod.DELETE;
import static org.springframework.web.bind.annotation.RequestMethod.GET;
import static org.springframework.web.bind.annotation.RequestMethod.POST;

import javax.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.annotation.Secured;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import com.coinblesk.json.v1.UserAccountStatusTO;
import com.coinblesk.server.dto.ChangePasswordDTO;
import com.coinblesk.server.dto.UserAccountDTO;
import com.coinblesk.server.exceptions.BusinessException;
import com.coinblesk.server.exceptions.CoinbleskInternalError;
import com.coinblesk.server.service.MailService;
import com.coinblesk.server.service.UserAccountService;
import com.coinblesk.server.utils.ApiVersion;

/**
 * @author Thomas Bocek
 */
@RestController
@RequestMapping("/auth/common")
@ApiVersion({ "" })
@Secured({ ROLE_USER, ROLE_ADMIN })
public class AuthCommonController {

	private final static Logger LOG = LoggerFactory.getLogger(AuthCommonController.class);

	private final UserAccountService userAccountService;
	private final MailService mailService;

	@Autowired
	public AuthCommonController(UserAccountService userAccountService, MailService mailService) {
		this.userAccountService = userAccountService;
		this.mailService = mailService;
	}

	@RequestMapping(value = "/user-account", method = GET, produces = APPLICATION_JSON_UTF8_VALUE)
	@ResponseBody
	public UserAccountDTO getUserAccount() {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		LOG.debug("Get user account for {}", auth.getName());

		try {
			UserAccountDTO userAccount = userAccountService.getDTO(auth.getName());
			if (userAccount == null) {
				LOG.error("Someone tried to access a user account with an invalid email address: {}", auth);
				mailService.sendAdminMail("Wrong Account?",
						"Someone tried to access a user account with an invalid email address: " + auth);
				return null;
			}
			LOG.debug("GET user account success for {}", auth.getName());
			return userAccount;

		} catch (Exception e) {
			LOG.error("User create error", e);
			throw new CoinbleskInternalError("An internal error occurred."+e.getMessage());
		}
	}

	@RequestMapping(value = "/user-account", method = DELETE, produces = APPLICATION_JSON_UTF8_VALUE)
	@ResponseBody
	public UserAccountStatusTO deleteAccount() {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		LOG.debug("Delete account for {}", auth.getName());
		try {
			UserAccountStatusTO status = userAccountService.delete(auth.getName());
			if (!status.isSuccess()) {
				LOG.error("Someone tried a delete account with an invalid email address: {}/{}", auth, status.type().name());
				mailService.sendAdminMail("Wrong Delete Account?", "Someone tried a delete account with an invalid "
						+ "email address: "
						+ auth
						+ "/"
						+ status.type().name());
			}
			LOG.debug("Delete account success for {}", auth.getName());
			return status;

		} catch (Exception e) {
			LOG.error("User create error", e);
			return new UserAccountStatusTO().type(SERVER_ERROR).message(e.getMessage());
		}
	}

	@RequestMapping(value = "/user-account/change-password", method = POST, produces = APPLICATION_JSON_UTF8_VALUE, consumes = APPLICATION_JSON_UTF8_VALUE)
	public void changePassword(@Valid @RequestBody ChangePasswordDTO changePasswordDTO) throws BusinessException {

		Authentication auth = SecurityContextHolder.getContext().getAuthentication();

		if (auth != null && auth.getName() != null) {
			LOG.debug("Change password account for {}", auth.getName());
			userAccountService.changePassword(auth.getName(), changePasswordDTO.getNewPassword());

		} else {
			LOG.error("User is not logged in while trying to change the password");
			throw new CoinbleskInternalError("Account not found while changing the password.");
		}

	}

}