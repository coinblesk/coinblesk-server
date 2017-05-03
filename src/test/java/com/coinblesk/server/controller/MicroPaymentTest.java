package com.coinblesk.server.controller;

import com.coinblesk.bitcoin.TimeLockedAddress;
import com.coinblesk.server.config.AppConfig;
import com.coinblesk.server.dao.AccountRepository;
import com.coinblesk.dto.ErrorDTO;
import com.coinblesk.dto.MicroPaymentRequestDTO;
import com.coinblesk.dto.SignedDTO;
import com.coinblesk.server.dao.TimeLockedAddressRepository;
import com.coinblesk.server.entity.Account;
import com.coinblesk.server.entity.TimeLockedAddressEntity;
import com.coinblesk.server.service.AccountService;
import com.coinblesk.server.service.ForexService;
import com.coinblesk.server.service.MicropaymentService;
import com.coinblesk.server.service.WalletService;
import com.coinblesk.server.utilTest.CoinbleskTest;
import com.coinblesk.server.utilTest.FakeTxBuilder;
import com.coinblesk.server.utilTest.PaymentChannel;
import com.coinblesk.util.DTOUtils;
import org.bitcoinj.core.*;
import org.bitcoinj.core.Transaction.SigHash;
import org.bitcoinj.crypto.TransactionSignature;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;
import org.bitcoinj.script.ScriptOpCodes;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/***
 * @author Sebastian Stephan
 */
public class MicroPaymentTest extends CoinbleskTest {
	public static final String URL_MICRO_PAYMENT = "/payment/micropayment";
	private static final int LOW_FEE = 50;
	private static Coin oneUSD;
	private static Coin channelThreshold;
	private static final long validLockTime = Instant.now().plus(Duration.ofDays(30)).getEpochSecond();
	private static MockMvc mockMvc;
	@Autowired
	private WebApplicationContext webAppContext;
	@Autowired
	private WalletService walletService;
	@Autowired
	private MicropaymentService micropaymentService;
	@Autowired
	private AccountService accountService;
	@Autowired
	private ForexService forexService;
	@Autowired
	private AccountRepository accountRepository;
	@Autowired
	private TimeLockedAddressRepository timeLockedAddressRepository;
	@Autowired
	private AppConfig appConfig;

	public static NetworkParameters params;

	private static long validNonce()  {
		return Instant.now().toEpochMilli();
	}

	@Before
	public void setUp() throws Exception {
		mockMvc = MockMvcBuilders.webAppContextSetup(webAppContext).build();
		params = appConfig.getNetworkParameters();
		oneUSD = Coin.valueOf(BigDecimal.valueOf(100000000).divide(forexService.getExchangeRate("BTC", "USD"), BigDecimal.ROUND_UP).longValue());
		channelThreshold = oneUSD.multiply(appConfig.getMaximumChannelAmountUSD());
	}

	@Test
	public void microPayment_returns200OK() throws Exception{
		final ECKey senderKey = new ECKey();
		final ECKey serverPublicKey = accountService.createAccount(senderKey);
		final ECKey receiverKey = new ECKey();
		accountService.createAccount(receiverKey);

		TimeLockedAddress tla = accountService.createTimeLockedAddress(senderKey, validLockTime).getTimeLockedAddress();
		Transaction fundingTx = FakeTxBuilder.createFakeTxWithoutChangeAddress(params, tla.getAddress(params));
		watchAndMineTransactions(fundingTx);

		PaymentChannel channel = new PaymentChannel(params, tla.getAddress(params), senderKey, serverPublicKey)
			.addInputs(tla, fundingTx.getOutput(0))
			.addToServerOutput(Coin.valueOf(200));

		sendAndExpect2xxSuccess(buildRequestDTO(senderKey, receiverKey, channel.buildTx(), 200L, validNonce()));
	}

	@Test
	public void microPayment_failsOnNoTransaction() throws Exception {
		ECKey clientKey = new ECKey();
		MicroPaymentRequestDTO microPaymentRequestDTO = new MicroPaymentRequestDTO("", new ECKey().getPublicKeyAsHex()
			, new ECKey().getPublicKeyAsHex(), 10L, 0L);
		SignedDTO dto = DTOUtils.serializeAndSign(microPaymentRequestDTO, clientKey);
		sendAndExpect4xxError(dto, "");
	}

	@Test
	public void microPayment_failsOnEmptyTransaction() throws Exception {
		ECKey clientKey = new ECKey();
		Transaction microPaymentTransaction = new Transaction(params);
		SignedDTO dto = buildRequestDTO(clientKey, new ECKey(), microPaymentTransaction, 1337L);
		sendAndExpect4xxError(dto, "Transaction had no inputs or no outputs");
	}

	@Test
	public void microPayment_failsOnUnknownUTXOs() throws Exception {
		final ECKey senderKey = new ECKey();
		final ECKey serverPublicKey = accountService.createAccount(senderKey);
		final ECKey receiverKey = new ECKey();
		accountService.createAccount(receiverKey);

		TimeLockedAddress tla = accountService.createTimeLockedAddress(senderKey, validLockTime).getTimeLockedAddress();
		Transaction fundingTx = FakeTxBuilder.createFakeTxWithoutChangeAddress(params, tla.getAddress(params));
		// Funding tx is not mined or broadcasted

		PaymentChannel channel = new PaymentChannel(params, tla.getAddress(params), senderKey, serverPublicKey)
			.addInputs(tla, fundingTx.getOutput(0))
			.addToServerOutput(Coin.valueOf(200));

		SignedDTO dto = buildRequestDTO(senderKey, receiverKey, channel.buildTx(), 200L, validNonce());
		sendAndExpect4xxError(dto, "Transaction spends unknown UTXOs");
	}

