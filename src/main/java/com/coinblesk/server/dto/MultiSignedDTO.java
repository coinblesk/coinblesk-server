package com.coinblesk.server.dto;

import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

public @Data class MultiSignedDTO {
	@NotNull
	private final String payload;

	@NotNull
	@Valid
	private final SignatureDTO signatureForSender;

	@NotNull
	@Valid
	private final SignatureDTO signatureForReceiver;
}
