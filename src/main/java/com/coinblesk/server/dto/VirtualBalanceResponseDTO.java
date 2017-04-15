package com.coinblesk.server.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;

public @Data class VirtualBalanceResponseDTO {
	@NotNull private final long balance;
}
