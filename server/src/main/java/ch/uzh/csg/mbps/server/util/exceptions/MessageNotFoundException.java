package ch.uzh.csg.mbps.server.util.exceptions;

import java.util.Date;

public class MessageNotFoundException extends Exception {
	private static final long serialVersionUID = 7576676881671365435L;

	public MessageNotFoundException() {}

	public MessageNotFoundException(Long id){
		super("The message with id " + id + " does not exist.");
	}
	
	public MessageNotFoundException(String subject, Date date){
		super("The message with subject " + subject + " and timestamp "+ date +" does not exist.");
	}
}