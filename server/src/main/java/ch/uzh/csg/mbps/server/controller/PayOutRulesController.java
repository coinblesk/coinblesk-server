package ch.uzh.csg.mbps.server.controller;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import ch.uzh.csg.mbps.responseobject.CustomResponseObject;
import ch.uzh.csg.mbps.responseobject.CustomResponseObject.Type;
import ch.uzh.csg.mbps.responseobject.PayOutRulesTransferObject;
import ch.uzh.csg.mbps.server.domain.PayOutRule;
import ch.uzh.csg.mbps.server.service.PayOutRuleService;
import ch.uzh.csg.mbps.server.util.AuthenticationInfo;
import ch.uzh.csg.mbps.server.util.exceptions.PayOutRuleNotFoundException;
import ch.uzh.csg.mbps.server.util.exceptions.PayOutRulesAlreadyDefinedException;
import ch.uzh.csg.mbps.server.util.exceptions.UserAccountNotFoundException;

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
	private static final String INVALID_ADDRESS = "Your defined payout adress is not a valid bitcoin address.";
	private static final String RULES_ALREADY_DEFINED = "You already defined your payout rules. Please reset first to create new rules.";
	

	/**
	 * Creates one/multiple new PayOutRules. Returns failure message when
	 * payoutrules are already defined for this useraccount.
	 * 
	 * @param porto
	 * @return CustomResponseObject with information about success/non success
	 */
	@RequestMapping(value = "/create", method = RequestMethod.POST, consumes = "application/json")
	@ResponseBody
	public CustomResponseObject createRule(@RequestBody PayOutRulesTransferObject porto) {
		try {
			PayOutRuleService.getInstance().createRule(porto, AuthenticationInfo.getPrincipalUsername());
			return new CustomResponseObject(true, CREATION_SUCCESS);
		} catch (UserAccountNotFoundException e) {
			return new CustomResponseObject(false, ACCOUNT_NOT_FOUND);
		} catch (BitcoinException e) {
			return new CustomResponseObject(false, INVALID_ADDRESS);
		} catch (PayOutRulesAlreadyDefinedException e) {
			return new CustomResponseObject(false, RULES_ALREADY_DEFINED);
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
	public CustomResponseObject getRules() {
		try {
			ArrayList<PayOutRule> list = PayOutRuleService.getInstance().getRules(AuthenticationInfo.getPrincipalUsername());
			PayOutRulesTransferObject porto = new PayOutRulesTransferObject();
			porto.setPayOutRulesList(transform(list));
			CustomResponseObject cro = new CustomResponseObject(true, "", Type.PAYOUT_RULE);
			cro.setPorto(porto);
			return cro;
		} catch (UserAccountNotFoundException e) {
			return new CustomResponseObject(false, ACCOUNT_NOT_FOUND);
		} catch (PayOutRuleNotFoundException e) {
			return new CustomResponseObject(false, NO_RULES);
		}
	}

	/**
	 * Transforms PayOutRules to DB conform PayOutRules
	 * 
	 * @param list
	 * @return ArrayList<ch.uzh.csg.mbps.model.PayOutRule> with transformed
	 *         PayOutRules
	 */
	public ArrayList<ch.uzh.csg.mbps.model.PayOutRule> transform(List<PayOutRule> list) {
		ArrayList<ch.uzh.csg.mbps.model.PayOutRule> list2 = new ArrayList<ch.uzh.csg.mbps.model.PayOutRule>();
		ch.uzh.csg.mbps.model.PayOutRule por2;
		for(int i=0; i<list.size();i++){
			PayOutRule por = list.get(i);
			por2 = new ch.uzh.csg.mbps.model.PayOutRule();
			por2.setBalanceLimit(por.getBalanceLimit());
			por2.setDay(por.getDay());
			por2.setHour(por.getHour());
			por2.setPayoutAddress(por.getPayoutAddress());
			por2.setUserId(por.getUserId());
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
	@RequestMapping(value = "/reset", method = RequestMethod.POST, produces = "application/json")
	@ResponseBody
	public CustomResponseObject resetRules() {
		try {
			PayOutRuleService.getInstance().deleteRules(AuthenticationInfo.getPrincipalUsername());
			return new CustomResponseObject(true, RESET_SUCCESS);
		} catch (UserAccountNotFoundException e) {
			return new CustomResponseObject(false, ACCOUNT_NOT_FOUND);
		}
	}
	
}
