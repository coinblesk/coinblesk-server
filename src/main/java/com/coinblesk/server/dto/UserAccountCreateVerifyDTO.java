package com.coinblesk.server.dto;

import javax.validation.constraints.NotNull;

import lombok.Data;

@Data
public class UserAccountCreateVerifyDTO {

	@NotNull
	private String email;

	@NotNull
	private String token;

}
