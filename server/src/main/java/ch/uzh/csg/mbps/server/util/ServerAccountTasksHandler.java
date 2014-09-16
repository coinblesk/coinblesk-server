package ch.uzh.csg.mbps.server.util;

import java.io.IOException;

import net.minidev.json.JSONObject;

import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.util.EntityUtils;
import org.springframework.beans.factory.annotation.Autowired;

import ch.uzh.csg.mbps.customserialization.PKIAlgorithm;
import ch.uzh.csg.mbps.customserialization.exceptions.UnknownPKIAlgorithmException;
import ch.uzh.csg.mbps.keys.CustomPublicKey;
import ch.uzh.csg.mbps.responseobject.TransferObject;
import ch.uzh.csg.mbps.server.clientinterface.IActivities;
import ch.uzh.csg.mbps.server.clientinterface.IServerAccount;
import ch.uzh.csg.mbps.server.clientinterface.IServerAccountTasks;
import ch.uzh.csg.mbps.server.dao.ServerPublicKeyDAO;
import ch.uzh.csg.mbps.server.domain.ServerAccount;
import ch.uzh.csg.mbps.server.domain.ServerAccountTasks;
import ch.uzh.csg.mbps.server.domain.ServerPublicKey;
import ch.uzh.csg.mbps.server.domain.UserAccount;
import ch.uzh.csg.mbps.server.response.HttpRequestHandler;
import ch.uzh.csg.mbps.server.service.ServerAccountTasksService;
import ch.uzh.csg.mbps.server.util.exceptions.InvalidUrlException;
import ch.uzh.csg.mbps.server.util.exceptions.ServerAccountNotFoundException;
import ch.uzh.csg.mbps.server.util.exceptions.UserAccountNotFoundException;
import ch.uzh.csg.mbps.server.web.response.CreateSAObject;
import ch.uzh.csg.mbps.server.web.response.ServerAccountObject;

/**
 * This class creates a new server account into the db 
 * by communicating with the other server.
 *
 */
public class ServerAccountTasksHandler {

	@Autowired
	private IServerAccount serverAccountService;
	@Autowired
	private IActivities activitiesService;
	@Autowired
	private IServerAccountTasks serverAccountTasksService;
	@Autowired
	private ServerPublicKeyDAO serverPublicKeyDAO;
	
	private static ServerAccountTasksHandler instance = null;
	
