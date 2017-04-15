package com.coinblesk.server.exceptions;

public class MissingFieldException extends RuntimeException {

	private final String fieldName;

	public MissingFieldException(String fieldName) {
		super("Field " + fieldName + " is missing");
		this.fieldName = fieldName;
	}

}
