package ch.uzh.csg.coinblesk.server.clientinterface;

import java.util.List;

import ch.uzh.csg.coinblesk.responseobject.PayOutRulesTransferObject;
import ch.uzh.csg.coinblesk.server.domain.PayOutRule;
import ch.uzh.csg.coinblesk.server.domain.UserAccount;
import ch.uzh.csg.coinblesk.server.util.exceptions.PayOutRuleNotFoundException;
import ch.uzh.csg.coinblesk.server.util.exceptions.PayOutRulesAlreadyDefinedException;
import ch.uzh.csg.coinblesk.server.util.exceptions.UserAccountNotFoundException;

import com.azazar.bitcoin.jsonrpcclient.BitcoinException;

public interface IPayOutRule {

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
	public void createRule(PayOutRulesTransferObject porto, String username) throws UserAccountNotFoundException, BitcoinException, PayOutRulesAlreadyDefinedException;

	/**
	 * Returns ArrayList with all {@link PayOutRule}s for UserAccount with username.
	 * 
	 * @param username
	 * @return ArrayList<PayOutRules>
	 * @throws PayOutRuleNotFoundException
	 * @throws UserAccountNotFoundException
	 */
	public List<PayOutRule> getRules(String username) throws PayOutRuleNotFoundException, UserAccountNotFoundException;

	/**
	 * Deletes all {@link PayOutRule}s for UserAccount with username.
	 * 
	 * @param username
	 * @throws UserAccountNotFoundException
	 */
	public void deleteRules(String username) throws UserAccountNotFoundException;

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
	public void checkBalanceLimitRules(UserAccount sellerAccount) throws PayOutRuleNotFoundException, UserAccountNotFoundException, BitcoinException;

	/**
	 * Returns all {@link PayOutRule}s assigned to {@link UserAccount} with userId
	 * 
	 * @param userId
	 * @return List<PayOutRule>
	 * @throws PayOutRuleNotFoundException
	 */
	public List<PayOutRule> getRules(long userId) throws PayOutRuleNotFoundException;

	/**
	 * Checks if Rules exist for the current hour and day. If yes these
	 * {@link PayOutRule}s are executed.
	 */
	public void checkAllRules();
}
