package ch.uzh.csg.mbps.server.controllerui;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import ch.uzh.csg.mbps.responseobject.TransferObject;
import ch.uzh.csg.mbps.responseobject.UserAccountObject;
import ch.uzh.csg.mbps.server.clientinterface.IUserAccount;
import ch.uzh.csg.mbps.server.domain.UserAccount;
import ch.uzh.csg.mbps.server.util.Config;
import ch.uzh.csg.mbps.server.web.response.UserAccountTransferObject;

@Controller
@RequestMapping("/users")
public class UsersController {
	
	@Autowired
	private IUserAccount userAccountService;
	
	@RequestMapping(method = RequestMethod.GET)
	public String users() {
        return "html/users";
    }
	
	@RequestMapping(value={"/all"}, method = RequestMethod.POST, produces="application/json")
	public @ResponseBody UserAccountTransferObject getUsers() {
		UserAccountTransferObject response = new UserAccountTransferObject();
		List<UserAccount> users = userAccountService.getUsers();
		List<UserAccountObject> usersObject = new ArrayList<UserAccountObject>();
		for(UserAccount user: users){
			usersObject.add(transform1(user));
		}
		response.setUserAccountObjectList(usersObject);
		response.setMessage(Config.SUCCESS);
		response.setSuccessful(true);
		return response;
    }
	
	private UserAccountObject transform1(UserAccount userAccount) {
		UserAccountObject o = new UserAccountObject();
		o.setBalanceBTC(userAccount.getBalance());
		o.setEmail(userAccount.getEmail());
		o.setId(userAccount.getId());
		o.setPassword(userAccount.getPassword());
		o.setPaymentAddress(userAccount.getPaymentAddress());
		o.setUsername(userAccount.getUsername());
		return o;
	}
	
	@RequestMapping(value = { "/sendMailToAll" }, method = RequestMethod.POST, consumes="application/json")
	@ResponseBody public TransferObject sendMailToAll(
			@RequestParam(value = "subject", required = false) String subject,
			@RequestParam(value = "text", required = false) String text) {
		TransferObject response = new TransferObject();
		userAccountService.sendMailToAll(subject, text);
		response.setMessage(Config.SUCCESS);
		response.setSuccessful(true);
		return null;
	}
}
