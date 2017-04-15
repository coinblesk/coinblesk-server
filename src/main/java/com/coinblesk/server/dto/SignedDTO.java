package com.coinblesk.server.dto;

import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

@Data
public class SignedDTO {
	// Payload contains Base64URL encoded json
	@NotNull
	private final String payload;

	// Signature contains the signature of the payload
	@NotNull
	@Valid
	private final SignatureDTO signature;
}
