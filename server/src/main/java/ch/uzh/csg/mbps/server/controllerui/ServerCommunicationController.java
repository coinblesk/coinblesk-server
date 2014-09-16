package ch.uzh.csg.mbps.server.controllerui;

import java.util.Calendar;

import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import ch.uzh.csg.mbps.customserialization.PKIAlgorithm;
import ch.uzh.csg.mbps.customserialization.exceptions.UnknownPKIAlgorithmException;
import ch.uzh.csg.mbps.keys.CustomPublicKey;
import ch.uzh.csg.mbps.responseobject.CreateSAObject;
import ch.uzh.csg.mbps.responseobject.ServerAccountObject;
import ch.uzh.csg.mbps.responseobject.TransferObject;
import ch.uzh.csg.mbps.server.clientinterface.IActivities;
import ch.uzh.csg.mbps.server.clientinterface.IMessages;
import ch.uzh.csg.mbps.server.clientinterface.IServerAccount;
import ch.uzh.csg.mbps.server.clientinterface.IUserAccount;
import ch.uzh.csg.mbps.server.domain.Messages;
import ch.uzh.csg.mbps.server.domain.ServerAccount;
import ch.uzh.csg.mbps.server.util.Subjects;
import ch.uzh.csg.mbps.server.util.Config;
import ch.uzh.csg.mbps.server.util.Constants;
import ch.uzh.csg.mbps.server.util.SecurityConfig;
import ch.uzh.csg.mbps.server.util.exceptions.BalanceNotZeroException;
import ch.uzh.csg.mbps.server.util.exceptions.InvalidEmailException;
import ch.uzh.csg.mbps.server.util.exceptions.InvalidUrlException;
import ch.uzh.csg.mbps.server.util.exceptions.ServerAccountNotFoundException;
import ch.uzh.csg.mbps.server.util.exceptions.UrlAlreadyExistsException;
import ch.uzh.csg.mbps.server.util.exceptions.UserAccountNotFoundException;

import com.azazar.bitcoin.jsonrpcclient.BitcoinException;

@Controller
@RequestMapping("/communication")
public class ServerCommunicationController {

	 protected static Logger logger = Logger.getLogger("controller");
	
	@Autowired
	private IServerAccount serverAccountService;
	@Autowired
	private IUserAccount userAccountService;
	@Autowired
	private IActivities activitiesService;
	@Autowired
	private IMessages messagesService;
	
	@RequestMapping(value = "/createNewAccount", method = RequestMethod.POST, consumes="application/json", produces = "application/json")
	public @ResponseBody CreateSAObject createNewAccount(@RequestBody CreateSAObject request){
		CreateSAObject response = new CreateSAObject();
		PKIAlgorithm pkiAlgorithm;
		ServerAccount account;
		
		try {
			pkiAlgorithm = PKIAlgorithm.getPKIAlgorithm(request.getCustomPublicKey().getPkiAlgorithm());

			//check if account exists already
			if(serverAccountService.checkIfExistsByUrl(request.getUrl())){
				if(serverAccountService.isDeletedByUrl(request.getUrl())){
					serverAccountService.undeleteServerAccountByUrl(request.getUrl());
					response.setMessage(Config.ACCOUNT_DELETED);
				} else {
					response.setMessage(Config.ACCOUNT_EXISTS);
				}
			} else {				
				ServerAccount newAccount = new ServerAccount();
				newAccount.setUrl(request.getUrl());
				newAccount.setCreationDate(Calendar.getInstance().getTime());
				newAccount.setDeleted(false);
				newAccount.setEmail(request.getEmail());
				
				boolean success = serverAccountService.persistAccount(newAccount);
				if(!success){
					response.setMessage("Failed to create Server Account");
					response.setSuccessful(false);
				}
			}
			// retrieve existing account and added public key to the server account
			account = serverAccountService.getByUrl(request.getUrl());
			serverAccountService.saveServerPublicKey(account.getId(), pkiAlgorithm, request.getCustomPublicKey().getPublicKey());
			

			String serverUrl = SecurityConfig.BASE_URL;
			String email = userAccountService.getAdminEmail();

			CustomPublicKey cpk = new CustomPublicKey(Constants.SERVER_KEY_PAIR.getKeyNumber(), Constants.SERVER_KEY_PAIR.getPkiAlgorithm(), Constants.SERVER_KEY_PAIR.getPublicKey());
			response.setUrl(serverUrl);
			response.setEmail(email);
			response.setCustomPublicKey(cpk);
			response.setMessage(Config.ACCOUNT_SUCCESS);
			response.setSuccessful(true);

			return response;
		} 
		catch (UserAccountNotFoundException | ServerAccountNotFoundException e) {
			response.setSuccessful(false);
			response.setMessage(e.getMessage());
			return response;	
		} 
		catch (UnknownPKIAlgorithmException e) {
			response.setSuccessful(false);
			response.setMessage(e.getMessage());
			return response;	
		} 
		catch (UrlAlreadyExistsException e) {
			response.setSuccessful(false);
			response.setMessage(e.getMessage());
			return response;
		} 
		catch (BitcoinException e) {
			response.setSuccessful(false);
			response.setMessage(e.getMessage());
			return response;
		} 
		catch (InvalidUrlException e) {
			response.setSuccessful(false);
			response.setMessage(e.getMessage());
			return response;
		} 
		catch (InvalidEmailException e) {
			response.setSuccessful(false);
			response.setMessage(e.getMessage());
			return response;
		}	
	}

