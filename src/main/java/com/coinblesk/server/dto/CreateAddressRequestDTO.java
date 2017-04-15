package com.coinblesk.server.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;

@Data
public class CreateAddressRequestDTO {
	@NotNull
	private final String publicKey;
	private final long lockTime;
}
