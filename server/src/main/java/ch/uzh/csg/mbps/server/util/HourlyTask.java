package ch.uzh.csg.mbps.server.util;

import java.io.IOException;
import java.math.BigDecimal;

import net.minidev.json.parser.ParseException;

import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;

import ch.uzh.csg.mbps.server.domain.PayOutRule;
import ch.uzh.csg.mbps.server.service.PayOutRuleService;
import ch.uzh.csg.mbps.server.service.UserAccountService;

import com.azazar.bitcoin.jsonrpcclient.BitcoinException;

/**
 * Task executed by cron job for checking all {@link PayOutRule}s.
 *
 */
public class HourlyTask {
	private static Logger LOGGER = Logger.getLogger(HourlyTask.class);
	
	@Autowired
	private PayOutRuleService payOutRuleService;
	//TODO: fix autowired problem
	@Autowired
	private UserAccountService userAccountService;
	
	/**
	 * Update is executed every 60minutes.
	 */
	public void update() {
		//check payout rules
		payOutRuleService.checkAllRules();
		LOGGER.info("Cronjob is executing PayOutRules-Task.");
		
		// update USD/CHF-ExchangeRate
		updateUsdChf();
		
		//check if enough Bitcoins are available in the system
		sanityCheck();
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
				Emailer.send("simon.kaeser@uzh.ch", "Sanity Check Test Error", "Warning! There are more Bitcoins assigned to user accounts than are stored on Bitcoind! " + "SumOfAccountBalances:  " + sumOfAccountBalances.toPlainString() + " BitcoindSum: " + bitcoindAccountBalance.toPlainString());
		} catch (BitcoinException e) {
			Emailer.send("bitcoin@ifi.uzh.ch", "Sanity Check Test Failed", "Couldn't compare useraccount balances to bitcoind balances. Exception: " + e.getMessage());
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