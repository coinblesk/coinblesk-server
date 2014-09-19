package ch.uzh.csg.mbps.server.util;

/**
 * Splits the username which contains the actual username
 *  and server url to an username without server url and vice versa.
 * 
 *
 */
public class SplitNameHandler {

	private static SplitNameHandler instance;
	
	private SplitNameHandler(){ }
	
	public static SplitNameHandler getInstance(){
		if(instance==null){
			instance = new SplitNameHandler();
		}
		return instance;
	}
	
	public String getUsername(String fullString){
		int splitIndex = fullString.indexOf(Config.SPLIT_USERNAME);
		return fullString.substring(0, splitIndex);
	}
	
	public String getServerUrl(String fullString){
		int splitIndex = fullString.indexOf(Config.SPLIT_USERNAME);
		return fullString.substring(splitIndex + 1);
		
	}
}
