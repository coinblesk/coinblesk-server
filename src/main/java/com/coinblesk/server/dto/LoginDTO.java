package com.coinblesk.server.dto;

import lombok.Data;

import static com.coinblesk.server.service.UserAccountService.EMAIL_PATTERN;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;

public @Data class LoginDTO {

	@Pattern(regexp = EMAIL_PATTERN)
	@NotNull
	private String username;

	@NotNull
	private String password;

	@Override
	public String toString() {
		return "LoginDTO{" + "password='*****'" + ", username='" + username + "'}";
	}
}
