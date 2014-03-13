package ch.uzh.csg.mbps.server.util.exceptions;

public class BalanceNotZeroException extends Exception {
	private static final long serialVersionUID = 912722334911456154L;
	
	public BalanceNotZeroException() {
		super("Account cannot be deleted. Balance is not zero.");
	}
}