	@Test
	public void microPayment_failsOnWrongAddressType() throws Exception {
		final ECKey senderKey = new ECKey();
		final ECKey serverKey = accountService.createAccount(senderKey);

		final ECKey receiverKey = new ECKey();
		accountService.createAccount(receiverKey);

		TimeLockedAddress tla = accountService.createTimeLockedAddress(senderKey, validLockTime)
			.getTimeLockedAddress();
		walletService.addWatching(tla.getAddress(params));

		Transaction goodInput = FakeTxBuilder.createFakeTxWithoutChangeAddress(params, tla.getAddress(params));
		// Bad input is a pay 2 public key utxo from sender, that the server for some reason watches.
		Transaction badInput = FakeTxBuilder.createFakeTxWithoutChangeAddress(params, senderKey.toAddress(params));
		watchAndMineTransactions(goodInput, badInput);

		Transaction tx = new Transaction(params);
		tx.addOutput(P2PKOutput(tx, serverKey, 500L, params));
		tx.addInput(goodInput.getOutput(0));
		tx.addInput(badInput.getOutput(0));
		signInput(tx, 0, tla.createRedeemScript(), senderKey);
		TransactionSignature sig = tx.calculateSignature(1, senderKey, badInput.getOutput(0).getScriptPubKey(), SigHash.ALL, false);
		tx.getInput(1).setScriptSig(new ScriptBuilder().data(sig.encodeToBitcoin()).build());

		SignedDTO dto = buildRequestDTO(senderKey, receiverKey, tx, 500L);
		sendAndExpect4xxError(dto, "Transaction must spent P2SH addresses");
	}

	@Test
	public void microPayment_failsOnUnknownTLAInputs() throws Exception {
		final ECKey senderKey = new ECKey();
		final ECKey serverKey = accountService.createAccount(senderKey);
		final ECKey receiverKey = new ECKey();
		accountService.createAccount(receiverKey);

		// Create a tla but without creating it on the server
		TimeLockedAddress tla = new TimeLockedAddress(senderKey.getPubKey(), serverKey.getPubKey(), validLockTime);
		Transaction fundingTx = FakeTxBuilder.createFakeTxWithoutChangeAddress(params, tla.getAddress(params));
		watchAndMineTransactions(fundingTx);

		PaymentChannel channel = new PaymentChannel(params, tla.getAddress(params), senderKey, serverKey)
			.addInputs(tla, fundingTx.getOutput(0)).addToServerOutput(1337L);
		SignedDTO dto = buildRequestDTO(senderKey, receiverKey, channel.buildTx(), 1337L);
		sendAndExpect4xxError(dto, "Used TLA inputs are not known to server");
	}

	@Test
	public void microPayment_failsOnSpentUTXOs() throws Exception {
		final ECKey senderKey = new ECKey();
		final ECKey serverKey = accountService.createAccount(senderKey);
		final ECKey receiverKey = new ECKey();
		accountService.createAccount(receiverKey);

		TimeLockedAddress tla = accountService.createTimeLockedAddress(senderKey, validLockTime).getTimeLockedAddress();
		Transaction fundingTx = FakeTxBuilder.createFakeTxWithoutChangeAddress(params, tla.getAddress(params));
		watchAndMineTransactions(fundingTx);

		// Spend the funding tx
		Account accountSender = accountService.getByClientPublicKey(senderKey.getPubKey());
		final ECKey serverPrivateKey = ECKey.fromPrivateAndPrecalculatedPublic(accountSender.serverPrivateKey(),
			accountSender.serverPublicKey());
		Transaction spendingTx = new Transaction(params);
		spendingTx.addInput(fundingTx.getOutput(0));
		spendingTx.addOutput(anyP2PKOutput(spendingTx));
		TransactionSignature clientSig = spendingTx.calculateSignature(0, senderKey, tla.createRedeemScript(),
			SigHash.ALL, false);
		TransactionSignature serverSig = spendingTx.calculateSignature(0, serverPrivateKey, tla.createRedeemScript(),
			SigHash.ALL, false);
		Script scriptSig = tla.createScriptSigBeforeLockTime(clientSig, serverSig);
		spendingTx.getInput(0).setScriptSig(scriptSig);
		walletService.getWallet().maybeCommitTx(spendingTx);

		PaymentChannel channel = new PaymentChannel(params, tla.getAddress(params), senderKey, serverKey)
			.addInputs(tla, fundingTx.getOutput(0)).addToServerOutput(1337L);
		SignedDTO dto = buildRequestDTO(senderKey, receiverKey, channel.buildTx(), 1337L);
		sendAndExpect4xxError(dto, "Input is already spent");
	}

	@Test
	public void microPayment_failsOnNonMinedUTXOs() throws Exception {
		final ECKey senderKey = new ECKey();
		final ECKey serverPublicKey = accountService.createAccount(senderKey);
		final ECKey receiverKey = new ECKey();
		accountService.createAccount(receiverKey);

		TimeLockedAddress tla = accountService.createTimeLockedAddress(senderKey, validLockTime).getTimeLockedAddress();
		Transaction fundingTx = FakeTxBuilder.createFakeTxWithoutChangeAddress(params, tla.getAddress(params));
		watchAllOutputs(fundingTx);
		walletService.getWallet().maybeCommitTx(fundingTx);

		PaymentChannel channel = new PaymentChannel(params, tla.getAddress(params), senderKey, serverPublicKey)
			.addInputs(tla, fundingTx.getOutput(0))
			.addToServerOutput(Coin.valueOf(200));
		SignedDTO dto = buildRequestDTO(senderKey, receiverKey, channel.buildTx(), 1337L);
		sendAndExpect4xxError(dto, "UTXO must be mined");
	}

