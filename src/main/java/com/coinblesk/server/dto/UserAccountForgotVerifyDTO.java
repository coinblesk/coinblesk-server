package com.coinblesk.server.dto;

import javax.validation.constraints.NotNull;

import lombok.Data;

@Data
public class UserAccountForgotVerifyDTO {

	@NotNull
	private String email;

	@NotNull
	private String token;

	@NotNull
	private String newPassword;

}
