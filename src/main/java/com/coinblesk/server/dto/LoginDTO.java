package com.coinblesk.server.dto;

import static com.coinblesk.server.service.UserAccountService.EMAIL_PATTERN;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;

public class LoginDTO {

	@Pattern(regexp = EMAIL_PATTERN)
	@NotNull
	private String username;

	@NotNull
	private String password;

	public String getUsername() {
		return username;
	}

	public void setUsername(String username) {
		this.username = username;
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	@Override
	public String toString() {
		return "LoginDTO{" + "password='*****'" + ", username='" + username + "'}";
	}
}
