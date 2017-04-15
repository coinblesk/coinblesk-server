package com.coinblesk.server.dto;

import lombok.Data;

@Data
public class MicroPaymentRequestDTO {
	private final String tx;
	private final String fromPublicKey;
	private final String toPublicKey;
	private final Long amount;
	private final Long nonce;
}
