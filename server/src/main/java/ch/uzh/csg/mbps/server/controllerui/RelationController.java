package ch.uzh.csg.mbps.server.controllerui;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.azazar.bitcoin.jsonrpcclient.BitcoinException;

import ch.uzh.csg.mbps.server.clientinterface.IServerAccount;
import ch.uzh.csg.mbps.server.domain.ServerAccount;
import ch.uzh.csg.mbps.server.util.exceptions.InvalidEmailException;
import ch.uzh.csg.mbps.server.util.exceptions.InvalidPublicKeyException;
import ch.uzh.csg.mbps.server.util.exceptions.InvalidUrlException;
import ch.uzh.csg.mbps.server.util.exceptions.ServerAccountNotFoundException;
import ch.uzh.csg.mbps.server.util.exceptions.UrlAlreadyExistsException;

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
	
	@RequestMapping(value = { "/createNewAccount" }, method = RequestMethod.GET, produces = "application/json")
	public @ResponseBody
	void createAccount(@RequestParam(value = "url", required = false) String url, @RequestParam(value = "email", required = false) String email) {
		try {
			serverAccountService.createAccount(url, email);
		} catch (UrlAlreadyExistsException | BitcoinException e) {
			// TODO: mehmet should throw exception?
		} catch (InvalidEmailException | InvalidPublicKeyException | InvalidUrlException e) {
			//TODO: mehmet should throw exception?
		}
	}
}
