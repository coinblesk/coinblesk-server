/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.coinblesk.server.service;

/**
 * This service fetches the current fees that need to be paid in the bitcoin network to to include a
 * transaction into the blockchain. If the fee is not set right, the transaction may never get included. It is
 * important to get this right, as the server needs to cash in open channels and it needs to do that before a
 * timelock expires. An attack scenario could be to prevent the server to cash in the transaction, so that the
 * timelock expires and the refund tx can be collected by the attacker. Thus, this server transfers the
 * transaction to be cashed in to another server in case of a DoS. However, if the service returns a wrong
 * amount, an attack could be successful. As we expect the fees to rise, can can set a lower bound to 200
 * satoshis per bytes (22.3.2017).
 *
 * @author Thomas Bocek
 */
import com.coinblesk.server.utils.DTOUtils;
import com.coinblesk.util.Pair;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class FeeService {

	private final static Logger LOG = LoggerFactory.getLogger(FeeService.class);

	// current limit is 5000 requests per hour
	public final static int ONE_MINUTE_MILLIS = 60 * 1000;
	// 5min
	public final static int CACHING_FEE_MILLIS = 15 * ONE_MINUTE_MILLIS;

	//satoshis per bytes, see https://bitcoinfees.21.co/
	public final static int DEFAULT_FEE = 200;

	public final static String URL = "https://bitcoinfees.21.co/api/v1/fees/recommended";

	private Pair<Long, Integer> cachedFee;

	private final Object lock = new Object();

	public int fee() throws IOException {
		synchronized (lock) {
			if (cachedFee == null || cachedFee.element0() + CACHING_FEE_MILLIS < System.currentTimeMillis()) {
				int fee = askFee();
				cachedFee = new Pair<>(System.currentTimeMillis(), fee);
			}
			return cachedFee.element1();
		}
	}

	private int askFee() throws IOException {
		final StringBuffer response = ServiceUtils.doHttpRequest(URL);
		final FeeService.Root root = DTOUtils.fromJSON(response.toString(), FeeService.Root.class);
		try {
			return Integer.parseInt(root.hourFee);
		} catch (Exception e) {
			LOG.error("could not get fee", e);
			return DEFAULT_FEE;
		}
	}

	private void setFee(int fee) {
		synchronized (lock) {
			cachedFee = new Pair<>(System.currentTimeMillis(), fee);
		}
	}

	/*-
     * minimized JSON representation. Query result looks like:
     * {
     *   "fastestFee":220,
     *   "halfHourFee":220,
     *   "hourFee":200
     * }
     */
	private static class Root {
		private String fastestFee;
		private String halfHourFee;
		private String hourFee;
	}

	final static public class FeeTask {

		@Autowired
		private FeeService feeService;

		// call every 5 minutes
		@Scheduled(fixedRate = CACHING_FEE_MILLIS / 3)
		public void doTask() throws Exception {
			int fee = feeService.askFee();
			feeService.setFee(fee);
		}
	}
}
