package ch.uzh.csg.mbps.server.util.exceptions;

public class ServerPayOutRulesAlreadyDefinedException extends Exception {
	private static final long serialVersionUID = 5303889179434066072L;

	public ServerPayOutRulesAlreadyDefinedException() {
		super("You already defined your server payout rules. Please reset first to create new rules.");
	}
}
