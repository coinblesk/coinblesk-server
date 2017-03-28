package com.coinblesk.server.dto;

import lombok.Data;

import javax.annotation.Nonnull;

public @Data class CreateAddressRequestDTO implements Signable {
	@Nonnull private final String publicKey;
	private final long lockTime;

	@Override
	public String getPublicKeyForSigning() {
		return this.publicKey;
	}
}