	@RequestMapping(value = "/createNewAccountData", method = RequestMethod.POST, produces = "application/json")
	public @ResponseBody ServerAccountObject createNewAccountData(@RequestBody ServerAccountObject request){
		ServerAccountObject response = new ServerAccountObject();
		ServerAccount account;
		
		try {
			//check if account exists already
			if(serverAccountService.checkIfExistsByUrl(request.getUrl())){
				if(!serverAccountService.isDeletedByUrl(request.getUrl())){
					account = serverAccountService.getByUrl(request.getUrl());
					account.setPayoutAddress(request.getPayoutAddress());
					boolean success = serverAccountService.updateAccount(request.getUrl(), account);
					if(success){
						String serverUrl = SecurityConfig.BASE_URL;
						response.setUrl(serverUrl);
						response.setPayoutAddress(account.getPayinAddress());
						response.setSuccessful(true);
						response.setMessage(Config.ACCOUNT_SUCCESS);
					} else {
						response.setSuccessful(false);
						response.setMessage(Config.ACCOUNT_FAILED);
					}
				} else {
					response.setSuccessful(false);
					response.setMessage(Config.ACCOUNT_FAILED);
				}			
			} else {
				response.setSuccessful(false);
				response.setMessage(Config.ACCOUNT_FAILED);				
			}
			return response;
		} 
		catch (ServerAccountNotFoundException e) {
			response.setSuccessful(false);
			response.setMessage(e.getMessage());
			return response;	
		} 
	}
	
	@RequestMapping(value = "/downgradeTrustLevel", method = RequestMethod.POST,consumes="application/json", produces = "application/json")
	public @ResponseBody TransferObject downgradeTrustLevel(@RequestBody ServerAccountObject request) throws ServerAccountNotFoundException{
		TransferObject response = new TransferObject();
		try{
			ServerAccount account = serverAccountService.getByUrl(request.getUrl());
			if(account.getTrustLevel() > request.getTrustLevel()){
				ServerAccount updated = new ServerAccount();
				updated.setTrustLevel(request.getTrustLevel());
				boolean success = serverAccountService.updateAccount(request.getUrl(), updated);
				if(success){
					activitiesService.activityLog(Config.NOT_AVAILABLE, Subjects.DOWNGRADE_TRUST_LEVEL, "Trust level of Server account "+ request.getUrl() + " is downgraded to " + request.getTrustLevel());
					response.setSuccessful(true);
					response.setMessage("Succeeded to downgrade!");
					return response;
				}
			}
		} catch (ServerAccountNotFoundException e) {
			response.setSuccessful(false);
			response.setMessage("Server Account does not exists!");
			return response;
		}
		response.setSuccessful(false);
		response.setMessage("Failed to downgrade");
		return response;
	}
	
