package ch.uzh.csg.mbps.server.util;

import java.io.IOException;

import net.minidev.json.JSONObject;

import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.util.EntityUtils;
import org.springframework.beans.factory.annotation.Autowired;

import ch.uzh.csg.mbps.customserialization.PKIAlgorithm;
import ch.uzh.csg.mbps.keys.CustomPublicKey;
import ch.uzh.csg.mbps.responseobject.CreateServerAccountObject;
import ch.uzh.csg.mbps.responseobject.ServerAccountObject;
import ch.uzh.csg.mbps.server.clientinterface.IActivities;
import ch.uzh.csg.mbps.server.clientinterface.IServerAccount;
import ch.uzh.csg.mbps.server.clientinterface.IServerAccountTasks;
import ch.uzh.csg.mbps.server.domain.ServerAccount;
import ch.uzh.csg.mbps.server.domain.UserAccount;
import ch.uzh.csg.mbps.server.response.HttpRequestHandler;
import ch.uzh.csg.mbps.server.util.exceptions.InvalidUrlException;

public class ServerAccountTasksHandler {

	@Autowired
	private IServerAccount serverAccountService;
	@Autowired
	private IActivities activitiesService;
	@Autowired
	private IServerAccountTasks serverAccountTasksService;
	
	private static ServerAccountTasksHandler instance = null;
	
	public static ServerAccountTasksHandler getInstance(){
		if(instance == null){
			instance = new ServerAccountTasksHandler();
		}
		
		return instance;
	}
	
	public void createNewAccount(String url, String email, UserAccount user) throws Exception{
		//Get custom public key of the server
		CustomPublicKey cpk = new CustomPublicKey(Constants.SERVER_KEY_PAIR.getKeyNumber(), 
				Constants.SERVER_KEY_PAIR.getPkiAlgorithm(), Constants.SERVER_KEY_PAIR.getPublicKey());
		CreateServerAccountObject create = new CreateServerAccountObject(SecurityConfig.BASE_URL, user.getEmail());
		create.setCustomPublicKey(cpk);
		//encode object to json
		JSONObject jsonObj = new JSONObject();
		try {
			create.encode(jsonObj);
		} catch (Exception e) {
			throw new Exception(e.getMessage());
		}

		CloseableHttpResponse resBody;
		CreateServerAccountObject csao = new CreateServerAccountObject();
		String urlCreate = url+"/communication/createNewAccount";
		try {
			//execute post request
			resBody = HttpRequestHandler.prepPostResponse(jsonObj, urlCreate);
			try {
				HttpEntity entity1 = resBody.getEntity();
				String respString = EntityUtils.toString(entity1);
				if(respString != null && respString.trim().length() > 0) {
					csao.decode(respString);
				} else {
					//if response not correct store account into db for houtly tasks
					if(serverAccountTasksService != null)
						serverAccountTasksService.persistsCreateNewAccount(new ServerAccount(SecurityConfig.BASE_URL, user.getEmail()), url, email);
				}
			} catch (Exception e) {
				//if response not correct store account into db for houtly tasks
				if(serverAccountTasksService != null)
					serverAccountTasksService.persistsCreateNewAccount(new ServerAccount(SecurityConfig.BASE_URL, user.getEmail()), url, email);
				throw new Exception(e.getMessage());
			} finally {
				resBody.close();
			}
		} catch (IOException e) {
			//if response not correct store account into db for houtly tasks
			if(serverAccountTasksService != null)
				serverAccountTasksService.persistsCreateNewAccount(new ServerAccount(SecurityConfig.BASE_URL, user.getEmail()), url, email);
			throw new IOException(e.getMessage());
		}		
		
		if (csao.isSuccessful()) {
			// if urls are different throw exception
			if (url != csao.getUrl()) {
				if (serverAccountTasksService != null)
					serverAccountTasksService.persistsCreateNewAccount(
							new ServerAccount(SecurityConfig.BASE_URL, user
									.getEmail()), url, email);
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
					throw new Exception();
				}
			}
			
			updatedPayoutAddress(csao.getUrl(), email, user,csao.getCustomPublicKey());
			serverAccountTasksService.deleteCreateNewAccount(csao.getUrl());
		}
		activitiesService.activityLog(user.getUsername(), ActivitiesTitle.FAILED_CREATE_SERVER_ACCOUNT,"Failed to create a new relation with the server " + url + " and email " + email);
	}
	
	
	public void updatedPayoutAddress(String url, String email, UserAccount user, CustomPublicKey cpk) throws Exception{
		ServerAccount account = serverAccountService.getByUrl(url);
		PKIAlgorithm pkiAlgorithm = PKIAlgorithm.getPKIAlgorithm(cpk.getPkiAlgorithm());
		byte KeyNumber = serverAccountService.saveServerPublicKey(account.getId(), pkiAlgorithm, cpk.getPublicKey());
		
		ServerAccountObject createAccount = new ServerAccountObject(account.getUrl(), account.getEmail());
		createAccount.setPayoutAddress(account.getPayinAddress());
		JSONObject jsonAccount = new JSONObject();
		try {
			createAccount.encode(jsonAccount);
		} catch (Exception e) {
			throw new Exception(e.getMessage());
		}
		
		ServerAccountObject sao = new ServerAccountObject();
		
		CloseableHttpResponse resBody2;
		String urlCreateData = url+"/communication/createNewAccountData";
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
				throw new Exception(e.getMessage());
			} finally {
				resBody2.close();
			}
			
		} catch (IOException e) {
			throw new IOException(e.getMessage());
		}
		
		if(sao.isSuccessful()) {
			ServerAccount responseAccount = serverAccountService.getByUrl(sao.getUrl());
			responseAccount.setPayoutAddress(sao.getPayoutAddress());
			serverAccountService.updatePayOutAddress(responseAccount.getUrl(), responseAccount);
		}					

		activitiesService.activityLog(user.getUsername(), ActivitiesTitle.CREATE_SERVER_ACCOUNT,"Create a new relation with the server " + url + " and email " + email);
	}
}
