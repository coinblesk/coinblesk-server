package com.coinblesk.server.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;

@Data
public class SignatureDTO {
	@NotNull
	private final String sigR;
	@NotNull
	private final String sigS;
}
