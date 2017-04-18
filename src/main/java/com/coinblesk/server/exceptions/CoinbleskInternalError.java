package com.coinblesk.server.exceptions;

import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;

import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(code = INTERNAL_SERVER_ERROR, reason = "An internal error occurred.")
public class CoinbleskInternalError extends RuntimeException {
	public CoinbleskInternalError(String s) {
		super(s);
	}
}
