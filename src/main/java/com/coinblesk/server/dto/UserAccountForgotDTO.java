package com.coinblesk.server.dto;

import javax.validation.constraints.NotNull;

import lombok.Data;

@Data
public class UserAccountForgotDTO {

	@NotNull
	private String email;

}
