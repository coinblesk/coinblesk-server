package com.coinblesk.server.dto;

import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

public @Data class SignedDTO {
	@NotNull
	private final String payload;

	@NotNull
	@Valid
	private final SignatureDTO signature;
}