	@Test
	public void microPayment_failsOnSoonLockedInputs() throws Exception {
		final ECKey senderKey = new ECKey();
		final ECKey serverKey = accountService.createAccount(senderKey);
		final ECKey receiverKey = new ECKey();
		accountService.createAccount(receiverKey);

		// Simulate an address that was created some time ago
		final long lockTime = Instant.now().plus(Duration.ofSeconds(appConfig.getMinimumLockTimeSeconds()-1))
			.getEpochSecond();
		TimeLockedAddress tla = new TimeLockedAddress(senderKey.getPubKey(), serverKey.getPubKey(), lockTime);
		TimeLockedAddressEntity tlaEntity = new TimeLockedAddressEntity();
		Account senderAccount = accountService.getByClientPublicKey(senderKey.getPubKey());
		tlaEntity.setAccount(senderAccount);
		tlaEntity.setTimeCreated(Instant.now().minusSeconds(10000).getEpochSecond());
		tlaEntity.setAddressHash(tla.getAddressHash());
		tlaEntity.setLockTime(lockTime);
		tlaEntity.setRedeemScript(tla.createRedeemScript().getProgram());
		timeLockedAddressRepository.save(tlaEntity);
		walletService.addWatching(tla.getAddress(params));

		Transaction fundingTx = FakeTxBuilder.createFakeTxWithoutChangeAddress(params, tla.getAddress(params));
		mineTransaction(fundingTx);

		PaymentChannel channel = new PaymentChannel(params, tla.getAddress(params), senderKey, serverKey)
			.addInputs(tla, fundingTx.getOutput(0)).addToServerOutput(1337L);
		SignedDTO dto = buildRequestDTO(senderKey, receiverKey, channel.buildTx(), 1337L);
		sendAndExpect4xxError(dto, "Inputs must be locked at least until");
	}

	@Test
	public void microPayment_failsOnInputsBelongingToMultipleAccounts() throws Exception {
		final ECKey clientKey1 = new ECKey();
		final ECKey clientKey2 = new ECKey();
		accountService.createAccount(clientKey1);
		accountService.createAccount(clientKey2);

		TimeLockedAddress tla1 = accountService.createTimeLockedAddress(clientKey1, validLockTime).getTimeLockedAddress();
		TimeLockedAddress tla2 = accountService.createTimeLockedAddress(clientKey2, validLockTime).getTimeLockedAddress();

		// Fund both addresses
		Transaction fundingTx1 = FakeTxBuilder.createFakeTxWithoutChangeAddress(params, tla1.getAddress (params));
		Transaction fundingTx2 = FakeTxBuilder.createFakeTxWithoutChangeAddress(params, tla2.getAddress (params));
		watchAndMineTransactions(fundingTx1, fundingTx2);

		// Use them as inputs
		Transaction microPaymentTransaction = new Transaction(params);
		microPaymentTransaction.addInput(fundingTx1.getOutput(0));
		microPaymentTransaction.addInput(fundingTx2.getOutput(0));
		microPaymentTransaction.addOutput(anyP2PKOutput(microPaymentTransaction));
		signInput(microPaymentTransaction, 0, tla1.createRedeemScript(), clientKey1);
		signInput(microPaymentTransaction, 1, tla2.createRedeemScript(), clientKey2);

		SignedDTO dto = createExternalPaymentRequestDTO(clientKey1, microPaymentTransaction);
		sendAndExpect4xxError(dto, "Inputs must be from sender account");
	}

	@Test
	public void microPayment_failsOnSignedByWrongAccount() throws Exception {
		final ECKey senderKey = new ECKey();
		final ECKey serverKey = accountService.createAccount(senderKey);
		final ECKey receiverKey = new ECKey();
		accountService.createAccount(receiverKey);

		TimeLockedAddress tla = accountService.createTimeLockedAddress(senderKey, validLockTime).getTimeLockedAddress();
		Transaction fundingTx = FakeTxBuilder.createFakeTxWithoutChangeAddress(params, tla.getAddress(params));
		watchAndMineTransactions(fundingTx);

		PaymentChannel channel = new PaymentChannel(params, tla.getAddress(params), senderKey, serverKey)
			.addInputs(tla, fundingTx.getOutput(0)).addToServerOutput(1337L);
		MicroPaymentRequestDTO microPaymentRequestDTO = new MicroPaymentRequestDTO(
			DTOUtils.toHex(channel.buildTx().bitcoinSerialize()), senderKey.getPublicKeyAsHex(),
			receiverKey.getPublicKeyAsHex(), 1337L, validNonce());
		SignedDTO dto =  DTOUtils.serializeAndSign(microPaymentRequestDTO, new ECKey());
		sendAndExpect4xxError(dto, "Signature is not valid");
	}

	@Test
	public void microPayment_failsOnNoOutputForServer() throws Exception {
		final ECKey senderKey = new ECKey();
		final ECKey serverKey = accountService.createAccount(senderKey);
		final ECKey receiverKey = new ECKey();
		accountService.createAccount(receiverKey);

		TimeLockedAddress tla = accountService.createTimeLockedAddress(senderKey, validLockTime).getTimeLockedAddress();
		Transaction fundingTx = FakeTxBuilder.createFakeTxWithoutChangeAddress(params, tla.getAddress(params));
		watchAndMineTransactions(fundingTx);

		PaymentChannel channel = new PaymentChannel(params, tla.getAddress(params), senderKey, serverKey)
			.addInputs(tla, fundingTx.getOutput(0));
		SignedDTO dto = buildRequestDTO(senderKey, receiverKey, channel.buildTx(), 1337L);

		sendAndExpect4xxError(dto, "Invalid amount. 1337 requested but 0 given");
	}

	@Test
	public void microPayment_failsOnUnknownReceiver() throws Exception {
		final ECKey senderKey = new ECKey();
		final ECKey serverKey = accountService.createAccount(senderKey);
		final ECKey receiverKey = new ECKey();

		TimeLockedAddress tla = accountService.createTimeLockedAddress(senderKey, validLockTime).getTimeLockedAddress();
		Transaction fundingTx = FakeTxBuilder.createFakeTxWithoutChangeAddress(params, tla.getAddress(params));
		watchAndMineTransactions(fundingTx);

		PaymentChannel channel = new PaymentChannel(params, tla.getAddress(params), senderKey, serverKey)
			.addInputs(tla, fundingTx.getOutput(0)).addToServerOutput(1337L);
		SignedDTO dto = buildRequestDTO(senderKey, receiverKey, channel.buildTx(), 1337L);
		sendAndExpect4xxError(dto, "Receiver is unknown to server");
	}

