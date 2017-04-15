package com.coinblesk.server.dto;

import lombok.Data;

import java.util.Date;
import java.util.List;

@Data
public class AccountDTO {
	private final String publicKeyClient;
	private final String publicKeyServer;
	private final String privateKeyServer;
	private final Date timeCreated;
	private final long virtualBalance;
	private final long satoshiBalance;
	private final long totalBalance;
	private final List<TimeLockedAddressDTO> timeLockedAddresses;
}
