package ch.uzh.csg.coinblesk.server.util.exceptions;

public class VerificationTokenNotFoundException extends Exception {
	private static final long serialVersionUID = 2760481407675302207L;

	public VerificationTokenNotFoundException(String token) {
		super("Could not find the verification token "+token);
	}
	
}
