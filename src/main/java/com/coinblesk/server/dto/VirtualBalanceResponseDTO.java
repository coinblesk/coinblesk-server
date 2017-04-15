package com.coinblesk.server.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;

@Data
public class VirtualBalanceResponseDTO {
	@NotNull
	private final long balance;
}
