package ch.uzh.csg.mbps.server.clientinterface;

import java.util.List;

import ch.uzh.csg.mbps.server.domain.ServerAccountTasks;

public interface IServerAccountTasks {


	/**
	 * Persists the server account data of not existing account for hourly task. 
	 * 
	 * @param url
	 * @param username
	 * @param email
	 */
	public void persistsCreateNewAccount(String url, String username, String email);

	/**
	 * Persists the server account data of not existing account with payout address for hourly task.
	 * 
	 * @param url
	 * @param username
	 * @param email
	 * @param payoutAddress
	 */
	public void persistsCreateNewAccountPayOutAddress(String url, String username, String email, String payoutAddress);

	/**
	 * 
	 * @param url
	 * @return ServerAccountTasks
	 */
	public ServerAccountTasks getAccountTasksCreateByUrl(String url);

	/**
	 * 
	 * @param token
	 * @return ServerAccountTasks
	 */
	public ServerAccountTasks getAccountTasksByToken(String token);

	/**
	 * 
	 * @param type
	 * @param token
	 */
	public void deleteTask(int type, String token);

	/**
	 * 
	 * @param url
	 * @return
	 */
	public boolean checkIfExists(String url);

	/**
	 * This method is called by the HourlyTask to request failed request to create new Account
	 * 
	 * @param type
	 * @return List of ServerAccountTasks
	 */
	public List<ServerAccountTasks> processNewAccountTask(int type);

	/**
	 * Gets all tasks which are proceed.
	 * 
	 * @return List of ServerAccountTasks
	 */
	public List<ServerAccountTasks> getProceedAccounts();

	/**
	 * Change value from not proceed to proceed.
	 * 
	 * @param token
	 */
	public void updateProceed(String token);

}
