package com.coinblesk.server.controller;

import com.coinblesk.server.config.AppConfig;
import com.coinblesk.server.dao.AccountRepository;
import com.coinblesk.server.entity.Account;
import com.coinblesk.server.service.AccountService;
import com.coinblesk.server.service.MicropaymentService;
import com.coinblesk.server.service.WalletService;
import com.coinblesk.server.utilTest.CoinbleskTest;
import com.coinblesk.server.utilTest.FakeTxBuilder;
import org.bitcoinj.core.*;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.CoreMatchers.is;


/**
 * @author Sebastian Stephan
 */
public class PayoutTest extends CoinbleskTest {
	@Autowired
	private AccountService accountService;
	@Autowired
	private WalletService walletService;
	@Autowired
	private AccountRepository accountRepository;
	@Autowired
	private MicropaymentService micropaymentService;
	@Autowired
	private AppConfig appConfig;

	private NetworkParameters params() {
		return appConfig.getNetworkParameters();
	}

	@Test
	public void payout_works() throws Exception {
		final Address serverPot = appConfig.getPotPrivateKeyAddress().toAddress(params());
		Transaction fundPot = FakeTxBuilder.createFakeTxWithoutChangeAddress(params(), serverPot);
		mineTransaction(fundPot);

		ECKey user = new ECKey();
		ECKey serverKey = accountService.createAccount(user);
		final Address microPotAddress = serverKey.toAddress(params());
		walletService.getWallet().importKey(ECKey.fromPrivate(accountService.getByClientPublicKey(user.getPubKey()).serverPrivateKey()));

		Transaction funding = FakeTxBuilder.createFakeTxWithoutChangeAddress(params(), microPotAddress);
		mineTransaction(funding);
		giveUserBalance(user, 100000L);
		Coin potBefore = micropaymentService.getMicroPaymentPotValue();

		Transaction tx = micropaymentService.payOutVirtualBalance(user, new ECKey().toAddress(params()));
		assertThat(tx.getInputs().size(), is(1));
		mineTransaction(tx);
		assertThat(micropaymentService.getMicroPaymentPotValue(), is(potBefore.minus(Coin.valueOf(100000L))));

	}

	private void mineTransaction(Transaction tx) throws PrunedException {
		Block lastBlock = walletService.blockChain().getChainHead().getHeader();
		Transaction spendTXClone = new Transaction(params(), tx.bitcoinSerialize());
		Block newBlock = FakeTxBuilder.makeSolvedTestBlock(lastBlock, spendTXClone);
		walletService.blockChain().add(newBlock);
	}

	private void giveUserBalance(ECKey user, long balance) {
		Account account = accountService.getByClientPublicKey(user.getPubKey());
		accountRepository.save(account.virtualBalance(balance));
	}
}
