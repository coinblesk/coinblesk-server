package com.coinblesk.server.dto;

import lombok.Data;

import java.util.Date;

public @Data class TimeLockedAddressDTO {
	private final String bitcoinAddress;
	private final String adddressUrl;
	private final Date createdAt;
	private final Date lockedUntil;
	private final Boolean locked;
	private final Long balance;
}
