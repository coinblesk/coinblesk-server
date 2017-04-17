package com.coinblesk.server.dto;

import javax.validation.constraints.NotNull;

import lombok.Data;

@Data
public class LoginDTO {

	@NotNull
	private String email;

	@NotNull
	private String password;

	@Override
	public String toString() {
		return "LoginDTO{" + "password='*****'" + ", email='" + email + "'}";
	}
}
