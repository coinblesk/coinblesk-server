package com.coinblesk.server.controller;

import static com.coinblesk.server.config.Constants.PROFILE_PROD;
import static org.springframework.http.MediaType.TEXT_HTML_VALUE;
import static org.springframework.web.bind.annotation.RequestMethod.GET;

import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("!" + PROFILE_PROD)
@RequestMapping("/debug")
public class DebugController {

	@RequestMapping(value = "", method = GET, produces = TEXT_HTML_VALUE)
	public Resource index() {
		return new ClassPathResource("debug/index.html");
	}

	@RequestMapping(value = "/accounts", method = GET, produces = TEXT_HTML_VALUE)
	public Resource accounts() {
		return new ClassPathResource("debug/accounts.html");
	}

	@RequestMapping(value = "/bitcoin.js", method = GET)
	public Resource bitcoinJs() {
		return new ClassPathResource("debug/bitcoinjs.min.js");
	}

}