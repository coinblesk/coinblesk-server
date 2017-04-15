package com.coinblesk.server.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;

@Data
public class LoginDTO {

	@NotNull
	private String username;

	@NotNull
	private String password;

	@Override
	public String toString() {
		return "LoginDTO{" + "password='*****'" + ", username='" + username + "'}";
	}
}
