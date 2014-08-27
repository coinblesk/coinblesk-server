package ch.uzh.csg.mbps.server.service;

import java.math.BigDecimal;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ch.uzh.csg.mbps.server.clientinterface.IServerAccount;
import ch.uzh.csg.mbps.server.clientinterface.IServerPayOutRule;
import ch.uzh.csg.mbps.server.clientinterface.IServerPayOutTransaction;
import ch.uzh.csg.mbps.server.dao.ServerPayOutRuleDAO;
import ch.uzh.csg.mbps.server.domain.ServerAccount;
import ch.uzh.csg.mbps.server.domain.ServerPayOutRule;
import ch.uzh.csg.mbps.server.util.BitcoindController;
import ch.uzh.csg.mbps.server.util.Config;
import ch.uzh.csg.mbps.server.util.exceptions.PayOutRuleNotFoundException;
import ch.uzh.csg.mbps.server.util.exceptions.ServerAccountNotFoundException;
import ch.uzh.csg.mbps.server.util.exceptions.ServerPayOutRuleNotFoundException;
import ch.uzh.csg.mbps.server.util.exceptions.ServerPayOutRulesAlreadyDefinedException;
import ch.uzh.csg.mbps.server.util.web.ServerPayOutRulesTransferObject;

import com.azazar.bitcoin.jsonrpcclient.BitcoinException;

/**
 * Service class for {@link ServerPayOutRule}s.
 * 
 */
@Service
public class ServerPayOutRuleService implements IServerPayOutRule {
	//TODO: mehmet Tests
	
	@Autowired
	private ServerPayOutRuleDAO serverPayOutRuleDAO;
	@Autowired
	private IServerPayOutTransaction serverPayOutTransactionService;
	@Autowired
	private IServerAccount serverAccountService;
	
	public static Boolean testingMode = false;

	@Override
	@Transactional
	public void createRule(ServerPayOutRulesTransferObject sporto, String url) throws ServerAccountNotFoundException, BitcoinException, ServerPayOutRulesAlreadyDefinedException {

		ServerAccount serveraccount = serverAccountService.getByUrl(url);
		long serverAccountId = serveraccount.getId();
		boolean noRulesDefined = true;
		try {
			noRulesDefined = serverPayOutRuleDAO.getByServerAccountId(serverAccountId).isEmpty();
		} catch (ServerPayOutRuleNotFoundException e){
			noRulesDefined = true;
		}
		if(noRulesDefined || testingMode){			
			ServerPayOutRule spor;
			for (int i = 0; i < sporto.getPayOutRulesList().size(); i++) {
				spor = sporto.getPayOutRulesList().get(i);
				spor.setServerAccountId(serverAccountId);
				if (!BitcoindController.validateAddress(spor.getPayoutAddress())) {
					throw new BitcoinException("Invalid Payout Address");
				}
			}
		}  else {
			throw new ServerPayOutRulesAlreadyDefinedException();
		}
		serverPayOutRuleDAO.createPayOutRules(sporto.getPayOutRulesList());
	}

	@Override
	@Transactional(readOnly = true)
	public List<ServerPayOutRule> getRulesByUrl(String url) throws ServerAccountNotFoundException, ServerPayOutRuleNotFoundException {
		ServerAccount serverAccount = serverAccountService.getByUrl(url);
		return serverPayOutRuleDAO.getByServerAccountId(serverAccount.getId());
	}

	@Override
	@Transactional(readOnly = true)
	public List<ServerPayOutRule> getRulesById(long serverAccountId) throws ServerPayOutRuleNotFoundException {
		return serverPayOutRuleDAO.getByServerAccountId(serverAccountId);
	}

	@Override
	@Transactional
	public void deleteRules(String url) throws ServerAccountNotFoundException {
		ServerAccount serverAccount = serverAccountService.getByUrl(url);
		serverPayOutRuleDAO.deleteRules(serverAccount.getId());
	}

	@Override
	@Transactional
	public void checkBalanceLimitRules(ServerAccount serverAccount) throws ServerAccountNotFoundException, BitcoinException, ServerPayOutRuleNotFoundException {
		serverAccount = serverAccountService.getById(serverAccount.getId());
		List<ServerPayOutRule> rules = serverPayOutRuleDAO.getByServerAccountId(serverAccount.getId());
		
		ServerPayOutRule tempRule;
		for (int i = 0; i < rules.size(); i++) {
			tempRule = rules.get(i);
			if (tempRule.getBalanceLimit() != null && serverAccount.getActiveBalance().compareTo(tempRule.getBalanceLimit()) == 1) {
				BigDecimal amount = serverAccount.getActiveBalance().subtract(Config.TRANSACTION_FEE);
				String address = tempRule.getPayoutAddress();
				serverPayOutTransactionService.createPayOutTransaction(serverAccount.getUrl(), amount, address);
			}
		}
	}

	@Override
	@Transactional
	public void checkAllRules() {
		Date date = new Date();
		Calendar calendar = GregorianCalendar.getInstance();
		calendar.setTime(date);
		int hour = calendar.get(Calendar.HOUR_OF_DAY); // hour formatted in 24h
		int day = calendar.get(Calendar.DAY_OF_WEEK); // day of week (sun = 1, mon = 2,...sat = 7)
		List<ServerPayOutRule> rules;
		try {
			rules = serverPayOutRuleDAO.get(hour, day);
			ServerPayOutRule tempRule;
			for (int i = 0; i < rules.size(); i++) {
				tempRule = rules.get(i);
				try {
					ServerAccount serverAccount = serverAccountService.getById(tempRule.getServerAccountId());
					if (serverAccount.getActiveBalance().compareTo(Config.TRANSACTION_FEE) == 1) {
						BigDecimal amount = serverAccount.getActiveBalance().subtract(Config.TRANSACTION_FEE);
						String address = tempRule.getPayoutAddress();
						serverPayOutTransactionService.createPayOutTransaction(serverAccount.getUrl(), amount, address);
					}
				} catch (ServerAccountNotFoundException | BitcoinException e) {
				}
			}
		} catch (PayOutRuleNotFoundException e1) {
			// do nothing (no PayOutRule found for time/day)
		}
	}
}