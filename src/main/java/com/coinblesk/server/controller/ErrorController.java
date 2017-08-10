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

import static com.coinblesk.server.config.Constants.PROFILE_PROD;
import static com.coinblesk.server.config.Constants.PROFILE_TEST;
import static java.util.Arrays.asList;

import java.util.Date;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.web.ErrorAttributes;
import org.springframework.core.env.Environment;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.coinblesk.dto.GeneralErrorDTO;

/*
 * Controller for displaying a neat error JSON if the user opens a page
 * which is not available in the api (unmapped), forbidden etc.
 *
 */
@RestController
@RequestMapping(value = "/error")
public class ErrorController implements org.springframework.boot.autoconfigure.web.ErrorController {

	private final ErrorAttributes errorAttributes;
	private final Environment env;

	@Autowired
	public ErrorController(ErrorAttributes errorAttributes, Environment env) {
		Assert.notNull(errorAttributes, "ErrorAttributes cannot be null");
		this.errorAttributes = errorAttributes;
		this.env = env;
	}

	@Override
	public String getErrorPath() {
		return "/error";
	}

	@RequestMapping
	public GeneralErrorDTO error(HttpServletRequest request) {
		Map<String, Object> body = getErrorAttributes(request);

		GeneralErrorDTO result = new GeneralErrorDTO();
		result.setTimestamp(body.get("timestamp") != null ? (Date) body.get("timestamp") : new Date());
		if (body.get("status") != null) {
			result.setStatus((Integer) body.get("status"));
		}
		if (body.get("error") != null) {
			result.setError((String) body.get("error"));
		}
		if (body.get("path") != null) {
			result.setPath((String) body.get("path"));
		}
		// mask the reason behind the exception on PROD
		if (!asList(env.getActiveProfiles()).contains(PROFILE_PROD) && !asList(env.getActiveProfiles()).contains(PROFILE_TEST)) {
			if (body.get("message") != null) {
				result.setMessage((String) body.get("message"));
			}
		}
		return result;
	}

	private Map<String, Object> getErrorAttributes(HttpServletRequest request) {
		RequestAttributes requestAttributes = new ServletRequestAttributes(request);
		return errorAttributes.getErrorAttributes(requestAttributes, false);
	}

}
