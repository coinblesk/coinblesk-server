package ch.uzh.csg.mbps.server.controllerui;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import net.minidev.json.JSONObject;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import ch.uzh.csg.mbps.responseobject.TransferObject;
import ch.uzh.csg.mbps.server.clientinterface.IActivities;
import ch.uzh.csg.mbps.server.clientinterface.IMessages;
import ch.uzh.csg.mbps.server.clientinterface.IServerAccount;
import ch.uzh.csg.mbps.server.clientinterface.IServerAccountTasks;
import ch.uzh.csg.mbps.server.clientinterface.IUserAccount;
import ch.uzh.csg.mbps.server.domain.Messages;
import ch.uzh.csg.mbps.server.domain.ServerAccount;
import ch.uzh.csg.mbps.server.domain.UserAccount;
import ch.uzh.csg.mbps.server.response.HttpRequestHandler;
import ch.uzh.csg.mbps.server.response.HttpResponseHandler;
import ch.uzh.csg.mbps.server.util.AuthenticationInfo;
import ch.uzh.csg.mbps.server.util.Config;
import ch.uzh.csg.mbps.server.util.SecurityConfig;
import ch.uzh.csg.mbps.server.util.Subjects;
import ch.uzh.csg.mbps.server.util.exceptions.MessageNotFoundException;
import ch.uzh.csg.mbps.server.util.exceptions.ServerAccountNotFoundException;
import ch.uzh.csg.mbps.server.util.exceptions.UserAccountNotFoundException;
import ch.uzh.csg.mbps.server.web.model.MessagesObject;
import ch.uzh.csg.mbps.server.web.response.MessagesTransferObject;
import ch.uzh.csg.mbps.server.web.response.ServerAccountUpdatedObject;
import ch.uzh.csg.mbps.server.web.response.WebRequestTransferObject;

@Controller
@RequestMapping("/messages")
public class MessagesController {

	@Autowired
	public IMessages messagesService;
	@Autowired
	public IServerAccount serverAccountService;
	@Autowired
	public IUserAccount userAccountService;
	@Autowired
	private IServerAccountTasks serverAccountTasksService;
	@Autowired
	private IActivities activitiesService;
	
	@RequestMapping(method = RequestMethod.GET)
	public String home() {
        return "html/messages";
    }
	
	@RequestMapping(value={"/all"}, method = RequestMethod.POST, produces="application/json")
	@ResponseBody public MessagesTransferObject getlogs(){
		//TODO: mehmet page number should be passed too
		MessagesTransferObject objects = new MessagesTransferObject();
		List<Messages> messages = messagesService.getNotAnsweredMessages(0);
		List<MessagesObject> meObjects = new ArrayList<MessagesObject>(); 
		for(Messages message: messages){
			meObjects.add(transformMessage(message));
		}
		objects.setMessagesList(meObjects);
		return objects;
	}
	
	private MessagesObject transformMessage(Messages message) {
		MessagesObject o = new MessagesObject();
		o.setId(message.getId());
		o.setSubject(message.getSubject());
		o.setMessage(message.getMessage());
		o.setServerUrl(message.getServerUrl());
		o.setCreationDate(message.getCreationDate());
		o.setAnsweredDate(message.getAnsweredDate());
		o.setAnswered(message.getAnswered());
		o.setTrustLevel(message.getTrustLevel());
		return o;
	}
	
