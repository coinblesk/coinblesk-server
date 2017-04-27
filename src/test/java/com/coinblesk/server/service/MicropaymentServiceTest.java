package com.coinblesk.server.service;

import com.coinblesk.bitcoin.TimeLockedAddress;
import com.coinblesk.server.config.AppConfig;
import com.coinblesk.server.controller.MicroPaymentControllerTest;
import com.coinblesk.server.dao.AccountRepository;
import com.coinblesk.server.entity.Account;
import com.coinblesk.server.utilTest.CoinbleskTest;
import com.coinblesk.server.utilTest.FakeTxBuilder;
import org.bitcoinj.core.*;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Created by Sebastian Stephan on 27.04.17.
 */
public class MicropaymentServiceTest extends CoinbleskTest {

	@Autowired
	private MicropaymentService micropaymentService;

	@Autowired
	private AppConfig appConfig;

	@Autowired
	private AccountService accountService;

	@Autowired
	private AccountRepository accountRepository;

	@Test
	public void getTotalPendingChannelsSum() throws Exception {
		ECKey acc1 = new ECKey();
		ECKey acc2 = new ECKey();
		ECKey serverKey1 = accountService.createAcount(acc1); // Two accounts with open channels
		ECKey serverKey2 = accountService.createAcount(acc2);
		accountService.createAcount(new ECKey()); // Account with no open channel

		TimeLockedAddress address1 = accountService.createTimeLockedAddress(acc1,
			Instant.now().plus(Duration.ofDays(90)).getEpochSecond()).getTimeLockedAddress();
		TimeLockedAddress address2 = accountService.createTimeLockedAddress(acc2,
			Instant.now().plus(Duration.ofDays(100)).getEpochSecond()).getTimeLockedAddress();

		Transaction fundingTx1 = FakeTxBuilder.createFakeTxWithoutChangeAddress(params(), address1.getAddress(params()));
		Transaction fundingTx2 = FakeTxBuilder.createFakeTxWithoutChangeAddress(params(), address2.getAddress(params()));

		Transaction tx1 = MicroPaymentControllerTest.createChannelTx(10000, acc1, serverKey1, address1, 337, params(), fundingTx1.getOutput(0));
		Transaction tx2 = MicroPaymentControllerTest.createChannelTx(10000, acc1, serverKey2, address2, 662, params(), fundingTx2.getOutput(0));

		Account account1 = accountRepository.findByClientPublicKey(acc1.getPubKey())
			.channelTransaction(tx1.bitcoinSerialize())
			.broadcastBefore(address1.getLockTime());
		Account account2 = accountRepository.findByClientPublicKey(acc2.getPubKey())
			.channelTransaction(tx2.bitcoinSerialize())
			.broadcastBefore(address2.getLockTime());
		accountRepository.save(Arrays.asList(account1, account2));

		assertThat(micropaymentService.getTotalPendingChannelSum(), is(Coin.valueOf(999)));
	}

	private NetworkParameters params() {
		return appConfig.getNetworkParameters();
	}

}