	@Test
	public void microPayment_failsOnUnknownSender() throws Exception {
		final ECKey senderKey = new ECKey();
		final ECKey receiverKey = new ECKey();
		accountService.createAccount(receiverKey);

		TimeLockedAddress tla = new TimeLockedAddress(senderKey.getPubKey(), new ECKey().getPubKey(), validLockTime);
		Transaction fundingTx = FakeTxBuilder.createFakeTxWithoutChangeAddress(params, tla.getAddress(params));
		watchAndMineTransactions(fundingTx);

		PaymentChannel channel = new PaymentChannel(params, tla.getAddress(params), senderKey, new ECKey())
			.addInputs(tla, fundingTx.getOutput(0)).addToServerOutput(1337L);
		SignedDTO dto = buildRequestDTO(senderKey, receiverKey, channel.buildTx(), 1337L);
		sendAndExpect4xxError(dto, "Unknown sender");
	}

	@Test
	public void microPayment_failsSendingToOneself() throws Exception {
		final ECKey senderKey = new ECKey();
		final ECKey serverKey = accountService.createAccount(senderKey);

		TimeLockedAddress tla = accountService.createTimeLockedAddress(senderKey, validLockTime).getTimeLockedAddress();
		Transaction fundingTx = FakeTxBuilder.createFakeTxWithoutChangeAddress(params, tla.getAddress(params));
		watchAndMineTransactions(fundingTx);

		PaymentChannel channel = new PaymentChannel(params, tla.getAddress(params), senderKey, serverKey)
			.addInputs(tla, fundingTx.getOutput(0)).addToServerOutput(1337L);
		SignedDTO dto = buildRequestDTO(senderKey, senderKey, channel.buildTx(), 1337L);
		sendAndExpect4xxError(dto, "Sender and receiver must be different");
	}

	@Test
	public void microPayment_closesChannelWhenSendingToThirdParty() throws Exception {
		final ECKey senderKey = new ECKey();
		accountService.createAccount(senderKey);

		assertThat(accountService.getByClientPublicKey(senderKey.getPubKey()).isLocked(), is(false));

		TimeLockedAddress tla = accountService.createTimeLockedAddress(senderKey, validLockTime).getTimeLockedAddress();
		Transaction fundingTx = FakeTxBuilder.createFakeTxWithoutChangeAddress(params, tla.getAddress(params));
		watchAndMineTransactions(fundingTx);

		PaymentChannel channel = new PaymentChannel(params, tla.getAddress(params), senderKey, null)
			.addInputs(tla, fundingTx.getOutput(0)).addOutput(new ECKey().toAddress(params), Coin.valueOf(100L));
		SignedDTO dto = createExternalPaymentRequestDTO(senderKey, channel.buildTx(), validNonce());
		sendAndExpect2xxSuccess(dto);

		assertThat(accountService.getByClientPublicKey(senderKey.getPubKey()).isLocked(), is(true));
	}

	@Test
	public void microPayment_failsWhenChannelIsLocked() throws Exception {
		final ECKey senderKey = new ECKey();
		final ECKey serverKey = accountService.createAccount(senderKey);
		final ECKey receiverKey = new ECKey();
		accountService.createAccount(receiverKey);

		TimeLockedAddress tla = accountService.createTimeLockedAddress(senderKey, validLockTime).getTimeLockedAddress();
		Transaction fundingTx = FakeTxBuilder.createFakeTxWithoutChangeAddress(params, tla.getAddress(params));
		watchAndMineTransactions(fundingTx);

		accountRepository.save(accountRepository.findByClientPublicKey(senderKey.getPubKey()).locked(true));

		PaymentChannel channel = new PaymentChannel(params, tla.getAddress(params), senderKey, serverKey)
			.addInputs(tla, fundingTx.getOutput(0)).addToServerOutput(1337L);
		SignedDTO dto = buildRequestDTO(senderKey, receiverKey, channel.buildTx(), 1337L);
		sendAndExpect4xxError(dto, "Channel is locked");
	}

	@Test
	public void microPayment_failsOnInvalidNonce() throws Exception {
		final ECKey senderKey = new ECKey();
		final ECKey serverKey = accountService.createAccount(senderKey);
		final ECKey receiverKey = new ECKey();
		accountService.createAccount(receiverKey);

		TimeLockedAddress tla = accountService.createTimeLockedAddress(senderKey, validLockTime) .getTimeLockedAddress();
		Transaction fundingTx = FakeTxBuilder.createFakeTxWithoutChangeAddress(params, tla.getAddress (params));
		watchAndMineTransactions(fundingTx);

		PaymentChannel channel = new PaymentChannel(params, tla.getAddress(params), senderKey, serverKey)
			.addInputs(tla, fundingTx.getOutput(0)).addToServerOutput(1337L);
		long nonce = validNonce();
		SignedDTO dto = buildRequestDTO(senderKey, receiverKey, channel.buildTx(), 1337L, nonce);
		sendAndExpect2xxSuccess(dto);

		channel.addToServerOutput(42L);
		dto = buildRequestDTO(senderKey, receiverKey, channel.buildTx(), 1337L, nonce);
		sendAndExpect4xxError(dto, "Invalid nonce");
		dto = buildRequestDTO(senderKey, receiverKey, channel.buildTx(), 1337L, nonce - 1);
		sendAndExpect4xxError(dto, "Invalid nonce");
	}

