package ch.uzh.csg.mbps.server.controllerui;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import ch.uzh.csg.mbps.server.clientinterface.IUserAccount;
import ch.uzh.csg.mbps.server.domain.UserAccount;

@Controller
@RequestMapping("/users")
public class UsersController {
	
	@Autowired
	private IUserAccount userAccountService;
	
	@RequestMapping(method = RequestMethod.GET)
	public String users() {
        return "html/users";
    }
	
	@RequestMapping(value={"/all"}, method = RequestMethod.GET)
	public @ResponseBody List<UserAccount> getUsers() {
        return userAccountService.getUsers();
    }
}
