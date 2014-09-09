package ch.uzh.csg.mbps.server.controllerui;

import java.util.Calendar;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import ch.uzh.csg.mbps.customserialization.PKIAlgorithm;
import ch.uzh.csg.mbps.customserialization.exceptions.UnknownPKIAlgorithmException;
import ch.uzh.csg.mbps.keys.CustomPublicKey;
import ch.uzh.csg.mbps.responseobject.CreateServerAccountObject;
import ch.uzh.csg.mbps.server.clientinterface.IServerAccount;
import ch.uzh.csg.mbps.server.clientinterface.IUserAccount;
import ch.uzh.csg.mbps.server.domain.ServerAccount;
import ch.uzh.csg.mbps.server.util.Config;
import ch.uzh.csg.mbps.server.util.Constants;
import ch.uzh.csg.mbps.server.util.SecurityConfig;
import ch.uzh.csg.mbps.server.util.exceptions.InvalidEmailException;
import ch.uzh.csg.mbps.server.util.exceptions.InvalidUrlException;
import ch.uzh.csg.mbps.server.util.exceptions.ServerAccountNotFoundException;
import ch.uzh.csg.mbps.server.util.exceptions.UrlAlreadyExistsException;
import ch.uzh.csg.mbps.server.util.exceptions.UserAccountNotFoundException;

import com.azazar.bitcoin.jsonrpcclient.BitcoinException;

@Controller
@RequestMapping("/communication")
public class ServerCommunicationController {

	@Autowired
	IServerAccount serverAccountService;
	@Autowired
	IUserAccount userAccountService;
	
	@RequestMapping(value = "/createNewAccount", method = RequestMethod.POST, produces = "application/json")
	@ResponseBody
	public CreateServerAccountObject createNewAccount(@RequestBody CreateServerAccountObject request){
		CreateServerAccountObject response = new CreateServerAccountObject();
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
				
				if(newAccount.getUrl() == null){
					
				}
				boolean success = serverAccountService.persistAccount(newAccount);
				if(!success){
					response.setMessage("Failed to create Server Account");
					response.setSuccessful(false);
				}
				response.setMessage(Config.ACCOUNT_SUCCESS);
			}
			// retrieve existing account and added public key to the server account
			account = serverAccountService.getByUrl(request.getUrl());
			serverAccountService.saveServerPublicKey(account.getId(), pkiAlgorithm, request.getCustomPublicKey().getPublicKey());
			response.setSuccessful(true);

			String serverUrl = SecurityConfig.BASE_URL;
			String email = userAccountService.getAdminEmail();

			CustomPublicKey cpk = new CustomPublicKey(Constants.SERVER_KEY_PAIR.getKeyNumber(), Constants.SERVER_KEY_PAIR.getPkiAlgorithm(), Constants.SERVER_KEY_PAIR.getPublicKey());
			CreateServerAccountObject create = new CreateServerAccountObject(serverUrl,email);
			create.setCustomPublicKey(cpk);

			return response;
		} 
		catch (UserAccountNotFoundException | ServerAccountNotFoundException e) {
			response.setSuccessful(false);
			response.setMessage(e.getMessage());
			return response;	
		} 
		catch (UnknownPKIAlgorithmException e) {
			// TODO Auto-generated catch block
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
}