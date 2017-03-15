package com.coinblesk.server.config;

import static springfox.documentation.builders.PathSelectors.regex;
import static springfox.documentation.builders.RequestHandlerSelectors.any;
import static springfox.documentation.spi.DocumentationType.SWAGGER_2;

import java.util.ArrayList;
import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import springfox.documentation.service.ApiInfo;
import springfox.documentation.service.ApiKey;
import springfox.documentation.service.Contact;
import springfox.documentation.spring.web.plugins.Docket;
import springfox.documentation.swagger2.annotations.EnableSwagger2;

@Configuration
@EnableSwagger2
public class SwaggerConfig {

	@Bean
	public Docket api() {
		return new Docket(SWAGGER_2)
				.groupName("full-api")
				.apiInfo(apiInfo())
				.select()
				.apis(any())
				.paths(regex("/v[12]/.*"))
				.build()
				.securitySchemes(apiKeys());
	}

	private ApiInfo apiInfo() {
		ApiInfo apiInfo = new ApiInfo(
				"Coinblesk Server REST API",
				"REST API for the Android App and the Web Interface",
				"",
				"",
				new Contact(
						"Communication Systems Research Group CSG, University of Zurich",
						"https://bitcoin.csg.uzh.ch",
						"bocek@ifi.uzh.ch"),
				"",
				"");

		return apiInfo;
	}

	private List<ApiKey> apiKeys() {
		List<ApiKey> apiKeys = new ArrayList<>();
		// "header" & "query" are possible
		apiKeys.add(new ApiKey("Authorization", "Authorization", "header"));
		return apiKeys;
	}


}