package com.coinblesk.server.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;

public @Data class VirtualBalanceRequestDTO {
	@NotNull private final String publicKey;
}
