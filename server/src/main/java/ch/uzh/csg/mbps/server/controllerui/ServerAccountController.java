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

import ch.uzh.csg.mbps.server.clientinterface.IServerAccount;
import ch.uzh.csg.mbps.server.clientinterface.IServerTransaction;
import ch.uzh.csg.mbps.server.domain.ServerAccount;
import ch.uzh.csg.mbps.server.util.exceptions.BalanceNotZeroException;
import ch.uzh.csg.mbps.server.util.exceptions.ServerAccountNotFoundException;
import ch.uzh.csg.mbps.server.util.web.model.HistoryServerAccountTransaction;

@Controller
@RequestMapping("/serveraccount")
public class ServerAccountController {
	
	@Autowired
	private IServerAccount serverAccountService;
	
	@Autowired
	private IServerTransaction serverTransactionService;
	
	@RequestMapping(method = RequestMethod.GET)
	public String history() {
		return "html/serveraccount";
	}

	@RequestMapping(value = { "/account/{id}" }, method = RequestMethod.GET)
	public @ResponseBody
	ServerAccount account(@PathVariable("id") long id) throws ServerAccountNotFoundException {
		return serverAccountService.getById(id);
	}

	@RequestMapping(value = { "/lastAccountTransaction/{url}" }, method = RequestMethod.GET, produces = "application/json")
	public @ResponseBody
	List<HistoryServerAccountTransaction> getLastAccountTransaction(@PathVariable("url") String url) throws ServerAccountNotFoundException {
		return serverTransactionService.getLast5ServerAccountTransaction(url);
	}

	@RequestMapping(value = { "/deleteAccount}" }, method = RequestMethod.GET)
	public @ResponseBody
	boolean delete(@RequestParam(value = "url", required = false) String url) throws BalanceNotZeroException, ServerAccountNotFoundException {
		boolean passed = serverAccountService.checkPredefinedDeleteArguments(url);
		if (passed) {
			// TODO: mehmet before deleting make a delete request to delete from
			// other server
			boolean success = serverAccountService.deleteAccount(url);
			if (success) {
				// Make a server request singned object
			}
			return success;
		} else {
			return false;
		}
	}

	@RequestMapping(value = { "/updateTrustLevel}" }, method = RequestMethod.GET)
	public @ResponseBody
	void updateTrustLevel(
			@RequestParam(value = "url", required = false) String url,
			@RequestParam(value = "oldLevel", required = false) int oldLevel,
			@RequestParam(value = "newLevel", required = false) int newLevel) throws ServerAccountNotFoundException {
		serverAccountService.updateTrustLevel(url, oldLevel, newLevel);
	}

	@RequestMapping(value = { "/updateBalanceLimit}" }, method = RequestMethod.GET)
	public @ResponseBody
	void updateBalanceLimit(
			@RequestParam(value = "url", required = false) String url,
			@RequestParam(value = "oldLimit", required = false) BigDecimal oldLimit,
			@RequestParam(value = "newLimit", required = false) BigDecimal newLimit) throws ServerAccountNotFoundException {
		serverAccountService.updateBalanceLimit(url, oldLimit, newLimit);
	}
}