	@Test
	public void microPayment_failsWhenInputAmountIsTooSmall() throws Exception {
		final ECKey senderKey = new ECKey();
		final ECKey serverKey = accountService.createAccount(senderKey);
		final ECKey receiverKey = new ECKey();
		accountService.createAccount(receiverKey);

		TimeLockedAddress tla = accountService.createTimeLockedAddress(senderKey, validLockTime) .getTimeLockedAddress();
		Transaction fundingTx = FakeTxBuilder.createFakeTxWithoutChangeAddress(params, tla.getAddress (params));
		watchAndMineTransactions(fundingTx);

		// Try to send more than the inputs are worth
		Transaction tx = createChannelTx(fundingTx.getOutput(0), senderKey, serverKey,
			tla, Coin.COIN.plus(Coin.SATOSHI).getValue());
		SignedDTO dto = buildRequestDTO(senderKey, receiverKey, tx, 42L);
		sendAndExpect4xxError(dto, "Transaction output negative");
	}

	@Test
	public void microPayment_failsWithWrongAmountOnOpenChannel() throws Exception {
		final ECKey senderKey = new ECKey();
		final ECKey serverKey = accountService.createAccount(senderKey);
		final ECKey receiverKey = new ECKey();
		accountService.createAccount(receiverKey);

		TimeLockedAddress tla = accountService.createTimeLockedAddress(senderKey, validLockTime) .getTimeLockedAddress();
		Transaction fundingTx = FakeTxBuilder.createFakeTxWithoutChangeAddress(params, tla.getAddress (params));
		watchAndMineTransactions(fundingTx);

		PaymentChannel channel = new PaymentChannel(params, tla.getAddress(params), senderKey, serverKey)
			.addInputs(tla, fundingTx.getOutput(0));

		// Try to send 42 satoshis, but actally give only 41
		SignedDTO dto = buildRequestDTO(senderKey, receiverKey, channel.setServerOutput(41L).buildTx(), 42L);
		sendAndExpect4xxError(dto, "Invalid amount. 42 requested but 41 given");

		// Send 100 satoshis successfully
		sendAndExpect2xxSuccess(buildRequestDTO(senderKey, receiverKey, channel.setServerOutput(100L).buildTx(), 100L));

		// Try to send 50 satoshis, but don't increase the previous channel amount
		dto = buildRequestDTO(senderKey, receiverKey, channel.buildTx(), 50L);
		sendAndExpect4xxError(dto, "Invalid amount. 50 requested but 0 given");

		// Try to send 100 satoshis, but don't include the previous open channel amount
		// (sender forgets that he has an open channel)
		dto = buildRequestDTO(senderKey, receiverKey, channel.setServerOutput(50L).buildTx(), 50L);
		sendAndExpect4xxError(dto, "Amount to server must more than in open channel");

		// Try to send 50 satoshis, but actually give 100 to server.
		// (this protects the client)
		dto = buildRequestDTO(senderKey, receiverKey, channel.setServerOutput(200L).buildTx(), 50L);
		sendAndExpect4xxError(dto, "Invalid amount. 50 requested but 100 given");

		// Try to send negative amount
		dto = buildRequestDTO(senderKey, receiverKey, channel.setServerOutput(100L).buildTx(), -50L);
		sendAndExpect4xxError(dto, "Can't send zero or negative amont");

		// Try to send zero amount
		dto = buildRequestDTO(senderKey, receiverKey, channel.buildTx(), 0L);
		sendAndExpect4xxError(dto, "Can't send zero or negative amont");
	}

	@Test
	public void microPayment_failsWithInsufficientFee() throws Exception {
		final ECKey senderKey = new ECKey();
		final ECKey serverKey = accountService.createAccount(senderKey);
		final ECKey receiverKey = new ECKey();
		accountService.createAccount(receiverKey);

		TimeLockedAddress tla = accountService.createTimeLockedAddress(senderKey, validLockTime).getTimeLockedAddress();
		Transaction fundingTx = FakeTxBuilder.createFakeTxWithoutChangeAddress(params, tla.getAddress (params));
		watchAndMineTransactions(fundingTx);

		Transaction badTx = new PaymentChannel(params, tla.getAddress(params), senderKey, serverKey)
			.addInputs(tla, fundingTx.getOutput(0)).addToServerOutput(oneUSD).setFee(LOW_FEE).buildTx();
		SignedDTO dto = buildRequestDTO(senderKey, receiverKey, badTx, oneUSD.getValue());
		sendAndExpect4xxError(dto, "Insufficient transaction fee");
	}

	@Test
	public void microPayment_failsWhenSendingOverThreshold() throws Exception {
		final ECKey senderKey = new ECKey();
		final ECKey serverKey = accountService.createAccount(senderKey);
		final ECKey receiverKey = new ECKey();
		accountService.createAccount(receiverKey);

		TimeLockedAddress tla = accountService.createTimeLockedAddress(senderKey, validLockTime)
			.getTimeLockedAddress();
		Transaction fundingTx = FakeTxBuilder.createFakeTxWithoutChangeAddress(params, tla.getAddress(params));
		walletService.addWatching(tla.getAddress(params));
		mineTransaction(fundingTx);

		// Directly sending more than threshold fails
		Coin tooMuch = channelThreshold.plus(oneUSD);
		Transaction badTx = new PaymentChannel(params, tla.getAddress(params), senderKey, serverKey)
			.addInputs(tla, fundingTx.getOutput(0)).addToServerOutput(tooMuch).buildTx();
		SignedDTO dto = buildRequestDTO(senderKey, receiverKey, badTx, tooMuch.getValue());
		sendAndExpect4xxError(dto, "Maximum channel value reached");

		// Sending small amount that brings channel over limit fails
		Coin almostFull = channelThreshold.minus(oneUSD);
		PaymentChannel channel = new PaymentChannel(params, tla.getAddress(params), senderKey, serverKey)
			.addInputs(tla, fundingTx.getOutput(0)).addToServerOutput(almostFull);
		SignedDTO dto2 = buildRequestDTO(senderKey, receiverKey, channel.buildTx(), almostFull.getValue());
		sendAndExpect2xxSuccess(dto2); // channel is now one dollar below full
		channel.addToServerOutput(oneUSD.multiply(2));
		SignedDTO dto3 = buildRequestDTO(senderKey, receiverKey, channel.buildTx(), oneUSD.multiply(2).getValue());
		sendAndExpect4xxError(dto3, "Maximum channel value reached");
	}

