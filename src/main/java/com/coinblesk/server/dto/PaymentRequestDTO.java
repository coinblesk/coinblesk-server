package com.coinblesk.server.dto;

import lombok.Data;

import javax.annotation.Nonnull;

public @Data class PaymentRequestDTO implements Signable {
	@Nonnull private final String fromPublicKey;
	@Nonnull private final String toPublicKey;
	@Nonnull private final Long amount;
	private final long nonce;

	@Override
	public String getPublicKeyForSigning() {
		return this.fromPublicKey;
	}
}
