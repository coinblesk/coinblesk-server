package com.coinblesk.server.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;

public @Data class CreateAddressResponseDTO {
	@NotNull final String clientPublicKey;
	@NotNull final String serverPublicKey;
	@NotNull final long lockTime;
}
