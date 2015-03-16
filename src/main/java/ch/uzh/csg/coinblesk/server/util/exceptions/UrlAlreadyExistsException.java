package ch.uzh.csg.coinblesk.server.util.exceptions;

public class UrlAlreadyExistsException extends Exception {
	private static final long serialVersionUID = -4728694732215091157L;
	
	public UrlAlreadyExistsException(String url) {
		super("The url \""+url+"\" does already exist!");
	}

}
