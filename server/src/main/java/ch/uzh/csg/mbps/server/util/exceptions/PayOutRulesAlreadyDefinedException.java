package ch.uzh.csg.mbps.server.util.exceptions;

public class PayOutRulesAlreadyDefinedException extends Exception {
	private static final long serialVersionUID = 4155674502535515364L;

	public PayOutRulesAlreadyDefinedException() {
		super("You already defined your payout rules. Please reset first to create new rules.");
	}
	
}