	@RequestMapping(value = "/upgradeTrustLevel", method = RequestMethod.POST, consumes="application/json", produces = "application/json")
	public @ResponseBody TransferObject upgradeTrustLevel(@RequestBody ServerAccountObject request) throws ServerAccountNotFoundException{
		TransferObject response = new TransferObject();
		try{
			ServerAccount account = serverAccountService.getByUrl(request.getUrl());
			if(account.getTrustLevel() < request.getTrustLevel()){
				String messageInput = "Upgrade trust level to " + request.getTrustLevel();
				Messages message = new Messages(Config.UPGRADE_TRUST, messageInput, request.getUrl());
				message.setTrustLevel(request.getTrustLevel());
				messagesService.createMessage(message);
				response.setSuccessful(true);
				response.setMessage("upgrade message is created");
				return response;
			}
		} catch (ServerAccountNotFoundException e) {
			response.setSuccessful(false);
			response.setMessage("Server Account does not exists!");
			return response;
		}
		response.setSuccessful(false);
		response.setMessage("Failed to upgrade");
		return response;
	}
	
	@RequestMapping(value = "/upgradeAccepted", method = RequestMethod.POST, consumes="application/json", produces = "application/json")
	public @ResponseBody TransferObject upgradeAccepted(@RequestBody ServerAccountObject request) throws ServerAccountNotFoundException{
		TransferObject response = new TransferObject();
		try{
			serverAccountService.getByUrl(request.getUrl());
		} catch (ServerAccountNotFoundException e) {
			response.setSuccessful(false);
			response.setMessage("Server Account does not exists!");
			return response;
		}
		ServerAccount account = new ServerAccount();
		account.setTrustLevel(request.getTrustLevel());
		
		serverAccountService.updateAccount(request.getUrl(), account);
		activitiesService.activityLog(Config.NOT_AVAILABLE, Config.UPGRADE_TRUST, "Trust level of url: " + request.getUrl() + " is updated to " + request.getTrustLevel());
		response.setSuccessful(true);
		response.setMessage("Upgrade trust level to " + request.getTrustLevel());
		return response;
	}
	
	@RequestMapping(value = "/upgradeDeclined", method = RequestMethod.POST, consumes="application/json", produces = "application/json")
	public @ResponseBody TransferObject upgradeDeclined(@RequestBody ServerAccountObject request) throws ServerAccountNotFoundException{
		TransferObject response = new TransferObject();
		try{
			serverAccountService.getByUrl(request.getUrl());
		} catch (ServerAccountNotFoundException e) {
			response.setSuccessful(false);
			response.setMessage("Server Account does not exists!");
			return response;
		}
		activitiesService.activityLog(Config.NOT_AVAILABLE, Config.UPGRADE_TRUST, "Trust level of url: " + request.getUrl() + " is declined to updated to level " + request.getTrustLevel());
		response.setSuccessful(true);
		response.setMessage("Upgrade trust level to " + request.getTrustLevel());
		return response;
	}
	
	@RequestMapping(value ="/communication/deletedAccount", method = RequestMethod.POST, consumes="application/json")
	public @ResponseBody TransferObject deleteAccount(@RequestBody ServerAccountObject request){
		TransferObject response = new TransferObject();
		try{
			if(!serverAccountService.isDeletedByUrl(request.getUrl())){
				boolean success = serverAccountService.deleteAccount(request.getUrl());
				if(success){					
					response.setSuccessful(true);
					response.setMessage("Succeeded to deleted!");
					activitiesService.activityLog(Config.NOT_AVAILABLE, Subjects.DELETE_ACCOUNT, "Server account "+ request.getUrl()+ " is deleted");
					return response;
				}
			} else {
				response.setSuccessful(true);
				response.setMessage("Already deleted!");
				return response;
			}
		} catch (ServerAccountNotFoundException e) {
			response.setSuccessful(false);
			response.setMessage("Server Account does not exists!");
			return response;
		} catch (BalanceNotZeroException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		response.setSuccessful(false);
		response.setMessage("Failed to deleted");
		return response;
	}
}