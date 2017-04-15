package com.coinblesk.server.exceptions;

public class MissingFieldException extends RuntimeException {

	public MissingFieldException(String fieldName) {
		super("Field " + fieldName + " is missing");
	}

}
