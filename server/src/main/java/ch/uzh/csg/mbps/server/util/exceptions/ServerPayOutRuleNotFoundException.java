package ch.uzh.csg.mbps.server.util.exceptions;

public class ServerPayOutRuleNotFoundException extends Exception {
	private static final long serialVersionUID = -2792038192898247153L;
	
	public ServerPayOutRuleNotFoundException() {
		super("No ServerPayOutRules for this ServerAccount.");
	}
}
