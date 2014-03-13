package ch.uzh.csg.mbps.server.util.exceptions;

public class PayOutRuleNotFoundException extends Exception {
	private static final long serialVersionUID = 4155674502535515364L;

	public PayOutRuleNotFoundException() {
		super("No PayOutRules for this UserAccount.");
	}
	
}
