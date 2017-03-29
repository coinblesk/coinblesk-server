package com.coinblesk.server.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;

public @Data class CreateAddressRequestDTO implements Signable {
	@NotNull private final String publicKey;
	private final long lockTime;

	@Override
	public String getPublicKeyForSigning() {
		return this.publicKey;
	}
}