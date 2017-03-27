package com.coinblesk.server.dto;

import lombok.Data;

import javax.annotation.Nonnull;

public @Data class PaymentRequestDTO {
	@Nonnull private final String fromPublicKey;
	@Nonnull private final String toPublicKey;
	@Nonnull private final Long amount;
	private final long nonce;
}