	@RequestMapping(value={"/accept"}, method = RequestMethod.POST, consumes="application/json", produces="application/json")
	@ResponseBody public TransferObject accept(@RequestBody WebRequestTransferObject request){
		TransferObject response = new TransferObject();

		UserAccount sessionUser = null;
		try {
			//get the logged user
			sessionUser = userAccountService.getByUsername(AuthenticationInfo.getPrincipalUsername());
		} catch (UserAccountNotFoundException e1) {
			response.setMessage("User is not authorized" );
			response.setSuccessful(false);
			return response;
		}
		
		ServerAccount account = null;
		try {
			//get the server account which is involved
			account = serverAccountService.getByUrl(request.getUrl());
		} catch (ServerAccountNotFoundException e) {
			try {
				messagesService.updatedMessagesAnswered(request.getMessageId());
			} catch (MessageNotFoundException e1) {
				//ignore
			}
			response.setMessage("Url " + request.getUrl()+ " does not exits" );
			response.setSuccessful(false);
			return response;
		}
		
		//check if the active balance is zero
		if(account.getActiveBalance().abs().compareTo(BigDecimal.ZERO) != 0){
			response.setMessage("Request cannot procceed by an active balance not Zero" );
			response.setSuccessful(false);
			serverAccountTasksService.persistsUpgradeAccount(request.getUrl(), request.getUsername(), request.getEmail(), request.getTrustLevel());
			return response;			
		}
		
		ServerAccountUpdatedObject updatedAccount = new ServerAccountUpdatedObject();
		updatedAccount.setUrl(SecurityConfig.URL);
		updatedAccount.setEmail(sessionUser.getEmail());
		updatedAccount.setTrustLevel(request.getTrustLevel());
		JSONObject jsonObj = new JSONObject();
		try {
			updatedAccount.encode(jsonObj);
		} catch (Exception e) {
			response.setMessage(Config.FAILED);
			response.setSuccessful(false);
			return response;
		}
		
		CloseableHttpResponse resBody;
		TransferObject transferObject = new TransferObject();
		try {
			//execute post request
			resBody = HttpRequestHandler.prepPostResponse(jsonObj, request.getUrl() + Config.ACCEPT_UPGRADE_TRUST);
			try {
				transferObject = HttpResponseHandler.getResponse(transferObject, resBody);
			} catch (Exception e) {
				//ignore
			} finally {
					resBody.close();
			}
		} catch (IOException e) {
			//ignore
		}		
			
		if(transferObject.isSuccessful()){
			//persists the upgrade with the new trust level
			ServerAccount upgrade = new ServerAccount();
			upgrade.setTrustLevel(request.getTrustLevel());
			try {
				serverAccountService.updateAccount(request.getUrl(), upgrade);
			} catch (ServerAccountNotFoundException e) {
				//ignore
			}
			
			try {
				messagesService.updatedMessagesAnswered(request.getMessageId());
				activitiesService.activityLog(sessionUser.getUsername(), Subjects.ACCEPT_UPGRADE_TRUST_LEVEL, "Accepted upgrade trust level to " + request.getTrustLevel() + " of " + request.getUrl());
				response.setMessage(Config.SUCCESS);
				response.setSuccessful(true);
				return response;	
			} catch (MessageNotFoundException e) {
				//ignore
			}
		}
		response.setMessage(Config.FAILED);
		response.setSuccessful(false);
		return response;
	} 
	
	@RequestMapping(value={"/decline"}, method = RequestMethod.POST, consumes="application/json", produces="application/json")
	@ResponseBody public TransferObject decline(@RequestBody WebRequestTransferObject request){
		TransferObject response = new TransferObject();

		UserAccount sessionUser = null;
		try {
			sessionUser = userAccountService.getByUsername(AuthenticationInfo.getPrincipalUsername());
		} catch (UserAccountNotFoundException e1) {
			response.setMessage("User is not authorized" );
			response.setSuccessful(false);
			return response;
		}
		
		ServerAccount account = null;
		try {
			//get the server account which is involved
			account = serverAccountService.getByUrl(request.getUrl());
		} catch (ServerAccountNotFoundException e) {
			try {
				messagesService.updatedMessagesAnswered(request.getMessageId());
			} catch (MessageNotFoundException e1) {
				//ignore
			}
			response.setMessage("Url " + request.getUrl()+ " does not exits" );
			response.setSuccessful(false);
			return response;
		}
		
		//check if the active balance is zero
		if(account.getActiveBalance().abs().compareTo(BigDecimal.ZERO) != 0){
			response.setMessage("Request cannot procceed by an active balance not Zero" );
			response.setSuccessful(false);
			serverAccountTasksService.persistsDowngradeAccount(request.getUrl(), request.getUsername(), request.getEmail(), request.getTrustLevel());
			return response;			
		}
		
		ServerAccountUpdatedObject updatedAccount = new ServerAccountUpdatedObject();
		updatedAccount.setUrl(SecurityConfig.URL);
		updatedAccount.setEmail(sessionUser.getEmail());
		updatedAccount.setTrustLevel(request.getTrustLevel());
		JSONObject jsonObj = new JSONObject();
		try {
			updatedAccount.encode(jsonObj);
		} catch (Exception e) {
			response.setMessage(Config.FAILED);
			response.setSuccessful(false);
			return response;
		}
		
		CloseableHttpResponse resBody;
		TransferObject transferObject = new TransferObject();
		try {
			//execute post request
			resBody = HttpRequestHandler.prepPostResponse(jsonObj, request.getUrl() + Config.DECLINE_UPGRADE_TRUST);
			try {
				transferObject = HttpResponseHandler.getResponse(transferObject, resBody);
			} catch (Exception e) {
				//ignore
			} finally {
					resBody.close();
			}
		} catch (IOException e) {
			//ignore
		}		
			
		if(transferObject.isSuccessful()){
			try {
				messagesService.updatedMessagesAnswered(request.getMessageId());
				activitiesService.activityLog(sessionUser.getUsername(), Subjects.ACCEPT_UPGRADE_TRUST_LEVEL, "Declined upgrade trust level to " + request.getTrustLevel() + " of " + request.getUrl());
				response.setMessage(Config.SUCCESS);
				response.setSuccessful(true);
				return response;	
			} catch (MessageNotFoundException e) {
				//ignore
			}
		}
		
		response.setMessage(Config.FAILED);
		response.setSuccessful(false);
		return response;
	} 
}
