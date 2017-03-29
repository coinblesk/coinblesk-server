package com.coinblesk.server.exceptions;


public class InvalidSignatureException extends RuntimeException {
	public InvalidSignatureException(String s) {
		super(s);
	}
}
