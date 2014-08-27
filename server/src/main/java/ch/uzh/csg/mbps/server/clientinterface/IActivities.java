package ch.uzh.csg.mbps.server.clientinterface;

import java.util.List;

import ch.uzh.csg.mbps.server.domain.Activities;

public interface IActivities {

	//TODO: mehmet test & javadoc
	
	/**
	 * 
	 * @param username
	 * @param title
	 * @param message
	 */
	public void activityLog(String username, String title, String message);

	/**
	 * 
	 * @param page
	 * @return
	 */
	public List<Activities> getLogs(int page);
}
