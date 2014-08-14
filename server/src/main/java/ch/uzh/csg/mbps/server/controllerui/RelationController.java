package ch.uzh.csg.mbps.server.controllerui;

import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import ch.uzh.csg.mbps.server.domain.ServerAccount;
import ch.uzh.csg.mbps.server.service.ServerAccountService;
import ch.uzh.csg.mbps.server.util.exceptions.ServerAccountNotFoundException;

@Controller
@RequestMapping("/relation")
public class RelationController {

	@RequestMapping(method = RequestMethod.GET)
	public String relation() {
        return "html/relation";
    }
	
	@RequestMapping(value={"/accounts"}, method = RequestMethod.GET, produces="application/json")
	public @ResponseBody List<ServerAccount> getAccounts() {
        return ServerAccountService.getInstance().getAll();
    }
	
	@RequestMapping(value={"/fulltrust"}, method = RequestMethod.GET, produces="application/json")
	public @ResponseBody List<ServerAccount> getFullTrust() {
        return ServerAccountService.getInstance().getByTrustLevel(2);
    }
	
	@RequestMapping(value={"/account/{id}"}, method = RequestMethod.GET)
	public @ResponseBody ServerAccount  account(@PathVariable("id") long id) throws ServerAccountNotFoundException{
		ServerAccount account = ServerAccountService.getInstance().getById(id);
		return account;
	}
}
