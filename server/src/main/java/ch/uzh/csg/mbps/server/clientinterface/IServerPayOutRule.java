package ch.uzh.csg.mbps.server.clientinterface;

import java.util.List;

import ch.uzh.csg.mbps.server.domain.ServerAccount;
import ch.uzh.csg.mbps.server.domain.ServerPayOutRule;
import ch.uzh.csg.mbps.server.util.exceptions.ServerAccountNotFoundException;
import ch.uzh.csg.mbps.server.util.exceptions.ServerPayOutRuleNotFoundException;
import ch.uzh.csg.mbps.server.util.exceptions.ServerPayOutRulesAlreadyDefinedException;
import ch.uzh.csg.mbps.server.util.exceptions.UserAccountNotFoundException;
import ch.uzh.csg.mbps.server.util.web.ServerPayOutRulesTransferObject;

import com.azazar.bitcoin.jsonrpcclient.BitcoinException;

public interface IServerPayOutRule {

	/**
	 * Creates a new set of {@link ServerPayOutRule}s for assigned
	 * ServerAccount. The rules are unlimited.
	 * 
	 * @param porto ServerPayOutRulesTransferObject containing list of ServerPayOutRules
	 * @param url
	 * @throws ServerAccountNotFoundException
	 * @throws BitcoinException
	 * @throws ServerPayOutRulesAlreadyDefinedException 
	 */
	public void createRule(ServerPayOutRulesTransferObject sporto, String url) throws ServerAccountNotFoundException, BitcoinException, ServerPayOutRulesAlreadyDefinedException;

	/**
	 * Returns List with all {@link ServerPayOutRule}s for ServerAccount
	 * with url.
	 * 
	 * @param url
	 * @return List<ServerPayOutRules>
	 * @throws ServerAccountNotFoundException
	 * @throws ServerPayOutRuleNotFoundException 
	 */
	public List<ServerPayOutRule> getRulesByUrl(String url) throws ServerAccountNotFoundException, ServerPayOutRuleNotFoundException;

	/**
	 * Returns all {@link ServerPayOutRule}s assigned to {@link ServerAccount}
	 * with serverAccountId
	 * 
	 * @param serverAccountId
	 * @return List<ServerPayOutRule>
	 * @throws ServerPayOutRuleNotFoundException 
	 */
	public List<ServerPayOutRule> getRulesById(long serverAccountId) throws ServerPayOutRuleNotFoundException;

	/**
	 * Deletes all {@link ServerPayOutRule}s for ServerAccount with url.
	 * 
	 * @param url
	 * @throws ServerAccountNotFoundException
	 */
	public void deleteRules(String url) throws ServerAccountNotFoundException;

	/**
	 * Checks if {@link ServerPayOutRule} is assigned for serverAccount. If
	 * rules exist for serverAccount they are checked if the balance of
	 * serverAccount exceeds the one defined in the rule. If yes a new PayOut is
	 * initiated according to the PayOutRule.
	 * 
	 * @param serverAccount
	 * @throws UserAccountNotFoundException
	 * @throws BitcoinException
	 * @throws ServerPayOutRuleNotFoundException 
	 */
	public void checkBalanceLimitRules(ServerAccount serverAccount) throws ServerAccountNotFoundException, BitcoinException, ServerPayOutRuleNotFoundException;

	/**
	 * Checks if Rules exist for the current hour and day. If yes these
	 * {@link ServerPayOutRule}s are executed.
	 */
	public void checkAllRules();	
}
