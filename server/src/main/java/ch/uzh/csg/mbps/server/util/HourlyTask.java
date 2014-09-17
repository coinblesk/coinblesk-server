package ch.uzh.csg.mbps.server.util;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;

import net.minidev.json.parser.ParseException;

import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;

import ch.uzh.csg.mbps.server.clientinterface.IPayOutRule;
import ch.uzh.csg.mbps.server.clientinterface.IServerAccount;
import ch.uzh.csg.mbps.server.clientinterface.IServerAccountTasks;
import ch.uzh.csg.mbps.server.clientinterface.IUserAccount;
import ch.uzh.csg.mbps.server.dao.ServerPublicKeyDAO;
import ch.uzh.csg.mbps.server.domain.PayOutRule;
import ch.uzh.csg.mbps.server.domain.ServerAccount;
import ch.uzh.csg.mbps.server.domain.ServerAccountTasks;
import ch.uzh.csg.mbps.server.domain.UserAccount;
import ch.uzh.csg.mbps.server.service.ServerAccountTasksService.ServerAccountTaskTypes;
import ch.uzh.csg.mbps.server.util.exceptions.ServerAccountNotFoundException;
import ch.uzh.csg.mbps.server.util.exceptions.UserAccountNotFoundException;

import com.azazar.bitcoin.jsonrpcclient.BitcoinException;

/**
 * Task executed by cron job for checking all {@link PayOutRule}s.
 *
 */
public class HourlyTask {
	private static Logger LOGGER = Logger.getLogger(HourlyTask.class);

	@Autowired
	private IUserAccount userAccountService;
	@Autowired
	private MensaXLSExporter mensaXLSExporter;
	@Autowired
	private IPayOutRule payOutRuleService;
	@Autowired
	IServerAccountTasks serverAccountTasksService;
	@Autowired
	ServerPublicKeyDAO serverPublicKeyDAO;
	@Autowired
	IServerAccount serverAccountService;

	/**
	 * Update is executed every 60minutes.
	 */
	public void update() {
		//check payout rules
		payOutRuleService.checkAllRules();
		LOGGER.info("Cronjob is executing PayOutRules-Task.");

		// update USD/CHF-ExchangeRate
		updateUsdChf();
		
		List<ServerAccountTasks> proceeds =  serverAccountTasksService.getProceedAccounts();
		for(ServerAccountTasks remove: proceeds){
			serverAccountTasksService.removeProceedTasks(remove.getToken());
		}
		
		List<ServerAccountTasks> creates = serverAccountTasksService.getAccountsByType(ServerAccountTaskTypes.CREATE_ACCOUNT.getCode());
		for(ServerAccountTasks create: creates){
			UserAccount user = null;
			ServerAccount account = null;
			try {
				user = userAccountService.getByUsername(create.getUsername());
			} catch (UserAccountNotFoundException e1) {
				//ignore
			}
			try {
				account = serverAccountService.getByUrl(create.getUrl());
			} catch (ServerAccountNotFoundException e1) {
				//ignore
			}
			if(account == null && user == null){				
				if(create.getPayoutAddress() == null){				
					try {
						serverAccountTasksService.createNewAccount(create.getUrl(), create.getEmail(), user, create.getToken());
					} catch (Exception e) {
						// ignore
					}
				} else {
					try {
						serverAccountTasksService.updatedPayoutAddress(create.getUrl(), create.getEmail(), user, create.getToken());
					} catch (Exception e) {
						// ignore
					}
				}
			}
		}

		
		List<ServerAccountTasks> upgrades = serverAccountTasksService.getAccountsByType(ServerAccountTaskTypes.ACCEPT_TRUST_ACCOUNT.getCode());
		for(ServerAccountTasks upgrade: upgrades){
			try {
				serverAccountTasksService.upgradedTrustLevel(upgrade.getUsername(), upgrade.getEmail(), upgrade.getUrl(), upgrade.getTrustLevel(), upgrade.getToken());
			} catch (Exception e) {
				//ignore
			}
		}
		
		List<ServerAccountTasks> downgrades = serverAccountTasksService.getAccountsByType(ServerAccountTaskTypes.DECLINE_TRUST_ACCOUNT.getCode());
		for(ServerAccountTasks downgrade: downgrades){
			try {
				serverAccountTasksService.upgradedTrustLevel(downgrade.getUsername(), downgrade.getEmail(), downgrade.getUrl(), downgrade.getTrustLevel(), downgrade.getToken());
			} catch (Exception e) {
				//ignore
			}
			
		}
		//TODO: mehmet include server payout rules hourly task

	}

	/**
	 * Checkes if the sum of all useraccount balances is smaller or equal than
	 * the amount fo Bitcoins available on the server's Bitcoin wallet
	 */
	private void sanityCheck() {
		try {
			BigDecimal sumOfAccountBalances = userAccountService.getSumOfUserAccountBalances();
			BigDecimal bitcoindAccountBalance = BitcoindController.getAccountBalance();
			if(bitcoindAccountBalance.compareTo(sumOfAccountBalances) < 0)
				Emailer.send("bitcoin@ifi.uzh.ch", "[CoinBlesk] Error: Sanity Check failed - Intervention required!", "Important: possible worst case scenario happened! There are more Bitcoins assigned to user accounts than are stored on Bitcoind! " + "SumOfAccountBalances:  " + sumOfAccountBalances.toPlainString() + " BitcoindSum: " + bitcoindAccountBalance.toPlainString());
		} catch (BitcoinException e) {
			Emailer.send("bitcoin@ifi.uzh.ch", "[CoinBlesk] Warning: Problem creating sanity check", "Couldn't compare useraccount balances to bitcoind balances. Exception: " + e.getMessage());
		}
		
	}

	/**
	 * Updates exchange rate for USD/CHF. Is needed for conversion from Bitstamp
	 * exchangrate from USD to CHF.
	 */
	private void updateUsdChf() {
		try {
			ExchangeRates.updateExchangeRateUsdChf();
		} catch (ParseException | IOException e) {
			LOGGER.error("Problem updating USD/CHF Exchange Rate "
					+ e.getMessage());
		}
	}
}
