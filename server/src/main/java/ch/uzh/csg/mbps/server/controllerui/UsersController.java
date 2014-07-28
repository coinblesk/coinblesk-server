package ch.uzh.csg.mbps.server.controllerui;

import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import ch.uzh.csg.mbps.server.domain.UserAccount;
import ch.uzh.csg.mbps.server.service.UserAccountService;

@Controller
@RequestMapping("/users")
public class UsersController {
	
	@RequestMapping(method = RequestMethod.GET)
	public String users() {
        return "users";
    }
	
	@RequestMapping(value={"/all"}, method = RequestMethod.GET)
	public @ResponseBody List<UserAccount> getUsers() {
        return UserAccountService.getInstance().getUsers();
    }
}
