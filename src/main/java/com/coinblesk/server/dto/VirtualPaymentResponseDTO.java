package com.coinblesk.server.dto;

import lombok.Data;

@Data
public class VirtualPaymentResponseDTO {
	private final long amountTransfered;

	private final String publicKeySender;
	private final long newBalanceSender;

	private final String publicKeyReceiver;
	private final long newBalanceReceiver;

	private final long timeOfExecution;
}
