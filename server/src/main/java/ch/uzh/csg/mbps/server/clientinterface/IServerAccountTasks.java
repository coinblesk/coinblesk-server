package ch.uzh.csg.mbps.server.clientinterface;

import ch.uzh.csg.mbps.server.domain.ServerAccount;
import ch.uzh.csg.mbps.server.domain.ServerAccountTasks;

public interface IServerAccountTasks {


	/**
	 * Persists the server account data of not existing account into the db. 
	 * 
	 * @param account
	 * @param username
	 * @param email
	 */
	public void persistsCreateNewAccount(ServerAccount account, String username, String email);

	/**
	 * 
	 * @param url
	 * @return ServerAccountTasks
	 */
	public ServerAccountTasks getAccountTasksByUrl(String url);

	/**
	 * 
	 * @param token
	 * @return ServerAccountTasks
	 */
	public ServerAccountTasks getAccountTasksByToken(String token);

	/**
	 * 
	 * @param url
	 */
	public void deleteCreateNewAccount(String url);

	/**
	 * 
	 * @param url
	 * @return
	 */
	public boolean checkIfExists(String url);

}
