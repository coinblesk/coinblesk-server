package ch.uzh.csg.coinblesk.server.clientinterface;

import java.util.List;

import ch.uzh.csg.coinblesk.server.domain.Activities;

public interface IActivities {
	/**
	 * Creates a log entry.
	 * 
	 * @param username
	 * @param title
	 * @param message
	 */
	public void activityLog(String username, String subject, String message);

	/**
	 * Returns log activities order by timestamp (desc)
	 * 
	 * @param page
	 * @return
	 */
	public List<Activities> getLogs(int page);
}
