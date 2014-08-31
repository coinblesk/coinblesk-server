package ch.uzh.csg.mbps.server.util;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;

import net.minidev.json.parser.ParseException;

import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;

import ch.uzh.csg.mbps.server.clientinterface.IUserAccount;
import ch.uzh.csg.mbps.server.domain.PayOutRule;
import ch.uzh.csg.mbps.server.service.PayOutRuleService;

import com.azazar.bitcoin.jsonrpcclient.BitcoinException;

/**
 * Task executed by cron job for checking all {@link PayOutRule}s.
 *
 */
public class HourlyTask {
	private static Logger LOGGER = Logger.getLogger(HourlyTask.class);

	@Autowired
	private PayOutRuleService payOutRuleService;
	@Autowired
	private IUserAccount userAccountService;

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

		//Check for MensaXLS Export
		Date date = new Date();
		Calendar calendar = GregorianCalendar.getInstance();
		calendar.setTime(date);
		int hour = calendar.get(Calendar.HOUR_OF_DAY); // hour formatted in 24h

		//TODO: for mensa testrun only, delete afterwards
		//do mensa export
		if(hour == 23){
			MensaXLSExporter.doQuery();
		}
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