	public static ServerAccountTasksHandler getInstance(){
		if(instance == null){
			instance = new ServerAccountTasksHandler();
		}
		
		return instance;
	}
	
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
	public void createNewAccount(String url, String email, UserAccount user, String token) throws Exception {
		//Get custom public key of the server
		CustomPublicKey cpk = new CustomPublicKey(Constants.SERVER_KEY_PAIR.getKeyNumber(), 
				Constants.SERVER_KEY_PAIR.getPkiAlgorithm(), Constants.SERVER_KEY_PAIR.getPublicKey());
		CreateSAObject create = new CreateSAObject();
		create.setUrl(SecurityConfig.BASE_URL);
		create.setEmail(user.getEmail());
		create.setCustomPublicKey(cpk);
		//encode object to json
		JSONObject jsonObj = new JSONObject();
		try {
			create.encode(jsonObj);
		} catch (Exception e) {
			throw new Exception(e.getMessage());
		}

		CloseableHttpResponse resBody;
		CreateSAObject csao = new CreateSAObject();
		String urlCreate = url+ Config.CREATE_NEW_SERVER;
		try {
			//execute post request
			resBody = HttpRequestHandler.prepPostResponse(jsonObj, urlCreate);
			try {
				HttpEntity entity1 = resBody.getEntity();
				String respString = EntityUtils.toString(entity1);
				if(respString != null && respString.trim().length() > 0) {
					csao.decode(respString);
				} else {
					//if response not correct store account into db for hourly tasks
					if(token == null)
						serverAccountTasksService.persistsCreateNewAccount(url, user.getUsername(), email);
				}
			} catch (Exception e) {
				//if response not correct store account into db for hourly tasks
				if(token == null)
					serverAccountTasksService.persistsCreateNewAccount(url, user.getUsername(), email);
				throw new Exception(e.getMessage());
			} finally {
				resBody.close();
			}
		} catch (IOException e) {
			//if response not correct store account into db for hourly tasks
			if(token == null)
				serverAccountTasksService.persistsCreateNewAccount(url, user.getUsername(), email);
			throw new IOException(e.getMessage());
		}		
		
		if (csao.isSuccessful()) {
			// if urls are different throw exception
			if (url != csao.getUrl()) {
				if (token == null)
					serverAccountTasksService.persistsCreateNewAccount(url, user.getUsername(), email);
				
				throw new InvalidUrlException();
			}
			ServerAccount serverAccount = new ServerAccount(csao.getUrl(),email);

			// if serveraccount is deleted undo delete
			if (serverAccountService.isDeletedByUrl(csao.getUrl())) {
				serverAccountService.undeleteServerAccountByUrl(csao.getUrl());
			} else {
				boolean success = serverAccountService.persistAccount(serverAccount);
				// if creation failed throw exception
				if (!success) {
					if (token == null)
						serverAccountTasksService.persistsCreateNewAccount(url, user.getUsername(), email);
					
					throw new Exception();
				}
				PKIAlgorithm pkiAlgorithm;
				try {
					pkiAlgorithm = PKIAlgorithm.getPKIAlgorithm(cpk.getPkiAlgorithm());
				} catch (UnknownPKIAlgorithmException e1) {
					if(token == null)
						serverAccountTasksService.persistsCreateNewAccount(url, user.getUsername(), email);
					throw new UnknownPKIAlgorithmException();
				}
				serverPublicKeyDAO.saveUserPublicKey(csao.getId(), pkiAlgorithm, csao.getCustomPublicKey().getPublicKey());
			}
			
			try {
				updatedPayoutAddress(csao.getUrl(), email, user, token);
			} catch (Exception e) {
				if (token == null)
					serverAccountTasksService.persistsCreateNewAccount(url, user.getUsername(), email);
				
				throw new Exception();
			}
			
			if(token != null)
				serverAccountTasksService.updateProceed(token);
		} else {
			if (token == null)
				serverAccountTasksService.persistsCreateNewAccount(url, user.getUsername(), email);

			activitiesService.activityLog(user.getUsername(), Subjects.FAILED_CREATE_SERVER_ACCOUNT,"Failed to create a new relation with the server " + url + " and email " + email);
		}
		activitiesService.activityLog(user.getUsername(), Subjects.CREATE_SERVER_ACCOUNT,"Create a new relation with the server " + url + " and email " + email);
	}
	
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
	public void updatedPayoutAddress(String url, String email, UserAccount user, String token) throws Exception{
		ServerAccount account;
		try {
			account = serverAccountService.getByUrl(url);
		} catch (ServerAccountNotFoundException e1) {
			throw new ServerAccountNotFoundException(url);
		}
		ServerAccountObject createAccount = new ServerAccountObject();
		createAccount.setUrl(account.getUrl());
		createAccount.setEmail(account.getEmail());
		// The payin address for a new server relation is created 
		createAccount.setPayoutAddress(account.getPayinAddress());

		JSONObject jsonAccount = new JSONObject();
		try {
			createAccount.encode(jsonAccount);
		} catch (Exception e) {
			if(token == null)
				serverAccountTasksService.persistsCreateNewAccountPayOutAddress(url, user.getUsername(), email, createAccount.getPayinAddress());
				
			throw new Exception(e.getMessage());
		}
		
		ServerAccountObject sao = new ServerAccountObject();
		
		// The http request is prepared and send with the information$
		//TODO: mehmet sign object
		CloseableHttpResponse resBody2;
		String urlCreateData = url+ Config.CREATE_NEW_SERVER_PUBLIC_KEY;
		try {
			//execute post request
			resBody2 = HttpRequestHandler.prepPostResponse(jsonAccount, urlCreateData);
			try {
				HttpEntity entity1 = resBody2.getEntity();
				String responseString = EntityUtils.toString(entity1);
				if (responseString != null && responseString.trim().length() > 0) {
					sao.decode(responseString);
				} 
			} catch (Exception e) {
				if(token == null)
					serverAccountTasksService.persistsCreateNewAccountPayOutAddress(url, user.getUsername(), email, createAccount.getPayinAddress());
				
				throw new Exception(e.getMessage());
			} finally {
				resBody2.close();
			}
			
		} catch (IOException e) {
			if(token == null)
				serverAccountTasksService.persistsCreateNewAccountPayOutAddress(url, user.getUsername(), email, createAccount.getPayinAddress());
			
			throw new IOException(e.getMessage());
		}
		
		// If successful store the received payout address into the database.
		if(sao.isSuccessful()) {
			ServerAccount responseAccount = serverAccountService.getByUrl(sao.getUrl());
			responseAccount.setPayoutAddress(sao.getPayoutAddress());
			serverAccountService.updatePayOutAddress(responseAccount.getUrl(), responseAccount);
		} else {
			if (token == null)
				serverAccountTasksService.persistsCreateNewAccountPayOutAddress(url, user.getUsername(), email, createAccount.getPayinAddress());
		}
	}
	
