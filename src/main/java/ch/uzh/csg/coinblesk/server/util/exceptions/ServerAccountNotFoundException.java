package ch.uzh.csg.coinblesk.server.util.exceptions;

public class ServerAccountNotFoundException extends Exception {
	private static final long serialVersionUID = -4147488137476492686L;

	public ServerAccountNotFoundException(String url){
		super("The server account with url " + url + " does not exist.");
	}
}
