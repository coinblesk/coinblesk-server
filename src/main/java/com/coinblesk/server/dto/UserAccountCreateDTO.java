package com.coinblesk.server.dto;

import javax.validation.constraints.NotNull;

import lombok.Data;

@Data
public class UserAccountCreateDTO {

	@NotNull
	private String email;

	@NotNull
	private String password;

}
