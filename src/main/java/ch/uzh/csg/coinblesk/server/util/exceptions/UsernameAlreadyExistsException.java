package ch.uzh.csg.coinblesk.server.util.exceptions;

public class UsernameAlreadyExistsException extends Exception {
	private static final long serialVersionUID = -6189949262424079424L;

	public UsernameAlreadyExistsException(String username) {
		super("The username \""+username+"\" does already exist!");
	}
	
}
