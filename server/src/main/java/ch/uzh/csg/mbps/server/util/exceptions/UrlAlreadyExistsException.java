package ch.uzh.csg.mbps.server.util.exceptions;

public class UrlAlreadyExistsException extends Exception {
	private static final long serialVersionUID = -4728694732215091157L;
	
	public UrlAlreadyExistsException(String username) {
		super("The url \""+username+"\" does already exist!");
	}

}
