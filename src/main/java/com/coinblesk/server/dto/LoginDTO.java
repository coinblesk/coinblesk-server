package com.coinblesk.server.dto;

import javax.validation.constraints.NotNull;

import lombok.Data;
import lombok.ToString;

@Data
@ToString(exclude = "password")
public class LoginDTO {

	@NotNull
	private String email;

	@NotNull
	private String password;

}
