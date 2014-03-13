package ch.uzh.csg.mbps.server.util.exceptions;

public class EmailAlreadyExistsException extends Exception {
	private static final long serialVersionUID = 5098463968026321653L;

	public EmailAlreadyExistsException(String email) {
		super("The emailaddress \""+email+"\" does already exist!");
	}
	
}
