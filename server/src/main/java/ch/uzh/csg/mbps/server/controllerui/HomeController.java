package ch.uzh.csg.mbps.server.controllerui;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import ch.uzh.csg.mbps.model.HistoryServerAccountTransaction;
import ch.uzh.csg.mbps.server.domain.UserAccount;
import ch.uzh.csg.mbps.server.service.ServerTransactionService;
import ch.uzh.csg.mbps.server.service.UserAccountService;

@Controller
@RequestMapping("/home")
public class HomeController {
	
	@RequestMapping(method = RequestMethod.GET)
	public String home() {
        return "home";
    }
	
	@RequestMapping(value={"/balance"})
	public 	@ResponseBody double getBalanceSum(){
		BigDecimal balance = UserAccountService.getInstance().getSumOfAllAccounts();
		return balance.doubleValue();
	}
	
	@RequestMapping(value={"/lastThreeTransaction"}, method=RequestMethod.GET, produces="application/json")
	public @ResponseBody ArrayList<HistoryServerAccountTransaction> getLastThreeTransaction(){
		return ServerTransactionService.getInstance().getLast3Transactions();
	}
	
	@RequestMapping(value={"/admins"}, method=RequestMethod.GET, produces="application/json")
	public @ResponseBody List<UserAccount> getAdmins(){
		return UserAccountService.getInstance().getAdmins();
	}
}
