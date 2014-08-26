package ch.uzh.csg.mbps.server.controllerui;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import ch.uzh.csg.mbps.server.domain.ServerPayOutRule;
import ch.uzh.csg.mbps.server.service.ServerPayOutRuleService;
import ch.uzh.csg.mbps.server.service.UserAccountService;
import ch.uzh.csg.mbps.server.util.AuthenticationInfo;
import ch.uzh.csg.mbps.server.util.exceptions.ServerAccountNotFoundException;
import ch.uzh.csg.mbps.server.util.exceptions.ServerPayOutRuleNotFoundException;
import ch.uzh.csg.mbps.server.util.exceptions.ServerPayOutRulesAlreadyDefinedException;
import ch.uzh.csg.mbps.server.util.exceptions.UserAccountNotFoundException;
import ch.uzh.csg.mbps.server.util.web.ServerPayOutRulesTransferObject;

import com.azazar.bitcoin.jsonrpcclient.BitcoinException;

/**
 * Controller for client http requests regarding ServerPayOutRules.
 * 
 */
@Controller
@RequestMapping("/serverRules")
public class ServerPayOutRuleController {
	private static final String CREATION_SUCCESS = "Your new rule has successfully been saved.";
	private static final String SUCCESS = "The request is successfully executed.";
	private static final String RESET_SUCCESS = "Your payout rules have successfully been reseted.";
	private static final String PAYOUT_RULES_DEFINED = "Please reset your payout rules before before creating new payout rules.";
	private static final String NO_RULES = "No payout rules defined for this account.";
	private static final String ACCOUNT_NOT_FOUND = "Logged user not found.";
	private static final String SERVER_ACCOUNT_NOT_FOUND = "Server account not found.";
	private static final String INVALID_ADDRESS = "Your defined payout address is not a valid bitcoin address.";

	@Autowired
	private ServerPayOutRuleService serverPayOutRuleService;
	@Autowired
	private UserAccountService userAccountService;
	
	/**
	 * Creates one/multiple new ServerPayOutRules.
	 * 
	 * @param porto
	 * @return String with information about success/non success
	 */
	@RequestMapping(value = "/create", method = RequestMethod.POST, consumes = "application/json")
	@ResponseBody
	public ServerPayOutRulesTransferObject createRule(@RequestBody ServerPayOutRulesTransferObject sporto, String url) {
		ServerPayOutRulesTransferObject response = new ServerPayOutRulesTransferObject();
		try {
			userAccountService.getByUsername(AuthenticationInfo.getPrincipalUsername());
		} catch (UserAccountNotFoundException e) {
			response.setMessage(ACCOUNT_NOT_FOUND);
			response.setSuccessful(false);
			return response;
		}
		
		try {
			serverPayOutRuleService.createRule(sporto, url);
			response.setMessage(CREATION_SUCCESS);
			response.setSuccessful(true);
			return response;
		} catch (ServerPayOutRulesAlreadyDefinedException e) {
			response.setMessage(PAYOUT_RULES_DEFINED);
			response.setSuccessful(false);
			return response;
		} catch (ServerAccountNotFoundException e) {
			response.setMessage(SERVER_ACCOUNT_NOT_FOUND);
			response.setSuccessful(false);
			return response;
		} catch (BitcoinException e) {
			response.setMessage(INVALID_ADDRESS);
			response.setSuccessful(false);
			return response;
		}
	}

	/**
	 * Returns all defined ServerPayOutRules for given ServerAccount.
	 * 
	 * @return ServerPayOutRulesTransferObject with ArrayList<ServerPayOutRules>
	 *         and boolean if request successful/not successful
	 */
	@RequestMapping(value = "/get", method = RequestMethod.GET, produces = "application/json")
	@ResponseBody
	public ServerPayOutRulesTransferObject getRules(String url) {
		ServerPayOutRulesTransferObject response = new ServerPayOutRulesTransferObject();
		try {
			userAccountService.getByUsername(AuthenticationInfo.getPrincipalUsername());
		} catch (UserAccountNotFoundException e) {
			response.setMessage(ACCOUNT_NOT_FOUND);
			response.setSuccessful(false);
			return response;
		}
		try {
			List<ServerPayOutRule> list = serverPayOutRuleService.getRulesByUrl(url);
			response.setPayOutRulesList(list);
			response.setMessage(SUCCESS);
			response.setSuccessful(true);
			return response;
		} catch (ServerAccountNotFoundException e) {
			response.setMessage(SERVER_ACCOUNT_NOT_FOUND);
			response.setSuccessful(false);
			return response;
		} catch (ServerPayOutRuleNotFoundException e) {
			response.setMessage(NO_RULES);
			response.setSuccessful(false);
			return response;
		}
	}

	/**
	 * Deletes all PayOutRules assigned to authenticated UserAccount.
	 * 
	 * @return CustomResponseObject with information about success/non success
	 *         of deletion
	 */
	@RequestMapping(value = "/reset", method = RequestMethod.POST, produces = "application/json")
	@ResponseBody
	public ServerPayOutRulesTransferObject resetRules(String url) {
		ServerPayOutRulesTransferObject response = new ServerPayOutRulesTransferObject();
		try {
			userAccountService.getByUsername(AuthenticationInfo.getPrincipalUsername());
		} catch (UserAccountNotFoundException e) {
			response.setMessage(ACCOUNT_NOT_FOUND);
			response.setSuccessful(false);
			return response;
		}
		try {
			serverPayOutRuleService.deleteRules(url);
			response.setMessage(RESET_SUCCESS);
			response.setSuccessful(false);
			return response;
		} catch (ServerAccountNotFoundException e) {
			response.setMessage(SERVER_ACCOUNT_NOT_FOUND);
			response.setSuccessful(false);
			return response;
		}
	}
}