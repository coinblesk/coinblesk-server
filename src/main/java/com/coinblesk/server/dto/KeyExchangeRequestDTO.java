package com.coinblesk.server.dto;

import lombok.Data;

import javax.validation.constraints.Size;

@Data
public class KeyExchangeRequestDTO {
	// In ECDSA public key in compressed form as hex
	@Size(min = 66, max = 66, message = "publicKey must be 33 bytes long in hex format (string of length 66)")
	public final String publicKey;
}
