package ch.uzh.csg.coinblesk.server.controller;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import ch.uzh.csg.coinblesk.responseobject.PayOutRulesTransferObject;
import ch.uzh.csg.coinblesk.responseobject.TransferObject;
import ch.uzh.csg.coinblesk.server.clientinterface.IPayOutRule;
import ch.uzh.csg.coinblesk.server.domain.PayOutRule;
import ch.uzh.csg.coinblesk.server.util.AuthenticationInfo;
import ch.uzh.csg.coinblesk.server.util.exceptions.PayOutRuleNotFoundException;
import ch.uzh.csg.coinblesk.server.util.exceptions.PayOutRulesAlreadyDefinedException;
import ch.uzh.csg.coinblesk.server.util.exceptions.UserAccountNotFoundException;

import com.azazar.bitcoin.jsonrpcclient.BitcoinException;

/**
 * Controller for client http requests regarding PayOutRules.
 * 
 */
@Controller
@RequestMapping("/rules")
public class PayOutRulesController {
	
	private static final String CREATION_SUCCESS = "Your new rule has successfully been saved.";
	private static final String ACCOUNT_NOT_FOUND = "UserAccount not found.";
	private static final String NO_RULES = "No payout rules defined for this user.";
	private static final String RESET_SUCCESS = "Your payout rules have successfully been reseted.";
	private static final String INVALID_ADDRESS = "Your defined payout address is not a valid bitcoin address.";
	private static final String RULES_ALREADY_DEFINED = "You already defined your payout rules. Please reset first to create new rules.";
	
	@Autowired
	private IPayOutRule payOutRuleService;

	/**
	 * Creates one/multiple new PayOutRules. Returns failure message when
	 * payoutrules are already defined for this useraccount.
	 * 
	 * @param porto
	 * @return CustomResponseObject with information about success/non success
	 */
	@RequestMapping(value = "/create", method = RequestMethod.POST, consumes = "application/json")
	@ResponseBody
	public TransferObject createRule(@RequestBody PayOutRulesTransferObject porto) {
		TransferObject reply = new TransferObject();
		try {
			payOutRuleService.createRule(porto, AuthenticationInfo.getPrincipalUsername());
			reply.setSuccessful(true);
			reply.setMessage(CREATION_SUCCESS);
			return reply;
		} catch (UserAccountNotFoundException e) {
			reply.setSuccessful(false);
			reply.setMessage(ACCOUNT_NOT_FOUND);
			return reply;
		} catch (BitcoinException e) {
			reply.setSuccessful(false);
			reply.setMessage(INVALID_ADDRESS);
			return reply;
		} catch (PayOutRulesAlreadyDefinedException e) {
			reply.setSuccessful(false);
			reply.setMessage(RULES_ALREADY_DEFINED);
			return reply;
		}
	}

	/**
	 * Returns all defined PayOutRules for given UserAccount.
	 * 
	 * @return CustomResponseObject with ArrayList<PayOutRules> and boolean if
	 *         request successful/not successful
	 */
	@RequestMapping(value = "/get", method = RequestMethod.GET, produces = "application/json")
	@ResponseBody
	public PayOutRulesTransferObject getRules() {
		PayOutRulesTransferObject response = new PayOutRulesTransferObject();
		try {
			List<PayOutRule> list = payOutRuleService.getRules(AuthenticationInfo.getPrincipalUsername());
			response.setPayOutRulesList(transform(list));
			response.setSuccessful(true);
		} catch (UserAccountNotFoundException e) {
			response.setSuccessful(false);
			response.setMessage(ACCOUNT_NOT_FOUND);
		} catch (PayOutRuleNotFoundException e) {
			response.setSuccessful(false);
			response.setMessage(NO_RULES);
		} catch (Throwable e) {
			response.setSuccessful(false);
			response.setMessage("Unexpected" + e.getMessage());
			e.printStackTrace();
		}
		return response;
	}

	/**
	 * Transforms PayOutRules to DB conform PayOutRules
	 * 
	 * @param list
	 * @return ArrayList<ch.uzh.csg.coinblesk.model.PayOutRule> with transformed
	 *         PayOutRules
	 */
	public ArrayList<ch.uzh.csg.coinblesk.model.PayOutRule> transform(List<PayOutRule> list) {
		ArrayList<ch.uzh.csg.coinblesk.model.PayOutRule> list2 = new ArrayList<ch.uzh.csg.coinblesk.model.PayOutRule>();
		ch.uzh.csg.coinblesk.model.PayOutRule por2;
		for(int i=0; i<list.size();i++){
			PayOutRule por = list.get(i);
			por2 = new ch.uzh.csg.coinblesk.model.PayOutRule();
			por2.setBalanceLimitBTC(por.getBalanceLimit());
			por2.setDay(por.getDay());
			por2.setHour(por.getHour());
			por2.setPayoutAddress(por.getPayoutAddress());
			//never transfer the userid over the network
			list2.add(por2);
		}
		return list2;
	}

	/**
	 * Deletes all PayOutRules assigned to authenticated UserAccount.
	 * 
	 * @return CustomResponseObject with information about success/non success
	 *         of deletion
	 */
	@RequestMapping(value = "/reset", method = RequestMethod.GET, produces = "application/json")
	@ResponseBody
	public TransferObject resetRules() {
		TransferObject reply = new TransferObject();
		try {
			payOutRuleService.deleteRules(AuthenticationInfo.getPrincipalUsername());
			reply.setSuccessful(true);
			reply.setMessage(RESET_SUCCESS);
			return reply;
		} catch (UserAccountNotFoundException e) {
			reply.setSuccessful(false);
			reply.setMessage(ACCOUNT_NOT_FOUND);
			return reply;
		}
	}
	
}
