package ch.uzh.csg.mbps.server.util;

import java.io.IOException;

import net.minidev.json.parser.ParseException;

import org.apache.log4j.Logger;

import ch.uzh.csg.mbps.server.domain.PayOutRule;
import ch.uzh.csg.mbps.server.service.PayOutRuleService;

/**
 * Task executed by cron job for checking all {@link PayOutRule}s.
 *
 */
public class HourlyTask {
	private static Logger LOGGER = Logger.getLogger(HourlyTask.class);

	/**
	 * Update is executed every 60minutes.
	 */
	public void update() {
		//check payout rules
		PayOutRuleService.getInstance().checkAllRules();
		LOGGER.info("Cronjob is executing PayOutRules-Task.");
		
		// update USD/CHF-ExchangeRate
		updateUsdChf();
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