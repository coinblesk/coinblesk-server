package ch.uzh.csg.coinblesk.server.service;

import java.math.BigDecimal;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ch.uzh.csg.coinblesk.responseobject.PayOutRulesTransferObject;
import ch.uzh.csg.coinblesk.server.clientinterface.IBitcoinWallet;
import ch.uzh.csg.coinblesk.server.clientinterface.IPayOutRule;
import ch.uzh.csg.coinblesk.server.clientinterface.IPayOutTransaction;
import ch.uzh.csg.coinblesk.server.clientinterface.IUserAccount;
import ch.uzh.csg.coinblesk.server.dao.PayOutRuleDAO;
import ch.uzh.csg.coinblesk.server.domain.PayOutRule;
import ch.uzh.csg.coinblesk.server.domain.UserAccount;
import ch.uzh.csg.coinblesk.server.util.Config;
import ch.uzh.csg.coinblesk.server.util.exceptions.PayOutRuleNotFoundException;
import ch.uzh.csg.coinblesk.server.util.exceptions.PayOutRulesAlreadyDefinedException;
import ch.uzh.csg.coinblesk.server.util.exceptions.UserAccountNotFoundException;

/**
 * Service class for {@link PayOutRule}s.
 *
 */
@Service
public class PayOutRuleService implements IPayOutRule{
	@Autowired
	private PayOutRuleDAO payOutRuleDAO;
	@Autowired
	private IPayOutTransaction payOutTransactionService;
	@Autowired
	private IUserAccount userAccountService;
	@Autowired
	private IBitcoinWallet bitcoindService;
	
	public static Boolean testingMode = false;

	@Override
	@Transactional
	public void createRule(PayOutRulesTransferObject porto, String username) throws UserAccountNotFoundException, PayOutRulesAlreadyDefinedException {
		UserAccount user = userAccountService.getByUsername(username);
		long userId = user.getId();
		boolean noRulesDefined = true;
		try {
			noRulesDefined = payOutRuleDAO.getByUserId(userId).isEmpty();
		} catch (PayOutRuleNotFoundException e) {
			noRulesDefined = true;
		}
		if(noRulesDefined && porto.getPayOutRulesList().size() <= 28 || testingMode){
			ch.uzh.csg.coinblesk.model.PayOutRule por;
			for(int i = 0;i<porto.getPayOutRulesList().size();i++) {
				por = porto.getPayOutRulesList().get(i);
				por.setUserId(userId);
				if(!bitcoindService.validateAddress(por.getPayoutAddress())){
				    //TODO: rewrite after change to bitcoinj
			        assert(false);
//					throw new Exception("Invalid Payout Address");
				}
			}
		} else {
			throw new PayOutRulesAlreadyDefinedException();
		}
		payOutRuleDAO.createPayOutRules(porto.getPayOutRulesList());
	}

	@Override
	@Transactional(readOnly = true)
	public List<PayOutRule> getRules(String username) throws PayOutRuleNotFoundException, UserAccountNotFoundException {
		UserAccount user = userAccountService.getByUsername(username);
		return payOutRuleDAO.getByUserId(user.getId());
	}

	@Override
	@Transactional
	public void deleteRules(String username) throws UserAccountNotFoundException {
		UserAccount user = userAccountService.getByUsername(username);
		payOutRuleDAO.deleteRules(user.getId());
	}

	@Override
	@Transactional
	public void checkBalanceLimitRules(UserAccount sellerAccount) throws PayOutRuleNotFoundException, UserAccountNotFoundException {
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

	@Override
	@Transactional(readOnly = true)
	public List<PayOutRule> getRules(long userId) throws PayOutRuleNotFoundException{
		return payOutRuleDAO.getByUserId(userId);
	}

	@Override
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
				} catch (UserAccountNotFoundException  e) {
				}
			}
		} catch (PayOutRuleNotFoundException e1) {
			// do nothing (no PayOutRule found for time/day)
		}
	}
}
