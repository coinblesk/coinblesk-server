package com.coinblesk.server.config;

import static com.coinblesk.server.config.Constants.PROFILE_PROD;
import static com.coinblesk.server.config.Constants.PROFILE_TEST;
import static springfox.documentation.builders.PathSelectors.regex;
import static springfox.documentation.builders.RequestHandlerSelectors.any;
import static springfox.documentation.spi.DocumentationType.SWAGGER_2;

import java.util.ArrayList;
import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import springfox.documentation.service.ApiInfo;
import springfox.documentation.service.ApiKey;
import springfox.documentation.service.Contact;
import springfox.documentation.spring.web.plugins.Docket;
import springfox.documentation.swagger2.annotations.EnableSwagger2;

@Configuration
@EnableSwagger2
@Profile({"!" + PROFILE_PROD, "!" + PROFILE_TEST})
public class SwaggerConfig {

	@Bean
	public Docket fullApi() {
		return new Docket(SWAGGER_2)
				.groupName("whole-api")
				.apiInfo(apiInfo())
				.select()
				.apis(any())
				// do not include default spring /error page and /debug,
				// see https://regex101.com/r/hHdVDj/3
				.paths(regex("^(?!\\/error$|\\/debug).*$"))
				.build()
				.securitySchemes(apiKeys());
	}

	@Bean
	// shows everything except /v1
	public Docket nonV1Api() {
		return new Docket(SWAGGER_2)
				.groupName("non-v1-api")
				.apiInfo(apiInfo())
				.select()
				.apis(any())
				// do not include default spring /error page, /debug (and /v1),
				// see  https://regex101.com/r/hHdVDj/4
				.paths(regex("^(?!(\\/v1|\\/error$|\\/debug)).*$"))
				.build()
				.securitySchemes(apiKeys());
	}

	private ApiInfo apiInfo() {
		ApiInfo apiInfo = new ApiInfo(
				"Coinblesk Server REST API", "REST API for the Android App and the Web Interface",
				"", "", new Contact("Communication Systems Research Group CSG, University of Zurich",
				"https://bitcoin.csg.uzh.ch", "bocek@ifi.uzh.ch"), "", "");
		return apiInfo;
	}

	private List<ApiKey> apiKeys() {
		List<ApiKey> apiKeys = new ArrayList<>();
		// "header" & "query" are possible
		apiKeys.add(new ApiKey("Authorization", "Authorization", "header"));
		return apiKeys;
	}

}