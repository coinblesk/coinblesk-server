/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.coinblesk.server.service;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import org.bitcoinj.core.Block;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.PrunedException;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.VerificationException;
import org.bitcoinj.params.UnitTestParams;
import org.bitcoinj.store.BlockStoreException;
import org.bitcoinj.wallet.UnreadableWalletException;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.coinblesk.json.v1.UserAccountTO;
import com.coinblesk.server.config.AppConfig;
import com.coinblesk.server.dao.UserAccountRepository;
import com.coinblesk.server.entity.UserAccount;
import com.coinblesk.server.exceptions.InvalidLockTimeException;
import com.coinblesk.server.exceptions.UserNotFoundException;
import com.coinblesk.server.utilTest.CoinbleskTest;
import com.coinblesk.server.utilTest.FakeTxBuilder;

/**
 * @author Thomas Bocek
 */
public class UserAccountServiceTest extends CoinbleskTest {
	final private ECKey ecKeyClient = new ECKey();

	@MockBean
	private EventService eventService;

	@Autowired
	private UserAccountService userAccountService;

	@Autowired
	private UserAccountRepository userAccountRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private TxQueueService txQueueService;

	@Autowired
	private AccountService accountService;

	@Autowired
	private WalletService walletService;

	@Autowired
	private AppConfig appConfig;

	private int counter = 0;

	@Before
	public void before() throws IOException, UnreadableWalletException, BlockStoreException, InterruptedException,
		InvalidLockTimeException, UserNotFoundException {
		System.setProperty("coinblesk.config.dir", "/tmp/lib/coinblesk" + (counter++));
		if (counter > 0) {
			walletService.init();
		}

		UserAccount userAccount = new UserAccount();
		userAccount.setBalance(BigDecimal.ONE).setCreationDate(new Date(1)).setDeleted(false).setEmail("test@test" +
			"" + ".test").setActivationEmailToken(null).setPassword(passwordEncoder.encode("test"));
		userAccountRepository.save(userAccount);

		accountService.createAccount(ecKeyClient);
		accountService.createTimeLockedAddress(ecKeyClient, Instant.now().plus(Duration.ofDays(7)).getEpochSecond());
	}

	@After
	public void after() {
		walletService.shutdown();
	}

	@Test
	public void testTransferFailed() {
		UserAccountTO result = userAccountService.transferP2SH(ecKeyClient, "test@test.test");
		Mockito.verify(eventService, Mockito.times(1)).error(Mockito.any(), Mockito.anyString());
		Assert.assertFalse(result.isSuccess());
	}

	@Test
	public void testTransferSuccess() throws BlockStoreException, VerificationException, PrunedException {
		Block block = FakeTxBuilder.makeSolvedTestBlock(walletService.blockChain().getBlockStore(), appConfig
			.getPotPrivKey().toAddress(appConfig.getNetworkParameters()));
		walletService.blockChain().add(block);
		UserAccountTO result = userAccountService.transferP2SH(ecKeyClient, "test@test.test");
		Assert.assertTrue(result.isSuccess());
		List<Transaction> list = txQueueService.all(UnitTestParams.get());
		Assert.assertEquals(1, list.size());
	}
}
