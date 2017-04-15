package com.coinblesk.server.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;

@Data
public class KeyExchangeResponseDTO {
	@NotNull
	private final String serverPublicKey;
}
