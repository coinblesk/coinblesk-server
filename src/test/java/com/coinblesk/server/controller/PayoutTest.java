package com.coinblesk.server.controller;

import com.coinblesk.server.config.AppConfig;
import com.coinblesk.server.dao.AccountRepository;
import com.coinblesk.server.entity.Account;
import com.coinblesk.server.service.AccountService;
import com.coinblesk.server.service.MicropaymentService;
import com.coinblesk.server.service.WalletService;
import com.coinblesk.server.utilTest.CoinbleskTest;
import com.coinblesk.server.utilTest.FakeTxBuilder;
import com.coinblesk.util.DTOUtils;
import org.bitcoinj.core.*;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.CoreMatchers.is;


/**
 * @author Sebastian Stephan
 */
public class PayoutTest extends CoinbleskTest {
	public static final String URL_PAYOUT = "/payment/payout";
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
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	public void payout_works() throws Exception {
		ECKey user = new ECKey();
		accountService.createAccount(user);
		final Address microPotAddress = appConfig.getMicroPaymentPotPrivKey().toAddress(params());
		walletService.getWallet().importKey(ECKey.fromPrivate(accountService.getByClientPublicKey(user.getPubKey()).serverPrivateKey()));

		// Load up some money to the server pot
		Transaction funding = FakeTxBuilder.createFakeTxWithoutChangeAddress(params(), microPotAddress);
		mineTransaction(funding);

		// Give the user virtual balance
		giveUserBalance(user, 100000L);
		Coin potBefore = micropaymentService.getMicroPaymentPotValue();

		// Payout
		String txString = micropaymentService.payOutVirtualBalance(user,
			new ECKey().toAddress(params()).toBase58()).transaction;
		Transaction tx = new Transaction(params(), DTOUtils.fromHex(txString));
		tx.verify();
		TransactionOutput serverOut = tx.getOutputs().stream().filter(out ->
			Objects.equals(out.getAddressFromP2PKHScript(params()), microPotAddress))
			.findFirst().get();
		assertThat(serverOut.getValue(), is(Coin.COIN.minus(Coin.valueOf(100000L))));

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
