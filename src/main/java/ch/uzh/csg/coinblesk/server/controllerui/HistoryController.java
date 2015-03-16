package ch.uzh.csg.coinblesk.server.controllerui;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import ch.uzh.csg.coinblesk.server.clientinterface.IServerTransaction;
import ch.uzh.csg.coinblesk.server.util.Config;
import ch.uzh.csg.coinblesk.server.web.model.HistoryServerAccountTransaction;
import ch.uzh.csg.coinblesk.server.web.response.GetHistoryServerTransaction;

@Controller
@RequestMapping("/history")
public class HistoryController {
	
	@Autowired
	private IServerTransaction serverTransactionService;

	@RequestMapping(method = RequestMethod.GET)
	public String history() {
        return "html/history";
    }
	
	@RequestMapping(value={"/transactions"}, method = RequestMethod.POST, produces="application/json")
	@ResponseBody public GetHistoryServerTransaction getHistory(){
		GetHistoryServerTransaction response = new GetHistoryServerTransaction();
		List<HistoryServerAccountTransaction> transactions = serverTransactionService.getHistory(0);
		response.setTransactionHistory(transactions);
		response.setMessage(Config.SUCCESS);
		response.setSuccessful(true);
		return response;
	}
}
