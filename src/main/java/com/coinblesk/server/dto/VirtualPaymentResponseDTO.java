package com.coinblesk.server.dto;

import lombok.Data;

public @Data class VirtualPaymentResponseDTO  {
	private final long amountTransfered;

	private final String publicKeySender;
	private final long newBalanceSender;

	private final String publicKeyReceiver;
	private final long newBalanceReceiver;

	private final long timeOfExecution;
}
