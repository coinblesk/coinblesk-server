package com.coinblesk.server.service;

import com.coinblesk.bitcoin.TimeLockedAddress;
import com.coinblesk.server.config.AppConfig;
import com.coinblesk.server.controller.MicroPaymentTest;
import com.coinblesk.server.dao.AccountRepository;
import com.coinblesk.server.entity.Account;
import com.coinblesk.server.utilTest.CoinbleskTest;
import com.coinblesk.server.utilTest.FakeTxBuilder;
import com.coinblesk.server.utilTest.PaymentChannel;
import org.bitcoinj.core.*;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;

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
	private WalletService walletService;

	@Autowired
	private AccountService accountService;

	@Autowired
	private AccountRepository accountRepository;

	@Test
	public void getPendingChannelValue() throws Exception {
		ECKey acc1 = new ECKey();
		ECKey acc2 = new ECKey();
		ECKey serverKey1 = accountService.createAccount(acc1); // Two accounts with open channels
		ECKey serverKey2 = accountService.createAccount(acc2);
		accountService.createAccount(new ECKey()); // Account with no open channel

		TimeLockedAddress address1 = accountService.createTimeLockedAddress(acc1,
			Instant.now().plus(Duration.ofDays(90)).getEpochSecond()).getTimeLockedAddress();
		TimeLockedAddress address2 = accountService.createTimeLockedAddress(acc2,
			Instant.now().plus(Duration.ofDays(100)).getEpochSecond()).getTimeLockedAddress();

		Transaction fundingTx1 = FakeTxBuilder.createFakeTxWithoutChangeAddress(params(), address1.getAddress(params()));
		Transaction fundingTx2 = FakeTxBuilder.createFakeTxWithoutChangeAddress(params(), address2.getAddress(params()));

		Transaction tx1 = MicroPaymentTest.createChannelTx(10000, acc1, serverKey1, address1, 337, params(), fundingTx1.getOutput(0));
		Transaction tx2 = MicroPaymentTest.createChannelTx(10000, acc1, serverKey2, address2, 662, params(), fundingTx2.getOutput(0));

		Account account1 = accountRepository.findByClientPublicKey(acc1.getPubKey())
			.channelTransaction(tx1.bitcoinSerialize())
			.broadcastBefore(address1.getLockTime());
		Account account2 = accountRepository.findByClientPublicKey(acc2.getPubKey())
			.channelTransaction(tx2.bitcoinSerialize())
			.broadcastBefore(address2.getLockTime());
		accountRepository.save(Arrays.asList(account1, account2));

		assertThat(micropaymentService.getPendingChannelValue(), is(Coin.valueOf(999)));
	}

	@Test
	public void getMicroPaymentPotValue() throws Exception {
		ECKey serverKey1 = accountService.createAccount(new ECKey());
		ECKey serverKey2 = accountService.createAccount(new ECKey());
		walletService.addWatching(serverKey1.toAddress(params()));
		walletService.addWatching(serverKey2.toAddress(params()));
		Transaction tx1 = FakeTxBuilder.createFakeTxWithoutChangeAddress(params(), serverKey1.toAddress(params()));
		Transaction tx2 = FakeTxBuilder.createFakeTxWithoutChangeAddress(params(), serverKey2.toAddress(params()));
		Transaction tx3 = FakeTxBuilder.createFakeTxWithoutChangeAddress(params(), serverKey2.toAddress(params()));
		mineTransaction(tx1);
		mineTransaction(tx2);
		walletService.getWallet().maybeCommitTx(tx3);
		assertThat(micropaymentService.getMicroPaymentPotValue(), is(Coin.valueOf(2, 0)));
		mineTransaction(tx3);
		assertThat(micropaymentService.getMicroPaymentPotValue(), is(Coin.valueOf(3, 0)));
	}

	@Test
	public void testPaymentChannel() throws Exception {
		ECKey clientKey = new ECKey();
		ECKey serverKey1 = accountService.createAccount(clientKey);
		walletService.addWatching(serverKey1.toAddress(params()));
		TimeLockedAddress tla = accountService.createTimeLockedAddress(clientKey,
			Instant.now().plus(Duration.ofDays(90)).getEpochSecond()).getTimeLockedAddress();
		Transaction fundingTx = FakeTxBuilder.createFakeTxWithoutChangeAddress(params(), tla.getAddress(params()));
		watchAndMineTransactions(fundingTx);

		Transaction tx = new PaymentChannel(params(), tla.getAddress(params()), clientKey, serverKey1)
			.addInputs(tla, getUTXOsForAddress(tla))
			.buildTx();
		tx.verify();
		assertThat(tx.getInputs().size(), is(1));
	}

	private NetworkParameters params() {
		return appConfig.getNetworkParameters();
	}

	private TransactionOutput[] getUTXOsForAddress(TimeLockedAddress address) {
		return walletService.getWallet().getWatchedOutputs(true).stream()
			.filter(o -> Objects.equals(o.getAddressFromP2SH(params()), address.getAddress(params())))
			.toArray(TransactionOutput[]::new);
	}

	private void watchAndMineTransactions(Transaction... txs) throws PrunedException {
		for (Transaction tx : Arrays.asList(txs)) {
			watchAllOutputs(tx);
			mineTransaction(tx);
		}
	}

	private void watchAllOutputs(Transaction tx) {
		tx.getOutputs().forEach(output -> {
			if (output.getScriptPubKey().isSentToAddress())
				walletService.addWatching(output.getAddressFromP2PKHScript(params()));

			if (output.getScriptPubKey().isPayToScriptHash())
				walletService.addWatching(output.getAddressFromP2SH(params()));
		});
	}

	private void mineTransaction(Transaction tx) throws PrunedException {
		Block lastBlock = walletService.blockChain().getChainHead().getHeader();
		Transaction spendTXClone = new Transaction(params(), tx.bitcoinSerialize());
		Block newBlock = FakeTxBuilder.makeSolvedTestBlock(lastBlock, spendTXClone);
		walletService.blockChain().add(newBlock);
	}
}