	@Test
	public void microPayment_sendsAmountToReceiver() throws Exception {
		final ECKey senderKey = new ECKey();
		final ECKey serverKey = accountService.createAccount(senderKey);
		final ECKey receiverKey = new ECKey();
		accountService.createAccount(receiverKey);

		TimeLockedAddress tla = accountService.createTimeLockedAddress(senderKey, validLockTime)
			.getTimeLockedAddress();
		Transaction fundingTx = FakeTxBuilder.createFakeTxWithoutChangeAddress(params, tla.getAddress(params));
		walletService.addWatching(tla.getAddress(params));
		mineTransaction(fundingTx);

		PaymentChannel channel = new PaymentChannel(params, tla.getAddress(params), senderKey, serverKey)
			.addInputs(tla, fundingTx.getOutput(0)).addToServerOutput(Coin.valueOf(1337));
		SignedDTO dto = buildRequestDTO(senderKey, receiverKey, channel.buildTx(), 1337L);
		sendAndExpect2xxSuccess(dto);
		assertThat(accountService.getVirtualBalanceByClientPublicKey(receiverKey.getPubKey()).getBalance(), is(1337L));
	}

	@Test
	public void microPayment_savesBroadcastableTransaction() throws Exception {
		final ECKey senderKey = new ECKey();
		final ECKey serverKey = accountService.createAccount(senderKey);
		final ECKey receiverKey = new ECKey();
		accountService.createAccount(receiverKey);

		TimeLockedAddress tla = accountService.createTimeLockedAddress(senderKey, validLockTime)
			.getTimeLockedAddress();
		Transaction fundingTx = FakeTxBuilder.createFakeTxWithoutChangeAddress(params, tla.getAddress(params));
		walletService.addWatching(tla.getAddress(params));
		mineTransaction(fundingTx);

		PaymentChannel channel = new PaymentChannel(params, tla.getAddress(params), senderKey, serverKey)
			.addInputs(tla, fundingTx.getOutput(0)).addToServerOutput(Coin.valueOf(1337));
		SignedDTO dto = buildRequestDTO(senderKey, receiverKey, channel.buildTx(), 1337L);
		sendAndExpect2xxSuccess(dto);

		Transaction openChannelTx = new Transaction(params, accountService.getByClientPublicKey(senderKey.getPubKey
			()).getChannelTransaction());
		openChannelTx.verify();
		walletService.getWallet().maybeCommitTx(openChannelTx);
	}

	@Test
	public void microPayment_replayAttackFails() throws Exception {
		final ECKey senderKey = new ECKey();
		final ECKey serverKey = accountService.createAccount(senderKey);
		final ECKey receiverKey = new ECKey();
		accountService.createAccount(receiverKey);

		TimeLockedAddress tla = accountService.createTimeLockedAddress(senderKey, validLockTime)
			.getTimeLockedAddress();
		Transaction fundingTx = FakeTxBuilder.createFakeTxWithoutChangeAddress(params, tla.getAddress(params));
		walletService.addWatching(tla.getAddress(params));
		mineTransaction(fundingTx);

		PaymentChannel channel = new PaymentChannel(params, tla.getAddress(params), senderKey, serverKey)
			.addInputs(tla, fundingTx.getOutput(0)) .addToServerOutput(Coin.valueOf(1337));
		SignedDTO dto = buildRequestDTO(senderKey, receiverKey, channel.buildTx(), 1337L);
		sendAndExpect2xxSuccess(dto);

		assertThat(accountService.getByClientPublicKey(receiverKey.getPubKey()).virtualBalance(), is(1337L));

		// Send again unmodified fails because nonce is the same
		sendAndExpect4xxError(dto, "Invalid nonce");

		// Increasing nonce fails because now the signature is wrong
		MicroPaymentRequestDTO orig = DTOUtils.fromJSON(DTOUtils.fromBase64(dto.getPayload()),
			MicroPaymentRequestDTO.class);
		MicroPaymentRequestDTO modifiedRequest = new MicroPaymentRequestDTO(orig.getTx(), orig.getFromPublicKey(),
			orig.getToPublicKey(), orig.getAmount(), validNonce());
		sendAndExpect4xxError(new SignedDTO(DTOUtils.toBase64(DTOUtils.toJSON(modifiedRequest)), dto.getSignature()),
			"Signature is not valid");

		// Signing again with some other key (that of the receiver) fails as well
		sendAndExpect4xxError(DTOUtils.serializeAndSign(modifiedRequest, receiverKey), "Signature is not valid");

		// Change sender to some other account's public key (that we own) in order to 'trick' system
		ECKey otherOwnedAccount = new ECKey();
		accountService.createAccount(otherOwnedAccount);
		MicroPaymentRequestDTO modifiedRequest2 = new MicroPaymentRequestDTO(orig.getTx(), otherOwnedAccount
			.getPublicKeyAsHex(), orig.getToPublicKey(), orig.getAmount(), validNonce());
		sendAndExpect4xxError(DTOUtils.serializeAndSign(modifiedRequest2, otherOwnedAccount),
			"Inputs must be from sender account");

		// Amount stayed the same during all failed attacks
		assertThat(accountService.getByClientPublicKey(receiverKey.getPubKey()).virtualBalance(), is(1337L));

	}

