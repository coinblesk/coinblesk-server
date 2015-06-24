package ch.uzh.csg.coinblesk.server.controllerui;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import ch.uzh.csg.coinblesk.responseobject.TransferObject;
import ch.uzh.csg.coinblesk.responseobject.UserAccountObject;
import ch.uzh.csg.coinblesk.server.clientinterface.IUserAccount;
import ch.uzh.csg.coinblesk.server.domain.UserAccount;
import ch.uzh.csg.coinblesk.server.util.AuthenticationInfo;
import ch.uzh.csg.coinblesk.server.util.Config;
import ch.uzh.csg.coinblesk.server.util.exceptions.EmailAlreadyExistsException;
import ch.uzh.csg.coinblesk.server.util.exceptions.InvalidEmailException;
import ch.uzh.csg.coinblesk.server.util.exceptions.InvalidUrlException;
import ch.uzh.csg.coinblesk.server.util.exceptions.InvalidUsernameException;
import ch.uzh.csg.coinblesk.server.util.exceptions.UserAccountNotFoundException;
import ch.uzh.csg.coinblesk.server.util.exceptions.UsernameAlreadyExistsException;
import ch.uzh.csg.coinblesk.server.web.model.UserModelObject;
import ch.uzh.csg.coinblesk.server.web.response.UserAccountTransferObject;
import ch.uzh.csg.coinblesk.server.web.response.WebRequestTransferObject;

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
		List<UserAccount> users = userAccountService.getAllUserAccounts();
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
		o.setEmail(userAccount.getEmail());
		o.setId(userAccount.getId());
		o.setPassword(userAccount.getPassword());
		o.setUsername(userAccount.getUsername());
		o.setRole(userAccount.getRoles());
		return o;
	}
	
	@RequestMapping(value = { "/sendMailToAll" }, method = RequestMethod.POST, consumes="application/json")
	@ResponseBody public TransferObject sendMailToAll(@RequestBody WebRequestTransferObject request) {
		TransferObject response = new TransferObject();
		userAccountService.sendMailToAll(request.getSubject(), request.getMessage());
		response.setMessage(Config.SUCCESS);
		response.setSuccessful(true);
		return null;
	}
	
	@RequestMapping(value={"/inviteAdmin"}, method=RequestMethod.POST, consumes="application/json", produces="application/json")
	@ResponseBody public TransferObject inviteAdmin(@RequestBody UserModelObject request) throws UserAccountNotFoundException, UsernameAlreadyExistsException,
			InvalidUsernameException, InvalidEmailException, EmailAlreadyExistsException, InvalidUrlException {
		
		TransferObject response = new TransferObject();
		
		try{			
			userAccountService.getByUsername(AuthenticationInfo.getPrincipalUsername());
		} catch (UserAccountNotFoundException e) {
			response.setMessage(Config.FAILED);
			response.setSuccessful(false);
			return response;
		}
		
		UserAccount admin = null;
		try {
			admin = userAccountService.getByEmail(request.getEmail());
		} catch (UserAccountNotFoundException e) {
			//ignore
		}

		if (admin != null) {
			userAccountService.changeRoleBoth(admin);
		} else {
			userAccountService.changeRoleAdmin(request.getEmail());
		}
		
		response.setMessage(Config.SUCCESS);
		response.setSuccessful(true);
		return response;
	}
}
