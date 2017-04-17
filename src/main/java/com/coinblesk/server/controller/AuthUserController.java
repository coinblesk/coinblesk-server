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

import static com.coinblesk.server.config.UserRole.ROLE_USER;
import static org.springframework.http.MediaType.APPLICATION_JSON_UTF8_VALUE;
import static org.springframework.web.bind.annotation.RequestMethod.POST;

import org.bitcoinj.core.ECKey;
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

import com.coinblesk.json.v1.BaseTO;
import com.coinblesk.json.v1.Type;
import com.coinblesk.json.v1.UserAccountTO;
import com.coinblesk.server.service.UserAccountService;
import com.coinblesk.server.utils.ApiVersion;

/**
 * @author Thomas Bocek
 */
@RestController
@RequestMapping(value = { "/auth/user" })
@ApiVersion({ "v1", "" })
@Secured(ROLE_USER)
public class AuthUserController {

	private final static Logger LOG = LoggerFactory.getLogger(AuthUserController.class);
	private final UserAccountService userAccountService;

	@Autowired
	public AuthUserController(UserAccountService userAccountService) {
		this.userAccountService = userAccountService;
	}

	@RequestMapping(value = "/transfer-p2sh", method = POST, produces = APPLICATION_JSON_UTF8_VALUE)
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

}
