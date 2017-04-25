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

import static com.coinblesk.server.config.UserRole.ROLE_ADMIN;
import static com.coinblesk.server.config.UserRole.ROLE_USER;
import static com.coinblesk.server.enumerator.EventType.USER_ACCOUNT_COULD_NOT_BE_DELETED;
import static com.coinblesk.server.enumerator.EventType.USER_ACCOUNT_COULD_NOT_BE_PROVIDED;
import static com.coinblesk.server.enumerator.EventType.USER_ACCOUNT_PASSWORD_COULD_NOT_BE_CHANGED;
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
import org.springframework.web.bind.annotation.RestController;

import com.coinblesk.server.dto.UserAccountChangePasswordDTO;
import com.coinblesk.server.dto.UserAccountDTO;
import com.coinblesk.server.exceptions.BusinessException;
import com.coinblesk.server.exceptions.CoinbleskInternalError;
import com.coinblesk.server.service.EventService;
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
	private final EventService eventService;

	@Autowired
	public AuthCommonController(UserAccountService userAccountService, EventService eventService) {
		this.userAccountService = userAccountService;
		this.eventService = eventService;
	}

	@RequestMapping(value = "/user-account", method = GET, produces = APPLICATION_JSON_UTF8_VALUE)
	public UserAccountDTO getUserAccount() throws BusinessException {

		UserAccountDTO userAccount;
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		LOG.debug("Get user account for {}", auth.getName());

		try {
			userAccount = userAccountService.getDTO(auth.getName());

		} catch(BusinessException exception) {
			LOG.error("Someone tried to access a user account with an invalid email address: {}", auth);
			eventService.warn(USER_ACCOUNT_COULD_NOT_BE_PROVIDED, "Someone tried to access a user account with an invalid email address: " + auth + " / " + exception.getClass().getSimpleName());
			throw exception;
		}

		LOG.debug("GET user account success for {}", auth.getName());
		return userAccount;
	}

	@RequestMapping(value = "/user-account", method = DELETE)
	public void deleteAccount() throws BusinessException {

		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		LOG.debug("Delete account for {}", auth.getName());

		try {
			userAccountService.delete(auth.getName());

		} catch(BusinessException exception) {
			LOG.error("Someone tried a delete account with an invalid email address: {} - {}", auth, exception.getClass().getSimpleName());
			eventService.warn(USER_ACCOUNT_COULD_NOT_BE_DELETED, "Someone tried a delete account with an invalid email address: " + auth + "/" + exception.getClass().getSimpleName());
			throw exception;
		}
		LOG.debug("Delete account success for {}", auth.getName());
	}

	@RequestMapping(value = "/user-account/change-password", method = POST, consumes = APPLICATION_JSON_UTF8_VALUE)
	public void changePassword(@Valid @RequestBody UserAccountChangePasswordDTO changePasswordDTO) throws BusinessException {

		Authentication auth = SecurityContextHolder.getContext().getAuthentication();

		if (auth != null && auth.getName() != null) {
			LOG.debug("Change password account for {}", auth.getName());
			try {
				userAccountService.changePassword(auth.getName(), changePasswordDTO.getNewPassword());
			} catch(BusinessException exception) {
				eventService.warn(USER_ACCOUNT_PASSWORD_COULD_NOT_BE_CHANGED, "Password of '" + auth.getName() + "' could not be changed, because: " + exception.getClass().getSimpleName());
			}
			LOG.debug("Change password account success for {}", auth.getName());

		} else {
			LOG.error("User is not logged in while trying to change the password");
			throw new CoinbleskInternalError("Account not found while changing the password.");
		}

	}

}