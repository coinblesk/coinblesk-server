package ch.uzh.csg.mbps.server.controllerui;

import java.util.ArrayList;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import ch.uzh.csg.mbps.model.HistoryServerAccountTransaction;
import ch.uzh.csg.mbps.server.service.ServerTransactionService;

@Controller
@RequestMapping("/history")
public class HistoryController {

	@RequestMapping(method = RequestMethod.GET)
	public String history() {
        return "html/history";
    }
	
	@RequestMapping(value={"/transactions"}, method = RequestMethod.GET)
	public @ResponseBody ArrayList<HistoryServerAccountTransaction> getHistory(){
		return ServerTransactionService.getInstance().getHistory(0);
	}
}
