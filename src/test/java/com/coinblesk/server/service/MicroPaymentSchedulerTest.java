package com.coinblesk.server.service;

import com.coinblesk.bitcoin.TimeLockedAddress;
import com.coinblesk.server.config.AppConfig;
import com.coinblesk.server.entity.Account;
import com.coinblesk.server.utilTest.CoinbleskTest;
import com.coinblesk.server.utilTest.FakeTxBuilder;
import com.coinblesk.server.utilTest.PaymentChannel;
import com.coinblesk.util.DTOUtils;
import org.bitcoinj.core.*;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Spy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@TestPropertySource(properties = {
	"coinblesk.minimumLockTimeSeconds:2",
	"coinblesk.closeSchedulerInterval:1",
	"bitcoin.micropaymentpotprivkey:34820369455086852953900390703369481881944826090436918420632015575575077759509"
})
public class MicroPaymentSchedulerTest extends CoinbleskTest {
	@Autowired
	private WalletService walletService;
	@Autowired
	@Spy
	private MicropaymentService micropaymentService;
	@Autowired
	private AccountService accountService;
	@Autowired
	private AppConfig appConfig;

	public static NetworkParameters params;

	private static ECKey serverKey;

	@Before
	public void setUp() throws Exception {
		params = appConfig.getNetworkParameters();
		serverKey = appConfig.getMicroPaymentPotPrivKey();
	}

	@Test
	@Transactional(propagation = Propagation.NOT_SUPPORTED) // Otherwise lock on account.
	public void schedulerClosesChannels() throws Exception {
		final ECKey senderKey = new ECKey();
		accountService.createAccount(senderKey);
		final ECKey receiverKey = new ECKey();
		accountService.createAccount(receiverKey);

		// Server should try to close this after 2 seconds...
		Long locktime = Instant.now().plus(Duration.ofSeconds(4)).getEpochSecond();
		TimeLockedAddress tla = accountService.createTimeLockedAddress(senderKey, locktime).getTimeLockedAddress();
		walletService.addWatching(tla.getAddress(params));

		// Funding
		Transaction fundingTx = FakeTxBuilder.createFakeTxWithoutChangeAddress(params, tla.getAddress(params));
		Block lastBlock = walletService.blockChain().getChainHead().getHeader();
		Transaction spendTXClone = new Transaction(params, fundingTx.bitcoinSerialize());
		Block newBlock = FakeTxBuilder.makeSolvedTestBlock(lastBlock, spendTXClone);
		walletService.blockChain().add(newBlock);

		// Make an open channel
		PaymentChannel channel = new PaymentChannel(params, tla.getAddress(params), senderKey, serverKey)
			.addInputs(tla, fundingTx.getOutput(0))
			.addToServerOutput(Coin.valueOf(200));
		micropaymentService.microPayment(senderKey, receiverKey.getPublicKeyAsHex(),
			DTOUtils.toHex(channel.buildTx().bitcoinSerialize()), 200L, 1L);

		// Some time passes...
		Thread.sleep(4000L);
		// ... the server should have closed the channel in the meantime
		Account acc = accountService.getByClientPublicKey(senderKey.getPubKey());
		assertThat(acc.isLocked(), is(true));
	}
}
