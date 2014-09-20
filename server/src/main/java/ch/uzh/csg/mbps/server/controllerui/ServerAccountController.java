package ch.uzh.csg.mbps.server.controllerui;

import java.io.IOException;
import java.math.BigDecimal;

import net.minidev.json.JSONObject;

import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.util.EntityUtils;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import ch.uzh.csg.mbps.responseobject.TransferObject;
import ch.uzh.csg.mbps.server.clientinterface.IActivities;
import ch.uzh.csg.mbps.server.clientinterface.IServerAccount;
import ch.uzh.csg.mbps.server.clientinterface.IServerAccountTasks;
import ch.uzh.csg.mbps.server.clientinterface.IServerTransaction;
import ch.uzh.csg.mbps.server.clientinterface.IUserAccount;
import ch.uzh.csg.mbps.server.domain.ServerAccount;
import ch.uzh.csg.mbps.server.domain.UserAccount;
import ch.uzh.csg.mbps.server.response.HttpRequestHandler;
import ch.uzh.csg.mbps.server.response.HttpResponseHandler;
import ch.uzh.csg.mbps.server.util.AuthenticationInfo;
import ch.uzh.csg.mbps.server.util.Config;
import ch.uzh.csg.mbps.server.util.SecurityConfig;
import ch.uzh.csg.mbps.server.util.Subjects;
import ch.uzh.csg.mbps.server.util.exceptions.ServerAccountNotFoundException;
import ch.uzh.csg.mbps.server.util.exceptions.UserAccountNotFoundException;
import ch.uzh.csg.mbps.server.web.response.GetHistoryServerTransaction;
import ch.uzh.csg.mbps.server.web.response.ServerAccountDataTransferObject;
import ch.uzh.csg.mbps.server.web.response.ServerAccountObject;
import ch.uzh.csg.mbps.server.web.response.WebRequestTransferObject;

@Controller
@RequestMapping("/serveraccount")
public class ServerAccountController {
	
	private static Logger LOGGER = Logger.getLogger(ServerAccountController.class);
	
	@Autowired
	private IServerAccount serverAccountService;
	@Autowired
	private IUserAccount userAccountService;
	@Autowired
	private IServerTransaction serverTransactionService;
	@Autowired
	private IServerAccountTasks serverAccountTasksServices;
	@Autowired
	private IActivities activitiesService;
	
	@RequestMapping(method = RequestMethod.GET)
	public String history() {
		return "html/serveraccount";
	}
	
	@RequestMapping(value = { "/accountData" }, method = RequestMethod.POST, consumes="application/json", produces="application/json")
	@ResponseBody public ServerAccountDataTransferObject accountData(@RequestBody ServerAccountObject request) throws ServerAccountNotFoundException {
		ServerAccountDataTransferObject response = new ServerAccountDataTransferObject();
		ServerAccount account = serverAccountService.getById(request.getId());
		response.setServerAccountObject(transformServerObject(account));
		GetHistoryServerTransaction ghsto = new GetHistoryServerTransaction();
		ghsto.setTransactionHistory(serverTransactionService.getLast5ServerAccountTransaction(account.getUrl()));
		response.setGetHistoryTransferObject(ghsto);
		response.setMessage(Config.SUCCESS);
		response.setSuccessful(true);
		return response;
	}

	private ServerAccountObject transformServerObject(ServerAccount account) {
		ServerAccountObject o = new ServerAccountObject();
		o.setId(account.getId());
		o.setUrl(account.getUrl());
		o.setPayinAddress(account.getPayinAddress());
		o.setPayoutAddress(account.getPayoutAddress());
		o.setTrustLevel(account.getTrustLevel());
		o.setActiveBalance(account.getActiveBalance());
		o.setBalanceLimit(account.getBalanceLimit());
		o.setUserBalanceLimit(account.getUserBalanceLimit());
		return o;
	}

