package com.coinblesk.server.exceptions;

public class UserNotFoundException extends Exception {
	public UserNotFoundException(String s) {
		super("Could not find user with public key " + s);
	}
}
