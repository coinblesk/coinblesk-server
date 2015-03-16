package ch.uzh.csg.coinblesk.server.util.exceptions;

public class UserAccountNotFoundException extends Exception {
	private static final long serialVersionUID = -4571797795332155191L;
	
	public UserAccountNotFoundException(String username) {
		super("The account with username " + username + " does not exist.");
	}
	
}
