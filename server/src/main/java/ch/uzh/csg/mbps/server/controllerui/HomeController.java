package ch.uzh.csg.mbps.server.controllerui;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import ch.uzh.csg.mbps.server.clientinterface.IServerTransaction;
import ch.uzh.csg.mbps.server.clientinterface.IUserAccount;
import ch.uzh.csg.mbps.server.domain.UserAccount;
import ch.uzh.csg.mbps.server.util.UserModel;
import ch.uzh.csg.mbps.server.util.exceptions.UserAccountNotFoundException;
import ch.uzh.csg.mbps.server.util.web.model.HistoryServerAccountTransaction;

@Controller
@RequestMapping("/home")
public class HomeController {
	
	@Autowired
	private IUserAccount userAccountService;
	
	@Autowired
	private IServerTransaction serverTransactionService;
	
	@RequestMapping(method = RequestMethod.GET)
	public String home() {
        return "html/home";
    }
	
	@RequestMapping(value={"/balance"}, method=RequestMethod.GET, produces="application/json")
	public 	@ResponseBody double getBalanceSum(){
		BigDecimal balance =userAccountService.getSumOfAllAccounts();
		return balance.doubleValue();
	}
	
	@RequestMapping(value={"/lastThreeTransaction"}, method=RequestMethod.GET, produces="application/json")
	public @ResponseBody List<HistoryServerAccountTransaction> getLastThreeTransaction(){
		return serverTransactionService.getLast5Transactions();
	}
	
	@RequestMapping(value={"/admins"}, method=RequestMethod.GET, produces="application/json")
	public @ResponseBody List<UserAccount> getAdmins(){
		return userAccountService.getAdmins();
	}
	
	@RequestMapping(value={"/user/{username}"}, method=RequestMethod.GET, produces="application/json")
	public @ResponseBody UserModel loggedUser(@PathVariable("username") String username){
		return userAccountService.getLoggedAdmin(username);
	}
	
	@RequestMapping(value={"/updateMail/{username}"}, method=RequestMethod.GET, produces="application/json")
	public @ResponseBody void updateMail(@PathVariable("username") String username,
			@RequestParam(value="newemail", required=false)String newemail){
		
		UserAccount account = new UserAccount(null, newemail, null);
		try {
			userAccountService.updateAccount(username, account);
//			UserAccountService.getInstance().updateAccount(AuthenticationInfo.getPrincipalUsername(), account);
		} catch (UserAccountNotFoundException e) {
			//TODO: mehmet may be not needed
			e.printStackTrace();
		}
	}
	
	@RequestMapping(value={"/updatePassword/{username}"}, method=RequestMethod.GET, produces="application/json")
	public @ResponseBody void updatePAssword(@PathVariable("username") String username,
			@RequestParam(value="password", required=false)String password){
		
		UserAccount account = new UserAccount(null, null, password);
		try {
			userAccountService.updateAccount(username, account);
//			UserAccountService.getInstance().updateAccount(AuthenticationInfo.getPrincipalUsername(), account);
		} catch (UserAccountNotFoundException e) {
			e.printStackTrace();
		}
	}
	
	@RequestMapping(value={"/inviteAdmin"}, method=RequestMethod.GET, produces="application/json")
	public @ResponseBody void inviteAdmin(@RequestParam(value="email", required=false)String email) throws UserAccountNotFoundException{
		UserAccount admin = null;
		try {
			admin = userAccountService.getByEmail(email);
		} catch (UserAccountNotFoundException e) {
			e.printStackTrace();
		}
		
		if(admin != null){
			userAccountService.changeRoleBoth(admin);
		}else{
			userAccountService.changeRoleAdmin(email);
		}
	}
}