	@Test
	public void microPayment_increasesServerPotWhenMined() throws Exception {
		final ECKey senderKey = new ECKey();
		final ECKey serverKey = accountService.createAccount(senderKey);
		final ECKey receiverKey = new ECKey();
		accountService.createAccount(receiverKey);
		walletService.addWatching(serverKey.toAddress(params));

		assertThat(micropaymentService.getMicroPaymentPotValue(), is(Coin.ZERO));

		TimeLockedAddress tla = accountService.createTimeLockedAddress(senderKey, validLockTime)
			.getTimeLockedAddress();
		walletService.addWatching(tla.getAddress(params));
		Transaction fundingTx = FakeTxBuilder.createFakeTxWithoutChangeAddress(params, tla.getAddress(params));
		mineTransaction(fundingTx);

		Transaction channelTx = new PaymentChannel(params, tla.getAddress(params), senderKey, serverKey)
			.addInputs(tla, fundingTx.getOutput(0)).addToServerOutput(Coin.valueOf(1337)).buildTx();

		SignedDTO dto = buildRequestDTO(senderKey, receiverKey, channelTx, 1337L);
		sendAndExpect2xxSuccess(dto);

		// Manually mine
		Transaction openChannelTx = new Transaction(params, accountService.getByClientPublicKey(senderKey.getPubKey
			()).getChannelTransaction());
		mineTransaction(openChannelTx);

		assertThat(micropaymentService.getMicroPaymentPotValue(), is(Coin.valueOf((1337))));
	}

	@Test
	public void microPayment_failsOnNotSignedInputs() throws Exception {
		final ECKey senderKey = new ECKey();
		final ECKey serverPublicKey = accountService.createAccount(senderKey);
		final ECKey receiverKey = new ECKey();
		accountService.createAccount(receiverKey);

		TimeLockedAddress tla = accountService.createTimeLockedAddress(senderKey, validLockTime).getTimeLockedAddress();

		Transaction fundingTx = FakeTxBuilder.createFakeTxWithoutChangeAddress(params, tla.getAddress(params));
		watchAndMineTransactions(fundingTx);

		Transaction tx = new PaymentChannel(params, tla.getAddress(params), senderKey, serverPublicKey)
			.addInputs(tla, fundingTx.getOutput(0))
			.addToServerOutput(Coin.valueOf(200))
			.buildTx();
		tx.getInput(0).clearScriptBytes();

		SignedDTO dto = buildRequestDTO(senderKey, receiverKey, tx, 200L, validNonce());
		sendAndExpect4xxError(dto, "Input was not signed");
	}

	@Test
	public void microPayment_failsOnWrongSignatureFormat() throws Exception {
		final ECKey senderKey = new ECKey();
		final ECKey serverPublicKey = accountService.createAccount(senderKey);
		final ECKey receiverKey = new ECKey();
		accountService.createAccount(receiverKey);

		TimeLockedAddress tla = accountService.createTimeLockedAddress(senderKey, validLockTime).getTimeLockedAddress();

		Transaction fundingTx = FakeTxBuilder.createFakeTxWithoutChangeAddress(params, tla.getAddress(params));
		watchAndMineTransactions(fundingTx);

		Transaction tx = new PaymentChannel(params, tla.getAddress(params), senderKey, serverPublicKey)
			.addInputs(tla, fundingTx.getOutput(0))
			.addToServerOutput(Coin.valueOf(200))
			.buildTx();

		tx.getInput(0).setScriptSig(new ScriptBuilder()
			.data(new byte[10])
			.op(ScriptOpCodes.OP_DUP).build());
		SignedDTO dto = buildRequestDTO(senderKey, receiverKey, tx, 200L, validNonce());
		sendAndExpect4xxError(dto, "Signature for input had wrong format");

		tx.getInput(0).setScriptSig(new ScriptBuilder()
			.data(new byte[72]).data(new byte[72]).build());
		dto = buildRequestDTO(senderKey, receiverKey, tx, 200L, validNonce());
		sendAndExpect4xxError(dto, "Signature for input had wrong format");
	}

	@Test
	public void microPayment_setsCorrectBroadcastBefore() throws Exception {
		final ECKey senderKey = new ECKey();
		final ECKey serverKey = accountService.createAccount(senderKey);
		final ECKey receiverKey = new ECKey();
		accountService.createAccount(receiverKey);

		// Use of three different TLAs. Second one is earliest and should be used by server for broadcast
		TimeLockedAddress tla1 = accountService.createTimeLockedAddress(senderKey, Instant.now().plus(Duration.ofDays(10)).getEpochSecond())
			.getTimeLockedAddress();
		TimeLockedAddress tla2 = accountService.createTimeLockedAddress(senderKey, Instant.now().plus(Duration.ofDays(9)).getEpochSecond())
			.getTimeLockedAddress();
		TimeLockedAddress tla3 = accountService.createTimeLockedAddress(senderKey, Instant.now().plus(Duration.ofDays(11)).getEpochSecond())
			.getTimeLockedAddress();

		Transaction fundingTx1 = FakeTxBuilder.createFakeTxWithoutChangeAddress(params, tla1.getAddress(params));
		Transaction fundingTx2 = FakeTxBuilder.createFakeTxWithoutChangeAddress(params, tla2.getAddress(params));
		Transaction fundingTx3 = FakeTxBuilder.createFakeTxWithoutChangeAddress(params, tla3.getAddress(params));
		walletService.addWatching(tla1.getAddress(params));
		walletService.addWatching(tla2.getAddress(params));
		walletService.addWatching(tla3.getAddress(params));
		mineTransaction(fundingTx1);
		mineTransaction(fundingTx2);
		mineTransaction(fundingTx3);

		PaymentChannel channel = new PaymentChannel(params, tla1.getAddress(params), senderKey, serverKey);
		channel.addInputs(tla1, fundingTx1.getOutput(0));
		channel.addInputs(tla2, fundingTx2.getOutput(0));
		channel.addInputs(tla3, fundingTx3.getOutput(0));
		channel.addToServerOutput(Coin.valueOf(500L));

		SignedDTO dto = buildRequestDTO(senderKey, receiverKey, channel.buildTx(), 500L);
		sendAndExpect2xxSuccess(dto);

		long savedBroadcastBefore = accountRepository.findByClientPublicKey(senderKey.getPubKey()).getBroadcastBefore();
		assertThat(savedBroadcastBefore, is(tla2.getLockTime()));
	}

