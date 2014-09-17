package ch.uzh.csg.mbps.server.clientinterface;

import java.util.Date;
import java.util.List;

import ch.uzh.csg.mbps.server.domain.ServerAccountTasks;
import ch.uzh.csg.mbps.server.domain.UserAccount;

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

	/**
	 * Persists the server account data for a upgrade of trust level for hourly task.
	 * 
	 * @param url
	 * @param username
	 * @param email
	 */
	public void persistsUpgradeAccount(String url, String username, String email, int trustLevel);

	/**
	 * 
	 * @param url
	 * @param date
	 * @return Server account tasks
	 */
	public ServerAccountTasks getAccountTaskByUrlAndDate(String url, Date date);

	/**
	 * Persists the server account data for a downgrade of trust level for hourly task.
	 * 
	 * @param url
	 * @param date
	 * @return Server account task
	 */
	public void persistsDowngradeAccount(String url, String username,String email, Integer trustLevel);

	/**
	 * Get all not proceeded accounts by a given parameter type.
	 * 
	 * @param type
	 * @return ServerAccountTasks
	 */
	public List<ServerAccountTasks> getAccountsByType(int type);

	/**
	 * Sends data to other server and gets an answer back. 
	 * The process of creating new server account. 
	 * 
	 * 
	 * @param url
	 * @param email
	 * @param user
	 * @param token
	 * @throws Exception
	 */
	public void createNewAccount(String url, String email, UserAccount user,String token) throws Exception;

	/**
	 * Downgrade trust level 
	 * 
	 * @param username
	 * @param email
	 * @param url
	 * @param trustLevel
	 * @param token
	 * @throws Exception
	 */
	public void downgradeTrustLevel(String username, String email, String url,Integer trustLevel, String token) throws Exception;

	/**
	 * Upgrade trust level 
	 * 
	 * @param string
	 * @param string2
	 * @param string3
	 * @param trustLevel 
	 * @throws Exception 
	 */
	public void upgradedTrustLevel(String username, String email, String url,int trustLevel, String token) throws Exception;

	/**
	 * Remove the database entries which are accomplished successfully.
	 * 
	 * @param token
	 * @return boolean
	 */
	public boolean removeProceedTasks(String token);

	/**
	 * Creates a new payout address and sends to another server. Receives also a payout address.
	 * 
	 * @param url
	 * @param email
	 * @param user
	 * @param cpk
	 * @param token
	 * @throws Exception
	 */
	public void updatedPayoutAddress(String url, String email, UserAccount user,String token) throws Exception;

}
