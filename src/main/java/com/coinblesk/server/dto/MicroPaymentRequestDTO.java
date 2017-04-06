package com.coinblesk.server.dto;

import lombok.Data;

public @Data class MicroPaymentRequestDTO {
	private final String tx;
	private final String toPublicKey;
	private final Long amount;
}