	@RequestMapping(value = { "/deleteAccount" }, method = RequestMethod.POST, consumes="application/json")
	public @ResponseBody TransferObject delete(@RequestBody ServerAccountObject request) throws Exception {
		TransferObject response = new TransferObject();
		
		UserAccount user;
		try{			
			user = userAccountService.getByUsername(AuthenticationInfo.getPrincipalUsername());
		} catch (UserAccountNotFoundException e){
			response.setMessage("User is not authorized");
			response.setSuccessful(false);
			throw new UserAccountNotFoundException(null);
		}
		
		if(serverAccountService.checkIfExistsByUrl(request.getUrl())){			
			boolean passed = serverAccountService.checkPredefinedDeleteArguments(request.getUrl());
			if (passed) {
				ServerAccountObject deleteAccount = new ServerAccountObject();
				deleteAccount.setUrl(SecurityConfig.BASE_URL);
				JSONObject jsonObj = new JSONObject();
				try {
					deleteAccount.encode(jsonObj);
				} catch (Exception e) {
					throw new Exception(e.getMessage());
				}
				CloseableHttpResponse resBody;
				try {
					resBody = HttpRequestHandler.prepPostResponse(jsonObj, request.getUrl() + Config.DELETE_ACCOUNT);									
					try {
						response = HttpResponseHandler.getResponse(response, resBody);
//						HttpEntity entity1 = resBody.getEntity();
//						String respString = EntityUtils.toString(entity1);
//						if(respString != null && respString.trim().length() > 0) {
//							response.decode(respString);
//						}
//					} catch (Exception e) {
//						throw new Exception(e.getMessage());
					} finally {
						resBody.close();
					}
				} catch (IOException e) {
					throw new IOException(e.getMessage());
				}
				
				if(response.isSuccessful()){				
					boolean success = serverAccountService.deleteAccount(request.getUrl());
					if (success) {
						activitiesService.activityLog(user.getUsername(), Subjects.DELETE_ACCOUNT, "The server account with url " + request.getUrl() +" is deleted.");
						response.setMessage(Config.SUCCESS);
						response.setSuccessful(true);
						return response;
					}
				}
			}	
		}
		response.setMessage(Config.FAILED);
		response.setSuccessful(false);
		return response;
	}

	@RequestMapping(value = { "/updateTrustLevel" }, method = RequestMethod.POST, consumes="application/json", produces="application/json")
	@ResponseBody public TransferObject updateTrustLevel(@RequestBody WebRequestTransferObject request) throws Exception {
		TransferObject response = new TransferObject();
		UserAccount user = null;
		try {
			user = userAccountService.getByUsername(AuthenticationInfo.getPrincipalUsername());
		} catch (UserAccountNotFoundException e) {
			response.setMessage("User is not authorized");
			response.setSuccessful(false);
			throw new UserAccountNotFoundException(AuthenticationInfo.getPrincipalUsername());
		}
		
		//Get the account that has a trust level update
		ServerAccount tmpAccount = serverAccountService.getByUrl(request.getUrl());
		
		if(tmpAccount.getActiveBalance().compareTo(BigDecimal.ZERO)==0){
			// Prepare your data to send
			ServerAccountObject updatedAccount = new ServerAccountObject(SecurityConfig.BASE_URL, user.getEmail());
			updatedAccount.setTrustLevel(request.getTrustLevel());
			JSONObject jsonObj = new JSONObject();
			try {
				updatedAccount.encode(jsonObj);
			} catch (Exception e) {
				throw new Exception(e.getMessage());
			}
			
			CloseableHttpResponse resBody;
			TransferObject transferObject = new TransferObject();
			try {
				//execute post request
				if(request.getTrustLevelOld() < request.getTrustLevel()){				
					resBody = HttpRequestHandler.prepPostResponse(jsonObj, request.getUrl() + Config.DOWNGRADE_TRUST);					
				} else {
					resBody = HttpRequestHandler.prepPostResponse(jsonObj, request.getUrl() + Config.UPGRADE_TRUST);
				}
				try {
					HttpEntity entity1 = resBody.getEntity();
					String respString = EntityUtils.toString(entity1);
					if(respString != null && respString.trim().length() > 0) {
						transferObject.decode(respString);
					}
				} catch (Exception e) {
					throw new Exception(e.getMessage());
				} finally {
					resBody.close();
				}
			} catch (IOException e) {
				throw new IOException(e.getMessage());
			}		
			
			if(transferObject.isSuccessful()){
				// update the trust level of the server account who's level was downgraded 
				// upgrade will be ignored --> other server can answer async
				if(transferObject.getMessage().equals(Config.DOWNGRADE_SUCCEEDED)){
					ServerAccount trustUpdated = new ServerAccount();
					trustUpdated.setTrustLevel(request.getTrustLevel());
					boolean success = serverAccountService.updateAccount(request.getUrl(), trustUpdated);
					if(success) {
						activitiesService.activityLog(AuthenticationInfo.getPrincipalUsername(), Subjects.DOWNGRADE_TRUST_LEVEL, "Server account "+ request.getUrl() +" is downgrade to " + request.getTrustLevel());
						response.setMessage(Config.SUCCESS);
						response.setSuccessful(true);
						return response;
					}
				}
			}
			
		}
		response.setMessage(Config.FAILED);
		response.setSuccessful(false);
		return response;
	}

