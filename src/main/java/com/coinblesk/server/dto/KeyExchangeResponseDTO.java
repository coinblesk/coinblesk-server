package com.coinblesk.server.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;

public @Data class KeyExchangeResponseDTO {
	@NotNull private final String serverPublicKey;
}