	/**
	 * Remove the database entries which are accomplished successfully.
	 * 
	 * @param token
	 * @return boolean
	 */
	public boolean removeProceedTasks(String token){
		ServerAccountTasks task = serverAccountTasksService.getAccountTasksByToken(token);
		if(ServerAccountTasksService.isValidServerAccountTaskType(task.getType())){			
			serverAccountTasksService.deleteTask(task.getType(), token);
			return true;
		}
		return false;
	}
	
	/**
	 * Upgrade the trust level of the 
	 * 
	 * @param string
	 * @param string2
	 * @param string3
	 * @param trustLevel 
	 * @throws Exception 
	 */
	public void upgradedTrustLevel(String username, String email, String url, int trustLevel, String token) throws Exception {
		ServerAccount account;
		try {
			account = serverAccountService.getByUrl(url);
		} catch (ServerAccountNotFoundException e1) {
			if(token == null)
				serverAccountTasksService.persistsUpgradeAccount(url, username, email, trustLevel);
			throw new ServerAccountNotFoundException(url);
		}
		CustomPublicKey cpk = new CustomPublicKey(Constants.SERVER_KEY_PAIR.getKeyNumber(), 
				Constants.SERVER_KEY_PAIR.getPkiAlgorithm(), Constants.SERVER_KEY_PAIR.getPublicKey());
		PKIAlgorithm pkiAlgorithm;
		try {
			pkiAlgorithm = PKIAlgorithm.getPKIAlgorithm(cpk.getPkiAlgorithm());
		} catch (UnknownPKIAlgorithmException e1) {
			if(token == null)
				serverAccountTasksService.persistsUpgradeAccount(url, username, email, trustLevel);
			throw new UnknownPKIAlgorithmException();
		}
		try {
			byte KeyNumber = serverAccountService.saveServerPublicKey(account.getId(), pkiAlgorithm, cpk.getPublicKey());
		} catch (UserAccountNotFoundException | ServerAccountNotFoundException  e1) {
			if(token == null)
				serverAccountTasksService.persistsUpgradeAccount(url, username, email, trustLevel);
			throw new Exception();
		}
		
		ServerAccountObject updatedAccount = new ServerAccountObject(SecurityConfig.BACKUP_DESTINATION, email);
		updatedAccount.setTrustLevel(trustLevel);
		JSONObject jsonAccount = new JSONObject();
		try {
			updatedAccount.encode(jsonAccount);
		} catch (Exception e) {
			if(token == null)
				serverAccountTasksService.persistsUpgradeAccount(url, username, email, trustLevel);
			throw new Exception(e.getMessage());
		}
		
		// The http request is prepared and send with the information
		//TODO: mehmet sign object
		CloseableHttpResponse resBody;
		String urlData = url+ Config.ACCEPT_UPGRADE_TRUST_LEVEL;
		TransferObject response = new TransferObject();
		try {
			//execute post request
			resBody = HttpRequestHandler.prepPostResponse(jsonAccount, urlData);
			try {
				HttpEntity entity1 = resBody.getEntity();
				String responseString = EntityUtils.toString(entity1);
				if (responseString != null && responseString.trim().length() > 0) {
					response.decode(responseString);
				} 
			} catch (Exception e) {
				if(token == null)
					serverAccountTasksService.persistsUpgradeAccount(url, username, email, trustLevel);
				throw new Exception(e.getMessage());
			} finally {
				resBody.close();
			}
			
		} catch (IOException e) {
			if(token == null)
				serverAccountTasksService.persistsUpgradeAccount(url, username, email, trustLevel);
			throw new IOException(e.getMessage());
		}
		
		if(response.isSuccessful()){
			serverAccountService.updateTrustLevel(urlData, trustLevel);
			serverAccountTasksService.updateProceed(token);
			activitiesService.activityLog(username, Subjects.UPGRADE_TRUST_LEVEL,"Trust relation with server " + url + " is updated to " + trustLevel);
		} else{
			if(token == null)
				serverAccountTasksService.persistsUpgradeAccount(url, username, email, trustLevel);
		}
	}

