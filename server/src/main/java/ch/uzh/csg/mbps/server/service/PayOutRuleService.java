package ch.uzh.csg.mbps.server.service;

import java.math.BigDecimal;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ch.uzh.csg.mbps.responseobject.PayOutRulesTransferObject;
import ch.uzh.csg.mbps.server.clientinterface.IUserAccount;
import ch.uzh.csg.mbps.server.dao.PayOutRuleDAO;
import ch.uzh.csg.mbps.server.domain.PayOutRule;
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
@Service
public class PayOutRuleService {
	@Autowired
	private PayOutRuleDAO payOutRuleDAO;
	@Autowired
	private PayOutTransactionService payOutTransactionService;
	@Autowired
	private IUserAccount userAccountService;
	
	public static Boolean testingMode = false;

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
	@Transactional
	public void createRule(PayOutRulesTransferObject porto, String username) throws UserAccountNotFoundException, BitcoinException, PayOutRulesAlreadyDefinedException {
		UserAccount user = userAccountService.getByUsername(username);
		long userId = user.getId();
		boolean noRulesDefined = true;
		try {
			noRulesDefined = payOutRuleDAO.getByUserId(userId).isEmpty();
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
		payOutRuleDAO.createPayOutRules(porto.getPayOutRulesList());
	}

	/**
	 * Returns ArrayList with all {@link PayOutRule}s for UserAccount with username.
	 * 
	 * @param username
	 * @return ArrayList<PayOutRules>
	 * @throws PayOutRuleNotFoundException
	 * @throws UserAccountNotFoundException
	 */
	@Transactional(readOnly = true)
	public List<PayOutRule> getRules(String username) throws PayOutRuleNotFoundException, UserAccountNotFoundException {
		UserAccount user = userAccountService.getByUsername(username);
		return payOutRuleDAO.getByUserId(user.getId());
	}

	/**
	 * Deletes all {@link PayOutRule}s for UserAccount with username.
	 * 
	 * @param username
	 * @throws UserAccountNotFoundException
	 */
	@Transactional
	public void deleteRules(String username) throws UserAccountNotFoundException {
		UserAccount user = userAccountService.getByUsername(username);
		payOutRuleDAO.deleteRules(user.getId());
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
	@Transactional
	public void checkBalanceLimitRules(UserAccount sellerAccount) throws PayOutRuleNotFoundException, UserAccountNotFoundException, BitcoinException {
		sellerAccount = userAccountService.getById(sellerAccount.getId());
		List<PayOutRule> rules = payOutRuleDAO.getByUserId(sellerAccount.getId());

		PayOutRule tempRule;
		for (int i=0; i < rules.size(); i++){
			tempRule = rules.get(i);
			if(tempRule.getBalanceLimit() != null && sellerAccount.getBalance().compareTo(tempRule.getBalanceLimit()) == 1){
				//set amount to account balance (minus transaction fee)
				BigDecimal amount = sellerAccount.getBalance().subtract(Config.TRANSACTION_FEE); 
				String address = tempRule.getPayoutAddress();
				payOutTransactionService.createPayOutTransaction(sellerAccount.getUsername(), amount, address);
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
	@Transactional(readOnly = true)
	public List<PayOutRule> getRules(long userId) throws PayOutRuleNotFoundException{
		return payOutRuleDAO.getByUserId(userId);
	}

	/**
	 * Checks if Rules exist for the current hour and day. If yes these
	 * {@link PayOutRule}s are executed.
	 */
	@Transactional
	public void checkAllRules() {
		Date date = new Date();
		Calendar calendar = GregorianCalendar.getInstance();
		calendar.setTime(date);
		int hour = calendar.get(Calendar.HOUR_OF_DAY); // hour formatted in 24h
		int day = calendar.get(Calendar.DAY_OF_WEEK); // day of week (sun = 1, mon = 2,...sat = 7)

		List<PayOutRule> rules;
		try {
			rules = payOutRuleDAO.get(hour, day);
			PayOutRule tempRule;
			for (int i = 0; i < rules.size(); i++) {
				tempRule = rules.get(i);
				try {
					UserAccount user = userAccountService.getById(tempRule.getUserId());
					if (user.getBalance().compareTo(Config.TRANSACTION_FEE) == 1) {
						BigDecimal amount = user.getBalance().subtract(Config.TRANSACTION_FEE);
						String address = tempRule.getPayoutAddress();
						payOutTransactionService.createPayOutTransaction(user.getUsername(), amount, address);
					}
				} catch (UserAccountNotFoundException | BitcoinException e) {
				}
			}
		} catch (PayOutRuleNotFoundException e1) {
			// do nothing (no PayOutRule found for time/day)
		}
	}
}
