package ch.uzh.csg.mbps.server.controllerui;

import java.util.ArrayList;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import ch.uzh.csg.mbps.server.clientinterface.IServerTransaction;
import ch.uzh.csg.mbps.server.util.web.model.HistoryServerAccountTransaction;

@Controller
@RequestMapping("/history")
public class HistoryController {
	
	@Autowired
	private IServerTransaction serverTransactionService;

	@RequestMapping(method = RequestMethod.GET)
	public String history() {
        return "html/history";
    }
	
	@RequestMapping(value={"/transactions"}, method = RequestMethod.GET)
	public @ResponseBody ArrayList<HistoryServerAccountTransaction> getHistory(){
		return serverTransactionService.getHistory(0);
	}
}
