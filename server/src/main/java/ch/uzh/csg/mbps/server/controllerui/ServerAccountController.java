package ch.uzh.csg.mbps.server.controllerui;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;

import net.minidev.json.JSONObject;

import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.util.EntityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import ch.uzh.csg.mbps.responseobject.ServerAccountObject;
import ch.uzh.csg.mbps.responseobject.TransferObject;
import ch.uzh.csg.mbps.server.clientinterface.IActivities;
import ch.uzh.csg.mbps.server.clientinterface.IServerAccount;
import ch.uzh.csg.mbps.server.clientinterface.IServerAccountTasks;
import ch.uzh.csg.mbps.server.clientinterface.IServerTransaction;
import ch.uzh.csg.mbps.server.clientinterface.IUserAccount;
import ch.uzh.csg.mbps.server.domain.ServerAccount;
import ch.uzh.csg.mbps.server.domain.UserAccount;
import ch.uzh.csg.mbps.server.response.HttpRequestHandler;
import ch.uzh.csg.mbps.server.util.ActivitiesTitle;
import ch.uzh.csg.mbps.server.util.AuthenticationInfo;
import ch.uzh.csg.mbps.server.util.Config;
import ch.uzh.csg.mbps.server.util.SecurityConfig;
import ch.uzh.csg.mbps.server.util.exceptions.ServerAccountNotFoundException;
import ch.uzh.csg.mbps.server.util.exceptions.UserAccountNotFoundException;
import ch.uzh.csg.mbps.server.util.web.model.HistoryServerAccountTransaction;

@Controller
@RequestMapping("/serveraccount")
public class ServerAccountController {
	
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

	@RequestMapping(value = { "/account/{id}" }, method = RequestMethod.GET)
	public @ResponseBody
	ServerAccount account(@PathVariable("id") long id) throws ServerAccountNotFoundException {
		return serverAccountService.getById(id);
	}

	@RequestMapping(value = { "/lastAccountTransaction/{url}" }, method = RequestMethod.GET, produces = "application/json")
	public @ResponseBody
	List<HistoryServerAccountTransaction> getLastAccountTransaction(@PathVariable("url") String url) throws ServerAccountNotFoundException {
		return serverTransactionService.getLast5ServerAccountTransaction(url);
	}

	@RequestMapping(value = { "/deleteAccount" }, method = RequestMethod.POST, consumes="application/json")
	public @ResponseBody
	boolean delete(@RequestParam(value = "url", required = false) String url) throws Exception {
		boolean passed = serverAccountService.checkPredefinedDeleteArguments(url);
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
			TransferObject response = new TransferObject();
			try {
				resBody = HttpRequestHandler.prepPostResponse(jsonObj, url + Config.DELETE_ACCOUNT);									
				try {
					HttpEntity entity1 = resBody.getEntity();
					String respString = EntityUtils.toString(entity1);
					if(respString != null && respString.trim().length() > 0) {
						response.decode(respString);
					}
				} catch (Exception e) {
					throw new Exception(e.getMessage());
				} finally {
					resBody.close();
				}
			} catch (IOException e) {
				throw new IOException(e.getMessage());
			}

			if(response.isSuccessful()){				
				boolean success = serverAccountService.deleteAccount(url);
				if (success) {
					return true;
				}
			} else {
				//TODO: mehmet ServerAccountTask??
			}
		}	
		//TODO: mehmet ServerAccountTask??
		return false;
	}

	@RequestMapping(value = { "/updateTrustLevel" }, method = RequestMethod.POST, consumes="application/json")
	public @ResponseBody void updateTrustLevel(
			@RequestParam(value = "url", required = false) String url,
			@RequestParam(value = "oldLevel", required = false) int oldLevel,
			@RequestParam(value = "newLevel", required = false) int newLevel) throws Exception {
		
		UserAccount user = null;
		try {
			user = userAccountService.getByUsername(AuthenticationInfo.getPrincipalUsername());
		} catch (UserAccountNotFoundException e) {
			throw new UserAccountNotFoundException(AuthenticationInfo.getPrincipalUsername());
		}
		
		//Get the account that has a trust level update
		ServerAccount tmpAccount = serverAccountService.getByUrl(url);
		
		if(tmpAccount.getActiveBalance().compareTo(BigDecimal.ZERO)==0){
			// Prepare your data to send
			ServerAccountObject updatedAccount = new ServerAccountObject(SecurityConfig.BASE_URL, user.getEmail());
			updatedAccount.setTrustLevel(newLevel);
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
				if(oldLevel < newLevel){				
					resBody = HttpRequestHandler.prepPostResponse(jsonObj, url + Config.DOWNGRADE_TRUST);					
				} else {
					resBody = HttpRequestHandler.prepPostResponse(jsonObj, url + Config.UPGRADE_TRUST);
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
				// upgrade will be ignored --> depends on the other server
				if(transferObject.getMessage().equals(Config.DOWNGRADE_SUCCEEDED)){
					ServerAccount trustUpdated = new ServerAccount();
					trustUpdated.setTrustLevel(newLevel);
					boolean success = serverAccountService.updateAccount(url, trustUpdated);
					if(success) {
						activitiesService.activityLog(AuthenticationInfo.getPrincipalUsername(), ActivitiesTitle.DOWNGRADE_TRUST_LEVEL, "Server account "+ url +" is downgrade to " + newLevel);
					}
				}
			}
			
		}
		
	}

	@RequestMapping(value = { "/updateBalanceLimit}" }, method = RequestMethod.GET)
	public @ResponseBody boolean updateBalanceLimit(
			@RequestParam(value = "url", required = false) String url,
			@RequestParam(value = "newLimit", required = false) BigDecimal newLimit) throws ServerAccountNotFoundException {
		ServerAccount updatedAccount = new ServerAccount();
		updatedAccount.setBalanceLimit(newLimit);
		return serverAccountService.updateAccount(url, updatedAccount);
	}
	
	@RequestMapping(value = { "/updateUserBalanceLimit}" }, method = RequestMethod.GET)
	public @ResponseBody boolean updateUserBalanceLimit(
			@RequestParam(value = "url", required = false) String url,
			@RequestParam(value = "newLimit", required = false) BigDecimal newLimit) throws ServerAccountNotFoundException {
		
		ServerAccount updatedAccount = new ServerAccount();
		updatedAccount.setUserBalanceLimit(newLimit);
		return serverAccountService.updateAccount(url, updatedAccount);
	}
}