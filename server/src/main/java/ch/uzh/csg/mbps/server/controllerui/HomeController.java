package ch.uzh.csg.mbps.server.controllerui;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import ch.uzh.csg.mbps.responseobject.TransferObject;
import ch.uzh.csg.mbps.server.clientinterface.IMessages;
import ch.uzh.csg.mbps.server.clientinterface.IServerTransaction;
import ch.uzh.csg.mbps.server.clientinterface.IUserAccount;
import ch.uzh.csg.mbps.server.domain.Messages;
import ch.uzh.csg.mbps.server.domain.UserAccount;
import ch.uzh.csg.mbps.server.util.AuthenticationInfo;
import ch.uzh.csg.mbps.server.util.Config;
import ch.uzh.csg.mbps.server.util.exceptions.UserAccountNotFoundException;
import ch.uzh.csg.mbps.server.web.model.MessagesObject;
import ch.uzh.csg.mbps.server.web.model.UserModelObject;
import ch.uzh.csg.mbps.server.web.response.GetHistoryServerTransaction;
import ch.uzh.csg.mbps.server.web.response.MainWebRequestObject;
import ch.uzh.csg.mbps.server.web.response.MessagesTransferObject;

@Controller
@RequestMapping("/home")
public class HomeController {
	
	@Autowired
	private IUserAccount userAccountService;
	@Autowired
	private IServerTransaction serverTransactionService;
	@Autowired
	private IMessages messagesService;
	
	@RequestMapping(method = RequestMethod.GET)
	public String home() {
        return "html/home";
    }
	
	@RequestMapping(value={"/mainRequestObjects"}, method=RequestMethod.POST, produces="application/json")
	@ResponseBody public MainWebRequestObject mainViewRequests() throws Exception {
		MainWebRequestObject response = new MainWebRequestObject();
		String username = AuthenticationInfo.getPrincipalUsername();
		UserAccount account = userAccountService.getLoggedAdmin(username);
		UserModelObject userAccount = transformUser(account);
		
		response.setSuccessful(true);

		// set Balance
		response.setBalanceBTC(userAccountService.getSumOfUserAccountBalances());
		//set Last Server Transactions
		GetHistoryServerTransaction ghsto = new GetHistoryServerTransaction();
		ghsto.setTransactionHistory(serverTransactionService.getLast5Transactions());
		response.setGetHistoryTransferObject(ghsto);
		MessagesTransferObject mto = new MessagesTransferObject();
		List<Messages> messages = messagesService.getLast5Messages();
		List<MessagesObject> meObjects = new ArrayList<MessagesObject>(); 
		for(Messages message: messages){
			meObjects.add(transformMessage(message));
		}
		mto.setMessagesList(meObjects);
		response.setGetMessageTransferObject(mto);
		response.setUserModelObject(userAccount);

		return response;
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
		return o;
	}
	
	@RequestMapping(value={"/updateMail"}, method=RequestMethod.POST, consumes="application/json", produces="application/json")
	@ResponseBody public TransferObject updateMail(@RequestBody UserModelObject request) throws Exception{
		
		TransferObject response = new TransferObject();
		String userName = AuthenticationInfo.getPrincipalUsername();
		if(userName.compareTo(request.getUsername()) != 0){
			response.setMessage(Config.FAILED);
			response.setSuccessful(false);
			return response;
		}
		UserAccount account = new UserAccount(null, request.getEmail(), null);
		try {
			boolean success = userAccountService.updateAccount(request.getUsername(), account);
			if(success){
				response.setMessage(Config.ACCOUNT_UPDATED);
				response.setSuccessful(true);
				return response;
			}
		} catch (UserAccountNotFoundException e) {
			throw new UserAccountNotFoundException(request.getUsername());
			
		}
		
		response.setMessage(Config.FAILED);
		response.setSuccessful(false);
		return response;
	}
	
	@RequestMapping(value={"/updatePassword"}, method=RequestMethod.POST, consumes="application/json", produces="application/json")
	@ResponseBody public TransferObject updatePassword(@RequestBody UserModelObject request){
		
		TransferObject response = new TransferObject();
		String username = AuthenticationInfo.getPrincipalUsername();
		if(username.compareTo(request.getUsername()) != 0){
			response.setMessage(Config.FAILED);
			response.setSuccessful(false);
			return response;
		}
		UserAccount account = new UserAccount(null, null, request.getPassword());
		try {
			boolean success = userAccountService.updateAccount(request.getUsername(), account);
			if(success){
				response.setMessage(Config.ACCOUNT_UPDATED);
				response.setSuccessful(true);
				return response;
			}
		} catch (UserAccountNotFoundException e) {
			//ignore
		}
		
		response.setMessage(Config.FAILED);
		response.setSuccessful(false);
		return response;
	}
	
	@RequestMapping(value={"/userAccount"}, method=RequestMethod.GET, produces="application/json")
	@ResponseBody public UserModelObject getUserAccount(){
		UserModelObject response = new UserModelObject();
		
		String username = AuthenticationInfo.getPrincipalUsername();
		if(username == null || username.isEmpty()){
			response.setMessage(Config.FAILED);
			response.setSuccessful(false);
			return response;
		}
		UserAccount account = userAccountService.getLoggedAdmin(username);
		
		if(account == null){
			response.setMessage(Config.FAILED);
			response.setSuccessful(false);
			return response;
		}
		response = transformUser(account);
		response.setMessage(Config.SUCCESS);
		response.setSuccessful(true);
		return response;
	}
	
	private UserModelObject transformUser(UserAccount account) {
		UserModelObject o = new UserModelObject();
		o.setId(account.getId());
		o.setUsername(account.getUsername());
		o.setCreationDate(account.getCreationDate());
		o.setPassword(account.getPassword());
		o.setPaymentAddress(account.getPaymentAddress());
		o.setEmail(account.getEmail());
		o.setRole(account.getRoles());
		return o;
	}
}