	@RequestMapping(value = { "/updateBalanceLimit}" }, method = RequestMethod.GET)
	@ResponseBody public TransferObject updateBalanceLimit(@RequestBody WebRequestTransferObject request) throws ServerAccountNotFoundException, UserAccountNotFoundException {
		TransferObject response = new TransferObject();
		UserAccount user = null;
		try {
			user = userAccountService.getByUsername(AuthenticationInfo.getPrincipalUsername());
		} catch (UserAccountNotFoundException e) {
			response.setMessage("User is not authorized");
			response.setSuccessful(false);
			throw new UserAccountNotFoundException(AuthenticationInfo.getPrincipalUsername());
		}
		
		if(request.getActiveBalance().compareTo(BigDecimal.ZERO) == 0){			
			ServerAccount updatedAccount = new ServerAccount();
			updatedAccount.setBalanceLimit(request.getBalanceLimit());
			boolean success = serverAccountService.updateAccount(request.getUrl(), updatedAccount);
			
			if(success){
				response.setMessage(Config.SUCCESS);
				response.setSuccessful(true);
				activitiesService.activityLog(user.getUsername(), Subjects.UPDATE_BALANCE_LIMIT, "The balance limit of the server url " + request.getUrl() + " is updated to "+ request.getBalanceLimit() + " BTC.");
				return response;
			}
		}
		response.setMessage(Config.FAILED);
		response.setSuccessful(false);
		return response;
	}
	
	@RequestMapping(value = { "/updateUserBalanceLimit}" }, method = RequestMethod.GET)
	@ResponseBody public TransferObject updateUserBalanceLimit(@RequestBody WebRequestTransferObject request) throws ServerAccountNotFoundException, UserAccountNotFoundException {
		
		TransferObject response = new TransferObject();
		UserAccount user = null;
		try {
			user = userAccountService.getByUsername(AuthenticationInfo.getPrincipalUsername());
		} catch (UserAccountNotFoundException e) {
			response.setMessage("User is not authorized");
			response.setSuccessful(false);
			throw new UserAccountNotFoundException(AuthenticationInfo.getPrincipalUsername());
		}

		if(request.getActiveBalance().compareTo(BigDecimal.ZERO) == 0){				
			ServerAccount updatedAccount = new ServerAccount();
			updatedAccount.setUserBalanceLimit(request.getUserBalanceLimit());
			boolean success = serverAccountService.updateAccount(request.getUrl(), updatedAccount);
			
			if(success){
				response.setMessage(Config.SUCCESS);
				response.setSuccessful(true);
				activitiesService.activityLog(user.getUsername(), Subjects.UPDATE_BALANCE_LIMIT, "The user balance limit of the server url " + request.getUrl() + " is updated to "+ request.getUserBalanceLimit() + " BTC.");
				return response;
			}
		}
		response.setMessage(Config.FAILED);
		response.setSuccessful(false);
		return response;
	}
}