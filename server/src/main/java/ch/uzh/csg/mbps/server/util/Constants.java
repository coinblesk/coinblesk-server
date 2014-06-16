package ch.uzh.csg.mbps.server.util;

import ch.uzh.csg.mbps.keys.CustomKeyPair;

/**
 * Class for saving constants of MBPS.
 */
public class Constants {
	public static final String INTERNAL_SERVER_ERROR = "Internal error occured. Please try again later.";
	
	public static CustomKeyPair SERVER_KEY_PAIR;
	
	/*
	 * The client version number helps to check if a version has been published
	 * in order to force the user to update the app. This assures that
	 * compatible versions are used. (see also server Constants.java)
	 */
	public static final int CLIENT_VERSION = 1;
	
	
}
