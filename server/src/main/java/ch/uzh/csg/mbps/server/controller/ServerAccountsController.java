package ch.uzh.csg.mbps.server.controller;

import java.util.List;

import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import ch.uzh.csg.mbps.model.ServerAccount;
import ch.uzh.csg.mbps.responseobject.ServerAccountTransferObject;
import ch.uzh.csg.mbps.responseobject.ServerAccountsRequestObject;
import ch.uzh.csg.mbps.server.service.ServerAccountService;
import ch.uzh.csg.mbps.server.util.AuthenticationInfo;

/**
 * REST Controller for client http requests regarding ServerAccount operations.
 * 
 */
@Controller
@RequestMapping("/serveraccounts")
public class ServerAccountsController {
	private static Logger LOGGER = Logger.getLogger(ServerAccountsController.class);

	@Autowired
	private ServerAccountService serverAccountService;
	/**
	 * Returns server accounts that have trust relation with the server. If a
	 * parameter is negative, server accounts are not returned. If a page number
	 * is too large, an empty list might be returned. The returned list is
	 * ordered by their url descending.
	 * 
	 * @param urlPage
	 *            the page number of server accounts
	 * @return 
	 */
	@RequestMapping(value = "/accounts", method = RequestMethod.GET, produces = "application/json")
	@ResponseBody
	public ServerAccountTransferObject getAccounts(@RequestBody ServerAccountsRequestObject request) {
		ServerAccountTransferObject response = new ServerAccountTransferObject();
		if(!request.isComplete()){
			response.setSuccessful(false);
			response.setMessage("request has missing parameters");
		}
		try {
			String username = AuthenticationInfo.getPrincipalUsername();
			
			int urlPage = request.getUrlPage();
			List<ServerAccount> accounts = serverAccountService.getServerAccounts(username, urlPage);
			long nofSA = (urlPage < 0) ? 0 : serverAccountService.getAccountsCount();
			
			response.setSuccessful(true);
			response.setServerAccountList(accounts);
			response.setNumberOfSA(nofSA);
			
			return response;
		} catch (Exception e) {
			response.setSuccessful(false);
			response.setMessage(e.getMessage());
			LOGGER.error(e.getMessage());
			return response;
		}
	}
}