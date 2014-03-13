package ch.uzh.csg.mbps.server.service;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;

import ch.uzh.csg.mbps.responseobject.PayOutRulesTransferObject;
import ch.uzh.csg.mbps.server.dao.PayOutRuleDAO;
import ch.uzh.csg.mbps.server.domain.PayOutRule;
import ch.uzh.csg.mbps.server.domain.PayOutTransaction;
import ch.uzh.csg.mbps.server.domain.UserAccount;
import ch.uzh.csg.mbps.server.util.BitcoindController;
import ch.uzh.csg.mbps.server.util.Config;
import ch.uzh.csg.mbps.server.util.exceptions.PayOutRuleNotFoundException;
import ch.uzh.csg.mbps.server.util.exceptions.PayOutRulesAlreadyDefinedException;
import ch.uzh.csg.mbps.server.util.exceptions.UserAccountNotFoundException;

import com.azazar.bitcoin.jsonrpcclient.BitcoinException;

/**
 * Service class for {@link PayOutRule}s.
 *
 */
public class PayOutRuleService {
	private static PayOutRuleService payOutRuleService;
	public static Boolean testingMode = false;

	private PayOutRuleService() {
	}

	/**
	 * Returns new or existing instance of {@link PayOutRuleService}.
	 * 
	 * @return instance of PayOutRuleService
	 */
	public static PayOutRuleService getInstance() {
		if (payOutRuleService == null)
			payOutRuleService = new PayOutRuleService();

		return payOutRuleService;
	}

	/**
	 * Creates a new set of {@link PayOutRule}s for assigned UserAccount. Not more than
	 * 28 rules can be created for one UserAccount.
	 * 
	 * @param porto	PayOutRulesTransferObject containing list of PayOutRules
	 * @param username
	 * @throws UserAccountNotFoundException
	 * @throws BitcoinException
	 * @throws PayOutRulesAlreadyDefinedException
	 *             if already PayOutRules for this UserAccount are defined
	 */
	public void createRule(PayOutRulesTransferObject porto, String username) throws UserAccountNotFoundException, BitcoinException, PayOutRulesAlreadyDefinedException {
		UserAccount user = UserAccountService.getInstance().getByUsername(username);
		long userId = user.getId();
		boolean noRulesDefined = true;
		try {
			noRulesDefined = PayOutRuleDAO.getByUserId(userId).isEmpty();
		} catch (PayOutRuleNotFoundException e) {
			noRulesDefined = true;
		}
		if(noRulesDefined && porto.getPayOutRulesList().size() <= 28 || testingMode){
			ch.uzh.csg.mbps.model.PayOutRule por;
			for(int i = 0;i<porto.getPayOutRulesList().size();i++) {
				por = porto.getPayOutRulesList().get(i);
				por.setUserId(userId);
				if(!BitcoindController.validateAddress(por.getPayoutAddress())){
					throw new BitcoinException("Invalid Payout Address");
				}
			}
		} else {
			throw new PayOutRulesAlreadyDefinedException();
		}
		PayOutRuleDAO.createPayOutRules(porto.getPayOutRulesList());
	}

	/**
	 * Returns ArrayList with all {@link PayOutRule}s for UserAccount with username.
	 * 
	 * @param username
	 * @return ArrayList<PayOutRules>
	 * @throws PayOutRuleNotFoundException
	 * @throws UserAccountNotFoundException
	 */
	public ArrayList<PayOutRule> getRules(String username) throws PayOutRuleNotFoundException, UserAccountNotFoundException {
		UserAccount user = UserAccountService.getInstance().getByUsername(username);
		return PayOutRuleDAO.getByUserId(user.getId());
	}

	/**
	 * Deletes all {@link PayOutRule}s for UserAccount with username.
	 * 
	 * @param username
	 * @throws UserAccountNotFoundException
	 */
	public void deleteRules(String username) throws UserAccountNotFoundException {
		UserAccount user = UserAccountService.getInstance().getByUsername(username);
		PayOutRuleDAO.deleteRules(user.getId());
	}

	/**
	 * Checks if {@link PayOutRule} is assigned for sellerAccount. If rules exist for
	 * sellerAccount they are checked if the balance of sellerAccount exceeds
	 * the one defined in the rule. If yes a new PayOut is initiated according
	 * to the PayOutRule.
	 * 
	 * @param sellerAccount
	 * @throws PayOutRuleNotFoundException
	 * @throws UserAccountNotFoundException
	 * @throws BitcoinException
	 */
	public void checkBalanceLimitRules(UserAccount sellerAccount) throws PayOutRuleNotFoundException, UserAccountNotFoundException, BitcoinException {
		sellerAccount = UserAccountService.getInstance().getById(sellerAccount.getId());
		List<PayOutRule> rules = PayOutRuleDAO.getByUserId(sellerAccount.getId());

		PayOutRule tempRule;
		for (int i=0; i < rules.size(); i++){
			tempRule = rules.get(i);
			if(tempRule.getBalanceLimit() != null && sellerAccount.getBalance().compareTo(tempRule.getBalanceLimit()) == 1){
				PayOutTransaction pot = new PayOutTransaction();
				//set amount to account balance (minus transaction fee)
				pot.setAmount(sellerAccount.getBalance().subtract(Config.TRANSACTION_FEE));
				pot.setBtcAddress(tempRule.getPayoutAddress());
				pot.setUserID(sellerAccount.getId());
				PayOutTransactionService.getInstance().createPayOutTransaction(sellerAccount.getUsername(), pot);
			}
		}		
	}

	/**
	 * Returns all {@link PayOutRule}s assigned to {@link UserAccount} with userId
	 * 
	 * @param userId
	 * @return List<PayOutRule>
	 * @throws PayOutRuleNotFoundException
	 */
	public List<PayOutRule> getRules(long userId) throws PayOutRuleNotFoundException{
		return PayOutRuleDAO.getByUserId(userId);
	}

	/**
	 * Checks if Rules exist for the current hour and day. If yes these
	 * {@link PayOutRule}s are executed.
	 */
	public void checkAllRules() {
		Date date = new Date();
		Calendar calendar = GregorianCalendar.getInstance();
		calendar.setTime(date);
		int hour = calendar.get(Calendar.HOUR_OF_DAY); // hour formatted in 24h
		int day = calendar.get(Calendar.DAY_OF_WEEK); // day of week (sun = 1, mon = 2,...sat = 7)

		List<PayOutRule> rules;
		try {
			rules = PayOutRuleDAO.get(hour, day);
			PayOutRule tempRule;
			for (int i = 0; i < rules.size(); i++) {
				tempRule = rules.get(i);
				try {
					UserAccount user = UserAccountService.getInstance().getById(tempRule.getUserId());
					if (user.getBalance().compareTo(Config.TRANSACTION_FEE) == 1) {
						PayOutTransaction pot = new PayOutTransaction();
						pot.setUserID(user.getId());
						pot.setBtcAddress(tempRule.getPayoutAddress());
						pot.setAmount(user.getBalance().subtract(Config.TRANSACTION_FEE));
						PayOutTransactionService.getInstance().createPayOutTransaction(user.getUsername(),pot);
					}
				} catch (UserAccountNotFoundException | BitcoinException e) {
				}
			}
		} catch (PayOutRuleNotFoundException e1) {
			// do nothing (no PayOutRule found for time/day)
		}
	}
}