	private Transaction createChannelTx(TransactionOutput input, ECKey senderKey, ECKey serverPK, TimeLockedAddress
		changeAddress, long amountToServer) {
		return createChannelTx(input.getValue().getValue()
			- amountToServer, senderKey, serverPK, changeAddress, amountToServer, params, input);
	}

	public static Transaction createChannelTx(long amountToChange, ECKey senderKey, ECKey serverPK, TimeLockedAddress
		change, long amountToServer, NetworkParameters params, TransactionOutput... usedOutputs) {
		Transaction channelTransaction = new Transaction(params);
		Arrays.asList(usedOutputs).forEach(channelTransaction::addInput);
		channelTransaction.addOutput(P2PKOutput(channelTransaction, serverPK, amountToServer, params));
		channelTransaction.addOutput(changeOutput(channelTransaction, change, amountToChange, params));
		signAllInputs(channelTransaction, change.createRedeemScript(), senderKey);
		return channelTransaction;
	}

	public static void signInput(Transaction tx, int index, Script redeemScript, ECKey signingKey) {
		TransactionSignature sig = tx.calculateSignature(index, signingKey, redeemScript.getProgram(), SigHash.ALL, false);
		tx.getInput(index).setScriptSig(new ScriptBuilder().data(sig.encodeToBitcoin()).build());
	}

	public static void signAllInputs(Transaction tx, Script redeemScript, ECKey signingKey) {
		for (int i = 0; i < tx.getInputs().size(); i++) {
			signInput(tx, i, redeemScript, signingKey);
		}
	}

	public static SignedDTO createMicroPaymentRequestDTO(ECKey from, ECKey to, Transaction tx) {
		return buildRequestDTO(from, to, tx, 100L, validNonce());
	}

	public static SignedDTO createExternalPaymentRequestDTO(ECKey from, Transaction tx) {
		MicroPaymentRequestDTO microPaymentRequestDTO = new MicroPaymentRequestDTO(DTOUtils.toHex(tx.bitcoinSerialize
			()), from.getPublicKeyAsHex(), "", 0L, validNonce());
		return DTOUtils.serializeAndSign(microPaymentRequestDTO, from);
	}

	public static SignedDTO createExternalPaymentRequestDTO(ECKey from, Transaction tx, Long nonce) {
		MicroPaymentRequestDTO microPaymentRequestDTO = new MicroPaymentRequestDTO(DTOUtils.toHex(tx.bitcoinSerialize
			()), from.getPublicKeyAsHex(), "", 0L, nonce);
		return DTOUtils.serializeAndSign(microPaymentRequestDTO, from);
	}

	public static SignedDTO buildRequestDTO(ECKey from, ECKey to, Transaction tx, Long amount) {
		return buildRequestDTO(from, to, tx, amount, validNonce());
	}

	public static SignedDTO buildRequestDTO(ECKey from, ECKey to, Transaction tx, Long amount, Long nonce) {
		MicroPaymentRequestDTO microPaymentRequestDTO = new MicroPaymentRequestDTO(DTOUtils.toHex(tx.bitcoinSerialize
			()), from.getPublicKeyAsHex(), to.getPublicKeyAsHex(), amount, nonce);
		return DTOUtils.serializeAndSign(microPaymentRequestDTO, from);
	}

	private void sendAndExpect4xxError(SignedDTO dto, String expectedErrorMessage) throws Exception {
		MvcResult result = mockMvc.perform(post(URL_MICRO_PAYMENT).contentType(APPLICATION_JSON).content(DTOUtils
			.toJSON(dto))).andExpect(status().is4xxClientError()).andReturn();
		String errorMessage = DTOUtils.fromJSON(result.getResponse().getContentAsString(), ErrorDTO.class).getError();
		assertThat(errorMessage, containsString(expectedErrorMessage));
	}

	private void sendAndExpect2xxSuccess(SignedDTO dto) throws Exception {
		mockMvc.perform(post(URL_MICRO_PAYMENT).contentType(APPLICATION_JSON).content(DTOUtils.toJSON(dto))).andExpect
			(status().is2xxSuccessful());
	}

	private TransactionOutput anyP2PKOutput(Transaction forTransaction) {
		return new TransactionOutput(params, forTransaction, Coin.valueOf(100), new ECKey().toAddress(params));
	}

	public static TransactionOutput P2PKOutput(Transaction forTransaction, ECKey to, long value, NetworkParameters params) {
		return new TransactionOutput(params, forTransaction, Coin.valueOf(value), to.toAddress(params));
	}

	public static TransactionOutput changeOutput(Transaction forTransaction, TimeLockedAddress changeTo, long value, NetworkParameters params) {
		return new TransactionOutput(params, forTransaction, Coin.valueOf(value), changeTo.getAddress(params));
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
				walletService.addWatching(output.getAddressFromP2PKHScript(params));

			if (output.getScriptPubKey().isPayToScriptHash())
				walletService.addWatching(output.getAddressFromP2SH(params));
		});
	}

	private void mineTransaction(Transaction tx) throws PrunedException {
		Block lastBlock = walletService.blockChain().getChainHead().getHeader();
		Transaction spendTXClone = new Transaction(params, tx.bitcoinSerialize());
		Block newBlock = FakeTxBuilder.makeSolvedTestBlock(lastBlock, spendTXClone);
		walletService.blockChain().add(newBlock);
	}

}

