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

import com.coinblesk.json.v1.FeeTO;
import com.coinblesk.server.service.FeeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import static com.coinblesk.json.v1.Type.SERVER_ERROR;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.MediaType.APPLICATION_JSON_UTF8_VALUE;
import static org.springframework.web.bind.annotation.RequestMethod.GET;

/**
 * Controller for serving Tx fee requests.
 */
@RestController
public class FeeController {

	private static final Logger LOG = LoggerFactory.getLogger(FeeController.class);

	private final FeeService feeService;

	@Autowired
	public FeeController(FeeService feeService) {
		this.feeService = feeService;
	}

	@RequestMapping(value = "/fee", method = GET, produces = APPLICATION_JSON_UTF8_VALUE)
	@ResponseBody
	public ResponseEntity<FeeTO> fee() {
		FeeTO output = new FeeTO();
		try {
			output.fee(feeService.fee());
			return new ResponseEntity<>(output, OK);
		} catch (Exception e) {
			LOG.error("{fee} - SERVER_ERROR - exception: ", e);
			output.type(SERVER_ERROR);
			output.message(e.getMessage());
			return new ResponseEntity<>(output, BAD_REQUEST);
		}
	}
}
