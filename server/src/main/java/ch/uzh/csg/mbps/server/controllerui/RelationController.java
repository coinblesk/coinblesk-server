package ch.uzh.csg.mbps.server.controllerui;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import ch.uzh.csg.mbps.server.clientinterface.IServerAccount;
import ch.uzh.csg.mbps.server.domain.ServerAccount;
import ch.uzh.csg.mbps.server.util.exceptions.ServerAccountNotFoundException;

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
	public @ResponseBody ServerAccount  account(@PathVariable("id") long id) throws ServerAccountNotFoundException{
		ServerAccount account = serverAccountService.getById(id);
		return account;
	}
}
