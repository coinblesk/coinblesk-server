package ch.uzh.csg.mbps.server.controllerui;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import ch.uzh.csg.mbps.server.clientinterface.IServerAccount;
import ch.uzh.csg.mbps.server.domain.ServerAccount;
import ch.uzh.csg.mbps.server.util.AuthenticationInfo;
import ch.uzh.csg.mbps.server.util.exceptions.InvalidEmailException;
import ch.uzh.csg.mbps.server.util.exceptions.InvalidPublicKeyException;
import ch.uzh.csg.mbps.server.util.exceptions.InvalidUrlException;
import ch.uzh.csg.mbps.server.util.exceptions.ServerAccountNotFoundException;
import ch.uzh.csg.mbps.server.util.exceptions.UrlAlreadyExistsException;
import ch.uzh.csg.mbps.server.util.exceptions.UserAccountNotFoundException;

import com.azazar.bitcoin.jsonrpcclient.BitcoinException;

@Controller
@RequestMapping("/relation")
public class RelationController {
	
	@Autowired
	private IServerAccount serverAccountService;

	@RequestMapping(method = RequestMethod.GET)
	public String relation() {
        return "html/relation";
    }
	
	@RequestMapping(value={"/accounts"}, method = RequestMethod.GET, produces="application/json")
	public @ResponseBody List<ServerAccount> getAccounts() {
        return serverAccountService.getAll();
    }
	
	@RequestMapping(value={"/fulltrust"}, method = RequestMethod.GET, produces="application/json")
	public @ResponseBody List<ServerAccount> getFullTrust() {
        return serverAccountService.getByTrustLevel(2);
    }
	
	@RequestMapping(value={"/account/{id}"}, method = RequestMethod.GET)
	public @ResponseBody ServerAccount account(@PathVariable("id") long id) throws ServerAccountNotFoundException{
		ServerAccount account = serverAccountService.getById(id);
		return account;
	}
	
	@RequestMapping(value = { "/createNewAccount" }, method = RequestMethod.POST, produces = "application/json")
	public @ResponseBody
	void createAccount(@RequestParam(value = "url", required = false) String url, 
			@RequestParam(value = "email", required = false) String email) throws UrlAlreadyExistsException, UserAccountNotFoundException {		

		try {
			serverAccountService.checkIfExistsByUrl(url);
		} catch (UrlAlreadyExistsException exception) {
			throw new UrlAlreadyExistsException(url);
		}
		
		boolean isDeleted = serverAccountService.isDeletedByUrl(url);
		
		ServerAccount account;

		if(!isDeleted){			
			try {
				account = serverAccountService.prepareAccount(new ServerAccount(url, email, null));
			} catch (UserAccountNotFoundException  e) {
				throw new UserAccountNotFoundException(AuthenticationInfo.getPrincipalUsername());
			} catch (InvalidUrlException | InvalidEmailException | InvalidPublicKeyException e) {
				// TODO mehmet throwable
			} 
			
			//DOTO: mehmet
			//1. http get request (timer catch) return information New Account
			
			//2. serverAccountService.persistAccount(account(with payoutaddress)) 
			
			//3. http post request send created payinaddress
		} else {
			//change deleted flag??
		}
		
	}
	
	@RequestMapping(value = { "/persistNewAccount" }, method = RequestMethod.POST, produces = "application/json")
	public @ResponseBody
	void persistAccount(@RequestParam(value = "serverAccount", required = false) ServerAccount persistAccount) {
		try {
			serverAccountService.persistAccount(persistAccount);
		} catch (UrlAlreadyExistsException | BitcoinException e) {
			// TODO: mehmet should throw exception?
		} catch (InvalidEmailException | InvalidPublicKeyException | InvalidUrlException e) {
			//TODO: mehmet should throw exception?
		}
	}
}
