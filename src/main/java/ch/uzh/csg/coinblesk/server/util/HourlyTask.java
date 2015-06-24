package ch.uzh.csg.coinblesk.server.util;

import java.io.IOException;
import java.util.List;

import net.minidev.json.parser.ParseException;

import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;

import ch.uzh.csg.coinblesk.server.clientinterface.IBitcoinWallet;
import ch.uzh.csg.coinblesk.server.clientinterface.IServerAccount;
import ch.uzh.csg.coinblesk.server.clientinterface.IServerAccountTasks;
import ch.uzh.csg.coinblesk.server.clientinterface.IUserAccount;
import ch.uzh.csg.coinblesk.server.dao.ServerPublicKeyDAO;
import ch.uzh.csg.coinblesk.server.domain.ServerAccount;
import ch.uzh.csg.coinblesk.server.domain.ServerAccountTasks;
import ch.uzh.csg.coinblesk.server.domain.UserAccount;
import ch.uzh.csg.coinblesk.server.service.ServerAccountTasksService.ServerAccountTaskTypes;
import ch.uzh.csg.coinblesk.server.util.exceptions.ServerAccountNotFoundException;
import ch.uzh.csg.coinblesk.server.util.exceptions.UserAccountNotFoundException;

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
	IServerAccountTasks serverAccountTasksService;
	@Autowired
	ServerPublicKeyDAO serverPublicKeyDAO;
	@Autowired
	IServerAccount serverAccountService;
	@Autowired
	IBitcoinWallet bitcoinWalletService;
	@Autowired
    private Emailer emailer;

	/**
	 * Update is executed every 60minutes.
	 */
	public void update() {

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
