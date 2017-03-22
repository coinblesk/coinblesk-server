/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.coinblesk.server.service;

import java.io.IOException;
import java.math.BigDecimal;
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
import org.springframework.test.context.TestExecutionListeners;

import com.coinblesk.bitcoin.TimeLockedAddress;
import com.coinblesk.json.v1.UserAccountTO;
import com.coinblesk.server.config.AppConfig;
import com.coinblesk.server.entity.Keys;
import com.coinblesk.server.entity.UserAccount;
import com.coinblesk.server.utilTest.CoinbleskTest;
import com.coinblesk.server.utilTest.FakeTxBuilder;
import com.github.springtestdbunit.DbUnitTestExecutionListener;
import com.github.springtestdbunit.annotation.DatabaseSetup;
import com.github.springtestdbunit.annotation.DatabaseTearDown;

/**
 *
 * @author Thomas Bocek
 */
@TestExecutionListeners(listeners = DbUnitTestExecutionListener.class, mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
public class UserAccountServiceTest extends CoinbleskTest {
	@MockBean
	private MailService mailService;

	@Autowired
	private UserAccountService userAccountService;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private TxQueueService txQueueService;

	@Autowired
	private KeyService keyService;

	@Autowired
	private WalletService walletService;

	@Autowired
	private AppConfig cfg;

	final private ECKey ecKeyClient = new ECKey();
	final private ECKey ecKeyServer = new ECKey();

	private int counter = 0;

	@Before
	public void before() throws IOException, UnreadableWalletException, BlockStoreException, InterruptedException {
		System.setProperty("coinblesk.config.dir", "/tmp/lib/coinblesk" + (counter++));
		if (counter > 0) {
			walletService.init();
		}

		UserAccount userAccount = new UserAccount();
		userAccount
				.setBalance(BigDecimal.ONE)
				.setCreationDate(new Date(1))
				.setDeleted(false)
				.setEmail("test@test.test")
				.setEmailToken(null)
				.setPassword(passwordEncoder.encode("test"))
				.setUsername("blib");
		userAccountService.save(userAccount);

		Keys keys = keyService.storeKeysAndAddress(ecKeyClient.getPubKey(), ecKeyServer.getPubKey(),
				ecKeyServer.getPrivKeyBytes()).element1();

		TimeLockedAddress address = new TimeLockedAddress(ecKeyClient.getPubKey(), ecKeyServer.getPubKey(), 123456);

		keyService.storeTimeLockedAddress(keys, address);
	}

	@After
	public void after() {
		walletService.shutdown();
	}

	@Test
	@DatabaseSetup("/EmptyDatabase.xml")
	@DatabaseTearDown("/EmptyDatabase.xml")
	public void testTransferFailed() {
		UserAccountTO result = userAccountService.transferP2SH(ecKeyClient, "test@test.test");
		Mockito.verify(mailService, Mockito.times(1)).sendAdminMail(Mockito.anyString(), Mockito.anyString());
		Assert.assertFalse(result.isSuccess());
	}

	@Test
	@DatabaseSetup("/EmptyDatabase.xml")
	@DatabaseTearDown("/EmptyDatabase.xml")
	public void testTransferSuccess() throws BlockStoreException, VerificationException, PrunedException {
		Block block = FakeTxBuilder.makeSolvedTestBlock(walletService.blockChain().getBlockStore(),
				cfg.getPotPrivateKeyAddress().toAddress(cfg.getNetworkParameters()));
		walletService.blockChain().add(block);
		UserAccountTO result = userAccountService.transferP2SH(ecKeyClient, "test@test.test");
		Assert.assertTrue(result.isSuccess());
		List<Transaction> list = txQueueService.all(UnitTestParams.get());
		Assert.assertEquals(1, list.size());
	}
}
