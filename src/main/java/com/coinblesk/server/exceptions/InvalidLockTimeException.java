package com.coinblesk.server.exceptions;

public class InvalidLockTimeException extends Exception {
	public InvalidLockTimeException() {
		super("Lock time is invalid");
	}
}
