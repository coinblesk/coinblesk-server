package com.coinblesk.server.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;


public @Data class VirtualPaymentRequestDTO {
	@NotNull private final String fromPublicKey;
	@NotNull private final String toPublicKey;
	@NotNull private final Long amount;
	private final long nonce;
}