	public void downgradeTrustLevel(String username, String email, String url, Integer trustLevel, String token) throws Exception {
		ServerAccount account;
		try {
			account = serverAccountService.getByUrl(url);
		} catch (ServerAccountNotFoundException e1) {
			if(token == null)
				serverAccountTasksService.persistsDowngradeAccount(url, username, email, trustLevel);
			throw new ServerAccountNotFoundException(url);
		}
		CustomPublicKey cpk = new CustomPublicKey(Constants.SERVER_KEY_PAIR.getKeyNumber(), 
				Constants.SERVER_KEY_PAIR.getPkiAlgorithm(), Constants.SERVER_KEY_PAIR.getPublicKey());
		PKIAlgorithm pkiAlgorithm;
		try {
			pkiAlgorithm = PKIAlgorithm.getPKIAlgorithm(cpk.getPkiAlgorithm());
		} catch (UnknownPKIAlgorithmException e2) {
			if(token == null)
				serverAccountTasksService.persistsDowngradeAccount(url, username, email, trustLevel);
			throw new UnknownPKIAlgorithmException();
		}
		try {
			byte KeyNumber = serverAccountService.saveServerPublicKey(account.getId(), pkiAlgorithm, cpk.getPublicKey());
		} catch (UserAccountNotFoundException | ServerAccountNotFoundException e1) {
			if(token == null)
				serverAccountTasksService.persistsDowngradeAccount(url, username, email, trustLevel);
			throw new Exception();
		}
		
		ServerAccountObject updatedAccount = new ServerAccountObject(SecurityConfig.BACKUP_DESTINATION, email);
		updatedAccount.setTrustLevel(trustLevel);
		JSONObject jsonAccount = new JSONObject();
		try {
			updatedAccount.encode(jsonAccount);
		} catch (Exception e) {
			if(token == null)
				serverAccountTasksService.persistsDowngradeAccount(url, username, email, trustLevel);
			throw new Exception(e.getMessage());
		}
		
		// The http request is prepared and send with the information
		//TODO: mehmet sign object
		CloseableHttpResponse resBody;
		String urlData = url+ Config.DECLINE_UPGRADE_TRUST_LEVEL;
		TransferObject response = new TransferObject();
		try {
			//execute post request
			resBody = HttpRequestHandler.prepPostResponse(jsonAccount, urlData);
			try {
				HttpEntity entity1 = resBody.getEntity();
				String responseString = EntityUtils.toString(entity1);
				if (responseString != null && responseString.trim().length() > 0) {
					response.decode(responseString);
				} 
			} catch (Exception e) {
				if(token == null)
					serverAccountTasksService.persistsDowngradeAccount(url, username, email, trustLevel);
				throw new Exception(e.getMessage());
			} finally {
				resBody.close();
			}
			
		} catch (IOException e) {
			if(token == null)
				serverAccountTasksService.persistsDowngradeAccount(url, username, email, trustLevel);
			throw new IOException(e.getMessage());
		}
		
		if(response.isSuccessful()){
			serverAccountService.updateTrustLevel(urlData, trustLevel);
			serverAccountTasksService.updateProceed(token);
			activitiesService.activityLog(username, Subjects.UPGRADE_TRUST_LEVEL,"Trust relation with server " + url + " is updated to " + trustLevel);
		} else{
			if(token == null)
				serverAccountTasksService.persistsUpgradeAccount(url, username, email, trustLevel);
		}
	}
}
