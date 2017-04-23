package com.coinblesk.server.controller;

import com.coinblesk.bitcoin.TimeLockedAddress;
import com.coinblesk.server.config.AppConfig;
import com.coinblesk.server.dao.AccountRepository;
import com.coinblesk.server.dto.ErrorDTO;
import com.coinblesk.server.dto.MicroPaymentRequestDTO;
import com.coinblesk.server.dto.SignedDTO;
import com.coinblesk.server.service.AccountService;
import com.coinblesk.server.service.WalletService;
import com.coinblesk.server.utilTest.CoinbleskTest;
import com.coinblesk.server.utilTest.FakeTxBuilder;
import com.coinblesk.server.utils.DTOUtils;
import org.bitcoinj.core.*;
import org.bitcoinj.core.Transaction.SigHash;
import org.bitcoinj.crypto.TransactionSignature;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

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
public class MicroPaymentControllerTest extends CoinbleskTest {
	public static final String URL_MICRO_PAYMENT = "/payment/micropayment";
	public static final String URL_VIRTUAL_PAYMENT = "/payment/virtualpayment";
	private static final long VALID_FEE = 250L;
	private static final long LOW_FEE = 50L;
	private static final long validLockTime = Instant.now().plus(Duration.ofDays(30)).getEpochSecond();
	private static MockMvc mockMvc;
	@Autowired
	private WebApplicationContext webAppContext;
	@Autowired
	private WalletService walletService;
	@Autowired
	private AccountService accountService;
	@Autowired
	private AccountRepository accountRepository;
	@Autowired
	private AppConfig appConfig;

	public static NetworkParameters params;

	@Before
	public void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(webAppContext).build();
		params = appConfig.getNetworkParameters();
	}

	@Test
	public void microPayment_failsOnEmpty() throws Exception {
		mockMvc.perform(post(URL_VIRTUAL_PAYMENT)).andExpect(status().is4xxClientError());
	}

	@Test
	public void microPayment_failsOnEmptyPayload() throws Exception {
		mockMvc.perform(post(URL_VIRTUAL_PAYMENT).contentType(APPLICATION_JSON).content("{}")).andExpect(status()
			.is4xxClientError());
	}

	@Test
	public void microPayment_failsOnWrongSignature() throws Exception {
		ECKey fromPublicKey = new ECKey();
		ECKey signingKey = new ECKey();
		Transaction microPaymentTransaction = new Transaction(params);
		MicroPaymentRequestDTO microPaymentRequestDTO = new MicroPaymentRequestDTO(DTOUtils.toHex
			(microPaymentTransaction.bitcoinSerialize()), fromPublicKey.getPublicKeyAsHex(), new ECKey()
			.getPublicKeyAsHex(), 100L, 0L);
		SignedDTO dto = DTOUtils.serializeAndSign(microPaymentRequestDTO, signingKey);
		sendAndExpect4xxError(dto, "Signature is not valid");
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
		SignedDTO dto = createMicroPaymentRequestDTO(clientKey, new ECKey(), microPaymentTransaction);
		sendAndExpect4xxError(dto, "Transaction had no inputs or no outputs");
	}

	@Test
	public void microPayment_failsOnUnknownUTXOs() throws Exception {
		ECKey clientKey = new ECKey();
		Transaction microPaymentTransaction = FakeTxBuilder.createFakeTx(params);
		SignedDTO dto = createMicroPaymentRequestDTO(clientKey, new ECKey(), microPaymentTransaction);
		sendAndExpect4xxError(dto, "Transaction spends unknown UTXOs");
	}

	@Test
	public void microPayment_failsOnWrongAddressType() throws Exception {
		ECKey clientKey = new ECKey();

		Transaction fundingTx1 = FakeTxBuilder.createFakeTx(params); // Creates a P2PKHash output, not what we want
		Transaction fundingTx2 = FakeTxBuilder.createFakeP2SHTx(params); // Creates a P2SH output, this would be ok
		watchAndMineTransactions(fundingTx1, fundingTx2);

		Transaction microPaymentTransaction = new Transaction(params);
		microPaymentTransaction.addOutput(anyP2PKOutput(microPaymentTransaction));
		microPaymentTransaction.addInput(fundingTx1.getOutput(0));
		microPaymentTransaction.addInput(fundingTx2.getOutput(0));

		SignedDTO dto = createMicroPaymentRequestDTO(clientKey, new ECKey(), microPaymentTransaction);
		sendAndExpect4xxError(dto, "Transaction must spent P2SH addresses");
	}

	@Test
	public void microPayment_failsOnUnknownTLAInputs() throws Exception {
		final ECKey clientKey = new ECKey();
		final ECKey serverKey = new ECKey();

		// Create a tla but without registering it in any way with the server
		TimeLockedAddress tla = new TimeLockedAddress(clientKey.getPubKey(), serverKey.getPubKey(), validLockTime);
		Transaction fundingTx = FakeTxBuilder.createFakeTxWithoutChangeAddress(params, tla.getAddress(params));
		watchAndMineTransactions(fundingTx);

		Transaction microPaymentTransaction = new Transaction(params);
		microPaymentTransaction.addInput(fundingTx.getOutput(0));
		microPaymentTransaction.addOutput(anyP2PKOutput(microPaymentTransaction));

		SignedDTO dto = createMicroPaymentRequestDTO(clientKey, new ECKey(), microPaymentTransaction);
		sendAndExpect4xxError(dto, "Used TLA inputs are not known to server");
	}

	@Test
	public void microPayment_failsOnSpentUTXOs() throws Exception {
		final ECKey clientKey = new ECKey();
		final ECKey serverKeySender = accountService.createAcount(clientKey);

		TimeLockedAddress inputAddress = accountService.createTimeLockedAddress(clientKey, validLockTime)
			.getTimeLockedAddress();
		walletService.addWatching(inputAddress.getAddress(params));
		Transaction fundingTx = FakeTxBuilder.createFakeTxWithoutChangeAddress(params, inputAddress.getAddress
			(params));
		mineTransaction(fundingTx);

		// Spend the funding tx
		walletService.getWallet().maybeCommitTx(createSpendingTransactionForOutput(fundingTx.getOutput(0),
			inputAddress, clientKey, serverKeySender));

		Transaction microPaymentTransaction = new Transaction(params);
		microPaymentTransaction.addInput(fundingTx.getOutput(0));
		microPaymentTransaction.addOutput(P2PKOutput(microPaymentTransaction, serverKeySender));
		signAllInputs(microPaymentTransaction, inputAddress.createRedeemScript(), clientKey);

		SignedDTO dto = createMicroPaymentRequestDTO(clientKey, new ECKey(), microPaymentTransaction);
		sendAndExpect4xxError(dto, "Input is already spent");
	}

	@Test
	public void microPayment_failsOnNonMinedUTXOs() throws Exception {
		final ECKey clientKey = new ECKey();
		final ECKey serverKeySender = accountService.createAcount(clientKey);

		final ECKey receiverKey = new ECKey();
		accountService.createAcount(receiverKey);

		TimeLockedAddress inputAddress = accountService.createTimeLockedAddress(clientKey, validLockTime)
			.getTimeLockedAddress();
		walletService.addWatching(inputAddress.getAddress(params));

		Transaction fundingTx1 = FakeTxBuilder.createFakeTxWithoutChangeAddress(params, inputAddress.getAddress
			(params));
		walletService.getWallet().maybeCommitTx(fundingTx1);

		Transaction microPaymentTransaction = new Transaction(params);
		microPaymentTransaction.addInput(fundingTx1.getOutput(0));
		microPaymentTransaction.addOutput(P2PKOutput(microPaymentTransaction, serverKeySender));
		signAllInputs(microPaymentTransaction, inputAddress.createRedeemScript(), clientKey);

		SignedDTO dto = createMicroPaymentRequestDTO(clientKey, receiverKey, microPaymentTransaction);
		sendAndExpect4xxError(dto, "UTXO must be mined");
	}

	@Test
	public void microPayment_failsOnSoonLockedInputs() throws Exception {
		final ECKey clientKey = new ECKey();
		final long lockTime = Instant.now().plus(Duration.ofHours(20)).getEpochSecond();

		accountService.createAcount(clientKey);
		TimeLockedAddress inputAddress = accountService.createTimeLockedAddress(clientKey, lockTime)
			.getTimeLockedAddress();
		Transaction fundingTx = FakeTxBuilder.createFakeTxWithoutChangeAddress(params, inputAddress.getAddress
			(params));
		watchAndMineTransactions(fundingTx);

		Transaction microPaymentTransaction = new Transaction(params);
		microPaymentTransaction.addInput(fundingTx.getOutput(0));
		microPaymentTransaction.addOutput(anyP2PKOutput(microPaymentTransaction));

		SignedDTO dto = createMicroPaymentRequestDTO(clientKey, new ECKey(), microPaymentTransaction);
		sendAndExpect4xxError(dto, "Inputs must be locked for 24 hours");
	}

	@Test
	public void microPayment_failsOnInputsBelongingToMultipleAccounts() throws Exception {
		final ECKey clientKey1 = new ECKey();
		final ECKey clientKey2 = new ECKey();

		accountService.createAcount(clientKey1);
		accountService.createAcount(clientKey2);
		TimeLockedAddress addressClient1 = accountService.createTimeLockedAddress(clientKey1, validLockTime)
			.getTimeLockedAddress();
		// This address belongs to a different client, which is not allowed
		TimeLockedAddress addressClient2 = accountService.createTimeLockedAddress(clientKey2, validLockTime)
			.getTimeLockedAddress();

		// Fund both addresses
		Transaction fundingTx1 = FakeTxBuilder.createFakeTxWithoutChangeAddress(params, addressClient1.getAddress
			(params));
		Transaction fundingTx2 = FakeTxBuilder.createFakeTxWithoutChangeAddress(params, addressClient2.getAddress
			(params));
		watchAndMineTransactions(fundingTx1, fundingTx2);

		// Use them as inputs
		Transaction microPaymentTransaction = new Transaction(params);
		microPaymentTransaction.addInput(fundingTx1.getOutput(0));
		microPaymentTransaction.addInput(fundingTx2.getOutput(0));
		microPaymentTransaction.addOutput(anyP2PKOutput(microPaymentTransaction));

		SignedDTO dto = createMicroPaymentRequestDTO(clientKey1, new ECKey(), microPaymentTransaction);
		sendAndExpect4xxError(dto, "Inputs must be from one account");
	}

	@Test
	public void microPayment_failsOnSignedByWrongAccount() throws Exception {
		final ECKey clientKey = new ECKey();

		accountService.createAcount(clientKey);
		TimeLockedAddress addressClient = accountService.createTimeLockedAddress(clientKey, validLockTime)
			.getTimeLockedAddress();
		Transaction fundingTx = FakeTxBuilder.createFakeTxWithoutChangeAddress(params, addressClient.getAddress
			(params));
		watchAndMineTransactions(fundingTx);

		Transaction microPaymentTransaction = new Transaction(params);
		microPaymentTransaction.addOutput(anyP2PKOutput(microPaymentTransaction));
		microPaymentTransaction.addInput(fundingTx.getOutput(0));


		SignedDTO dto = createMicroPaymentRequestDTO(new ECKey(), new ECKey(), microPaymentTransaction);
		sendAndExpect4xxError(dto, "Request was not signed by owner of inputs");
	}

	@Test
	public void microPayment_failsOnNoOutputForServer() throws Exception {
		final ECKey senderKey = new ECKey();
		final ECKey receiverKey = new ECKey();

		accountService.createAcount(senderKey);
		TimeLockedAddress timeLockedAddress = accountService.createTimeLockedAddress(senderKey, validLockTime)
			.getTimeLockedAddress();
		Transaction fundingTx = FakeTxBuilder.createFakeTxWithoutChangeAddress(params, timeLockedAddress.getAddress
			(params));
		watchAndMineTransactions(fundingTx);

		Transaction microPaymentTransaction = new Transaction(params);
		microPaymentTransaction.addOutput(anyP2PKOutput(microPaymentTransaction));
		microPaymentTransaction.addInput(fundingTx.getOutput(0));
		signAllInputs(microPaymentTransaction, timeLockedAddress.createRedeemScript(), senderKey);

		SignedDTO dto = createMicroPaymentRequestDTO(senderKey, receiverKey, microPaymentTransaction);
		sendAndExpect4xxError(dto, "Transaction must have exactly one output for server");
	}

	@Test
	public void microPayment_failsOnUnknownReceiver() throws Exception {
		final ECKey senderKey = new ECKey();
		final ECKey receiverKey = new ECKey();

		ECKey serverPublicKey = accountService.createAcount(senderKey);
		TimeLockedAddress timeLockedAddress = accountService.createTimeLockedAddress(senderKey, validLockTime)
			.getTimeLockedAddress();
		Transaction fundingTx = FakeTxBuilder.createFakeTxWithoutChangeAddress(params, timeLockedAddress.getAddress
			(params));
		watchAndMineTransactions(fundingTx);

		Transaction microPaymentTransaction = new Transaction(params);
		microPaymentTransaction.addOutput(P2PKOutput(microPaymentTransaction, serverPublicKey));
		microPaymentTransaction.addInput(fundingTx.getOutput(0));
		signAllInputs(microPaymentTransaction, timeLockedAddress.createRedeemScript(), senderKey);


		SignedDTO dto = createMicroPaymentRequestDTO(senderKey, receiverKey, microPaymentTransaction);
		sendAndExpect4xxError(dto, "Receiver is unknown to server");
	}

	@Test
	public void microPayment_failsSendingToOneself() throws Exception {
		final ECKey senderKey = new ECKey();

		ECKey serverPublicKey = accountService.createAcount(senderKey);
		TimeLockedAddress timeLockedAddress = accountService.createTimeLockedAddress(senderKey, validLockTime)
			.getTimeLockedAddress();
		Transaction fundingTx = FakeTxBuilder.createFakeTxWithoutChangeAddress(params, timeLockedAddress.getAddress
			(params));
		watchAndMineTransactions(fundingTx);

		Transaction microPaymentTransaction = new Transaction(params);
		microPaymentTransaction.addInput(fundingTx.getOutput(0));
		microPaymentTransaction.addOutput(P2PKOutput(microPaymentTransaction, serverPublicKey));
		signAllInputs(microPaymentTransaction, timeLockedAddress.createRedeemScript(), senderKey);

		SignedDTO dto = createMicroPaymentRequestDTO(senderKey, senderKey, microPaymentTransaction);
		sendAndExpect4xxError(dto, "Sender and receiver must be different");
	}

	@Test
	public void microPayment_failsWithMultipleChangeOutputs() throws Exception {
		final ECKey senderKey = new ECKey();
		final ECKey receiverKey = new ECKey();

		ECKey serverPublicKey = accountService.createAcount(senderKey);
		accountService.createAcount(receiverKey);

		TimeLockedAddress timeLockedAddress = accountService.createTimeLockedAddress(senderKey, validLockTime)
			.getTimeLockedAddress();
		Transaction fundingTx = FakeTxBuilder.createFakeTxWithoutChangeAddress(params, timeLockedAddress.getAddress
			(params));
		watchAndMineTransactions(fundingTx);

		Transaction microPaymentTransaction = new Transaction(params);
		microPaymentTransaction.addInput(fundingTx.getOutput(0));
		microPaymentTransaction.addOutput(P2PKOutput(microPaymentTransaction, serverPublicKey));
		microPaymentTransaction.addOutput(changeOutput(microPaymentTransaction, timeLockedAddress));
		microPaymentTransaction.addOutput(changeOutput(microPaymentTransaction, timeLockedAddress));
		signAllInputs(microPaymentTransaction, timeLockedAddress.createRedeemScript(), senderKey);

		SignedDTO dto = createMicroPaymentRequestDTO(senderKey, senderKey, microPaymentTransaction);
		sendAndExpect4xxError(dto, "Cannot have multiple change outputs");
	}

	@Test
	public void microPayment_closesChannelWhenChangeOutputIsSoonUnlocked() throws Exception {
		final ECKey senderKey = new ECKey();
		final ECKey receiverKey = new ECKey();

		ECKey serverPublicKey = accountService.createAcount(senderKey);
		accountService.createAcount(receiverKey);

		TimeLockedAddress inputAddress = accountService.createTimeLockedAddress(senderKey, validLockTime)
			.getTimeLockedAddress();
		TimeLockedAddress changeAddress = accountService.createTimeLockedAddress(senderKey, Instant.now().plus
			(Duration.ofHours(20)).getEpochSecond()).getTimeLockedAddress();
		Transaction fundingTx = FakeTxBuilder.createFakeTxWithoutChangeAddress(params, inputAddress.getAddress
			(params));
		watchAndMineTransactions(fundingTx);

		Transaction microPaymentTransaction = new Transaction(params);
		microPaymentTransaction.addInput(fundingTx.getOutput(0));
		microPaymentTransaction.addOutput(P2PKOutput(microPaymentTransaction, serverPublicKey));
		microPaymentTransaction.addOutput(changeOutput(microPaymentTransaction, changeAddress));
		signAllInputs(microPaymentTransaction, inputAddress.createRedeemScript(), senderKey);

		SignedDTO dto = createMicroPaymentRequestDTO(senderKey, receiverKey, microPaymentTransaction);
		sendAndExpect2xxSuccess(dto);
		assertThat(accountService.getByClientPublicKey(senderKey.getPubKey()).isLocked(), is(true));
	}

	@Test
	public void microPayment_closesChannelWhenSendingToThirdParty() throws Exception {
		final ECKey senderKey = new ECKey();
		final ECKey receiverKey = new ECKey();
		ECKey serverPublicKey = accountService.createAcount(senderKey);
		accountService.createAcount(receiverKey);

		assertThat(accountService.getByClientPublicKey(senderKey.getPubKey()).isLocked(), is(false));

		TimeLockedAddress inputAddress = accountService.createTimeLockedAddress(senderKey, validLockTime)
			.getTimeLockedAddress();
		Transaction fundingTx = FakeTxBuilder.createFakeTxWithoutChangeAddress(params, inputAddress.getAddress
			(params));
		watchAndMineTransactions(fundingTx);

		Transaction microPaymentTransaction = new Transaction(params);
		microPaymentTransaction.addInput(fundingTx.getOutput(0));
		microPaymentTransaction.addOutput(P2PKOutput(microPaymentTransaction, serverPublicKey));
		microPaymentTransaction.addOutput(anyP2PKOutput(microPaymentTransaction));
		signAllInputs(microPaymentTransaction, inputAddress.createRedeemScript(), senderKey);

		SignedDTO dto = createMicroPaymentRequestDTO(senderKey, receiverKey, microPaymentTransaction);
		sendAndExpect2xxSuccess(dto);
		assertThat(accountService.getByClientPublicKey(senderKey.getPubKey()).isLocked(), is(true));
	}

	@Test
	public void microPayment_failsWhenChannelIsLocked() throws Exception {
		final ECKey senderKey = new ECKey();
		ECKey serverPublicKey = accountService.createAcount(senderKey);
		accountRepository.save(accountRepository.findByClientPublicKey(senderKey.getPubKey()).locked(true));

		final ECKey receiverKey = new ECKey();
		accountService.createAcount(receiverKey);

		TimeLockedAddress inputAddress = accountService.createTimeLockedAddress(senderKey, validLockTime)
			.getTimeLockedAddress();
		Transaction fundingTx = FakeTxBuilder.createFakeTxWithoutChangeAddress(params, inputAddress.getAddress
			(params));
		watchAndMineTransactions(fundingTx);

		Transaction microPaymentTransaction = new Transaction(params);
		microPaymentTransaction.addInput(fundingTx.getOutput(0));
		microPaymentTransaction.addOutput(P2PKOutput(microPaymentTransaction, serverPublicKey));
		signAllInputs(microPaymentTransaction, inputAddress.createRedeemScript(), senderKey);

		SignedDTO dto = createMicroPaymentRequestDTO(senderKey, receiverKey, microPaymentTransaction);
		sendAndExpect4xxError(dto, "Channel is locked");
	}

	@Test
	public void microPayment_failsOnInvalidNonce() throws Exception {
		final ECKey senderKey = new ECKey();
		ECKey serverPublicKey = accountService.createAcount(senderKey);

		final ECKey receiverKey = new ECKey();
		accountService.createAcount(receiverKey);

		TimeLockedAddress inputAddress = accountService.createTimeLockedAddress(senderKey, validLockTime)
			.getTimeLockedAddress();
		Transaction fundingTx = FakeTxBuilder.createFakeTxWithoutChangeAddress(params, inputAddress.getAddress
			(params));
		watchAndMineTransactions(fundingTx);

		Transaction microPaymentTransaction = new Transaction(params);
		microPaymentTransaction.addInput(fundingTx.getOutput(0));
		microPaymentTransaction.addOutput(P2PKOutput(microPaymentTransaction, serverPublicKey));
		signAllInputs(microPaymentTransaction, inputAddress.createRedeemScript(), senderKey);

		SignedDTO dto = createMicroPaymentRequestDTO(senderKey, receiverKey, microPaymentTransaction, 1000L, 0L);
		sendAndExpect4xxError(dto, "Invalid nonce");

		accountRepository.save(accountRepository.findByClientPublicKey(senderKey.getPubKey()).nonce(Instant.now()
			.getEpochSecond()));
		dto = createMicroPaymentRequestDTO(senderKey, receiverKey, microPaymentTransaction, Instant.now().minus
			(Duration.ofMinutes(10)).getEpochSecond());
		sendAndExpect4xxError(dto, "Invalid nonce");
	}

	@Test
	public void microPayment_failsWhenInputAmountIsTooSmall() throws Exception {
		final ECKey senderKey = new ECKey();
		ECKey serverPublicKey = accountService.createAcount(senderKey);
		final ECKey receiverKey = new ECKey();
		accountService.createAcount(receiverKey);
		TimeLockedAddress inputAddress = accountService.createTimeLockedAddress(senderKey, validLockTime)
			.getTimeLockedAddress();
		Transaction fundingTx = FakeTxBuilder.createFakeTxWithoutChangeAddress(params, inputAddress.getAddress
			(params));
		watchAndMineTransactions(fundingTx);

		// Try to send more than the inputs are worth
		Transaction microPaymentTransaction = createChannelTx(fundingTx.getOutput(0), senderKey, serverPublicKey,
			inputAddress, Coin.COIN.plus(Coin.SATOSHI).getValue());
		SignedDTO dto = createMicroPaymentRequestDTO(senderKey, receiverKey, microPaymentTransaction, 800L);
		sendAndExpect4xxError(dto, "Transaction output negative");
	}

	@Test
	public void microPayment_failsWithWrongAmountOnOpenChannel() throws Exception {
		final ECKey senderKey = new ECKey();
		ECKey serverPublicKey = accountService.createAcount(senderKey);

		final ECKey receiverKey = new ECKey();
		accountService.createAcount(receiverKey);

		TimeLockedAddress inputAddress = accountService.createTimeLockedAddress(senderKey, validLockTime)
			.getTimeLockedAddress();
		Transaction fundingTx = FakeTxBuilder.createFakeTxWithoutChangeAddress(params, inputAddress.getAddress
			(params));
		watchAndMineTransactions(fundingTx);

		// Try to send 42 satoshis, but actally give only 41
		Transaction microPaymentTransaction = createChannelTx(fundingTx.getOutput(0), senderKey, serverPublicKey,
			inputAddress, 41L);
		SignedDTO dto = createMicroPaymentRequestDTO(senderKey, receiverKey, microPaymentTransaction, 42L);
		sendAndExpect4xxError(dto, "Invalid amount. 42 requested but 41 given");

		// Previous transaction has 100 to server
		Transaction prevChannelTX = createChannelTx(fundingTx.getOutput(0), senderKey, serverPublicKey, inputAddress,
			100L);
		accountRepository.save(accountRepository.findByClientPublicKey(senderKey.getPubKey()).channelTransaction
			(prevChannelTX.bitcoinSerialize()));

		// Try to send 200 satoshis, but actally give only 100 (200 - previous 100)
		microPaymentTransaction = createChannelTx(fundingTx.getOutput(0), senderKey, serverPublicKey, inputAddress,
			200L);
		dto = createMicroPaymentRequestDTO(senderKey, receiverKey, microPaymentTransaction, 200L);
		sendAndExpect4xxError(dto, "Invalid amount. 200 requested but 100 given");

		// Try to send 50 satoshis, but actually give 100 to server.
		microPaymentTransaction = createChannelTx(fundingTx.getOutput(0), senderKey, serverPublicKey, inputAddress,
			200L);
		dto = createMicroPaymentRequestDTO(senderKey, receiverKey, microPaymentTransaction, 50L);
		sendAndExpect4xxError(dto, "Invalid amount. 50 requested but 100 given");

		// Try to send negative amount
		microPaymentTransaction = createChannelTx(fundingTx.getOutput(0), senderKey, serverPublicKey, inputAddress,
			50L);
		dto = createMicroPaymentRequestDTO(senderKey, receiverKey, microPaymentTransaction, -50L);
		sendAndExpect4xxError(dto, "Can't send zero or negative amont");

		// Try to send zero amount
		microPaymentTransaction = createChannelTx(fundingTx.getOutput(0), senderKey, serverPublicKey, inputAddress,
			400L);
		dto = createMicroPaymentRequestDTO(senderKey, receiverKey, microPaymentTransaction, 0L);
		sendAndExpect4xxError(dto, "Can't send zero or negative amont");
	}

	@Test
	public void microPayment_failsWhenSendingMoreThanAllowed() throws Exception {
		final ECKey senderKey = new ECKey();
		final ECKey serverPublicKey = accountService.createAcount(senderKey);
		final ECKey receiverKey = new ECKey();
		accountService.createAcount(receiverKey);
		TimeLockedAddress inputAddress = accountService.createTimeLockedAddress(senderKey, validLockTime)
			.getTimeLockedAddress();
		Transaction fundingTx = FakeTxBuilder.createFakeTxWithoutChangeAddress(params, inputAddress.getAddress
			(params));
		watchAndMineTransactions(fundingTx);
		long amountTryingToSend = Coin.MILLICOIN.multiply(800).getValue();
		Transaction microPaymentTransaction = createChannelTx(fundingTx.getOutput(0), senderKey, serverPublicKey,
			inputAddress, amountTryingToSend);
		SignedDTO dto = createMicroPaymentRequestDTO(senderKey, receiverKey, microPaymentTransaction,
			amountTryingToSend);
		sendAndExpect4xxError(dto, "Maximum channel value reached");
	}

	@Test
	public void microPayment_failsWithInsufficientFee() throws Exception {
		final ECKey senderKey = new ECKey();
		ECKey serverPublicKey = accountService.createAcount(senderKey);

		final ECKey receiverKey = new ECKey();
		accountService.createAcount(receiverKey);

		TimeLockedAddress inputAddress = accountService.createTimeLockedAddress(senderKey, validLockTime)
			.getTimeLockedAddress();
		Transaction fundingTx = FakeTxBuilder.createFakeTxWithoutChangeAddress(params, inputAddress.getAddress
			(params));
		watchAndMineTransactions(fundingTx);

		long estimatedSize = calculateChannelTxSize(inputAddress, senderKey, serverPublicKey, fundingTx.getOutput(0));
		final Coin amountToServer = Coin.valueOf(1000L);
		final Coin fee = Coin.valueOf(estimatedSize * LOW_FEE);
		final Coin changeAmount = fundingTx.getOutput(0).getValue().minus(amountToServer).minus(fee);
		Transaction microPaymentTransaction = createChannelTx(changeAmount.getValue(), senderKey, serverPublicKey, inputAddress, amountToServer.getValue(), fundingTx.getOutput(0)
		);
		SignedDTO dto = createMicroPaymentRequestDTO(senderKey, receiverKey, microPaymentTransaction, amountToServer
			.getValue());
		sendAndExpect4xxError(dto, "Insufficient transaction fee");
	}

	@Test
	public void microPayment_locksAccountWhenSendingOverThreshold() throws Exception {
		final ECKey senderKey = new ECKey();
		accountService.createAcount(senderKey);
		final ECKey receiverKey = new ECKey();
		accountService.createAcount(receiverKey);

		TimeLockedAddress tla = accountService.createTimeLockedAddress(senderKey, validLockTime)
			.getTimeLockedAddress();
		Transaction fundingTx = FakeTxBuilder.createFakeTxWithoutChangeAddress(params, tla.getAddress(params));
		walletService.addWatching(tla.getAddress(params));
		mineTransaction(fundingTx);

		SignedDTO dto = createMicroPaymentRequestDTO(senderKey, receiverKey, 12000000L, tla, fundingTx.getOutput(0));
		sendAndExpect2xxSuccess(dto);
		assertThat(accountService.getByClientPublicKey(senderKey.getPubKey()).isLocked(), is(true));
	}

	@Test
	public void microPayment_sendsAmountToReceiver() throws Exception {
		final ECKey senderKey = new ECKey();
		accountService.createAcount(senderKey);
		final ECKey receiverKey = new ECKey();
		accountService.createAcount(receiverKey);

		TimeLockedAddress tla = accountService.createTimeLockedAddress(senderKey, validLockTime)
			.getTimeLockedAddress();
		Transaction fundingTx = FakeTxBuilder.createFakeTxWithoutChangeAddress(params, tla.getAddress(params));
		walletService.addWatching(tla.getAddress(params));
		mineTransaction(fundingTx);

		SignedDTO dto = createMicroPaymentRequestDTO(senderKey, receiverKey, 1000L, tla, fundingTx.getOutput(0));
		sendAndExpect2xxSuccess(dto);
		assertThat(accountService.getVirtualBalanceByClientPublicKey(receiverKey.getPubKey()).getBalance(), is(1000L));
	}

	@Test
	public void microPayment_savesBroadcastableTransaction() throws Exception {
		final ECKey senderKey = new ECKey();
		accountService.createAcount(senderKey);
		final ECKey receiverKey = new ECKey();
		accountService.createAcount(receiverKey);

		TimeLockedAddress tla = accountService.createTimeLockedAddress(senderKey, validLockTime)
			.getTimeLockedAddress();
		Transaction fundingTx = FakeTxBuilder.createFakeTxWithoutChangeAddress(params, tla.getAddress(params));
		walletService.addWatching(tla.getAddress(params));
		mineTransaction(fundingTx);

		SignedDTO dto = createMicroPaymentRequestDTO(senderKey, receiverKey, 1000L, tla, fundingTx.getOutput(0));
		sendAndExpect2xxSuccess(dto);

		Transaction openChannelTx = new Transaction(params, accountService.getByClientPublicKey(senderKey.getPubKey
			()).getChannelTransaction());
		openChannelTx.verify();
		walletService.getWallet().maybeCommitTx(openChannelTx);
	}

	@Test
	public void microPayment_replayAttackFails() throws Exception {
		final ECKey senderKey = new ECKey();
		accountService.createAcount(senderKey);
		final ECKey receiverKey = new ECKey();
		accountService.createAcount(receiverKey);

		TimeLockedAddress tla = accountService.createTimeLockedAddress(senderKey, validLockTime)
			.getTimeLockedAddress();
		Transaction fundingTx = FakeTxBuilder.createFakeTxWithoutChangeAddress(params, tla.getAddress(params));
		walletService.addWatching(tla.getAddress(params));
		mineTransaction(fundingTx);

		SignedDTO dto = createMicroPaymentRequestDTO(senderKey, receiverKey, 1337L, tla, fundingTx.getOutput(0));
		sendAndExpect2xxSuccess(dto);

		assertThat(accountService.getByClientPublicKey(receiverKey.getPubKey()).virtualBalance(), is(1337L));

		// Send again unmodified fails because nonce is the same
		sendAndExpect4xxError(dto, "Invalid nonce");

		// Increasing nonce fails because now the signature is wrong
		MicroPaymentRequestDTO orig = DTOUtils.fromJSON(DTOUtils.fromBase64(dto.getPayload()), MicroPaymentRequestDTO
			.class);
		MicroPaymentRequestDTO modifiedRequest = new MicroPaymentRequestDTO(orig.getTx(), orig.getFromPublicKey(),
			orig.getToPublicKey(), orig.getAmount(), Instant.now().plus(Duration.ofMinutes(1L)).getEpochSecond());
		sendAndExpect4xxError(new SignedDTO(DTOUtils.toBase64(DTOUtils.toJSON(modifiedRequest)), dto.getSignature()),
			"Signature is not valid");

		// Signing again with some other key (that of the receiver) fails as well
		sendAndExpect4xxError(DTOUtils.serializeAndSign(modifiedRequest, receiverKey), "Signature is not valid");

		// Change sender to some other account's public key (that we own) in order to 'trick' system
		ECKey otherOwnedAccount = new ECKey();
		accountService.createAcount(otherOwnedAccount);
		MicroPaymentRequestDTO modifiedRequest2 = new MicroPaymentRequestDTO(orig.getTx(), otherOwnedAccount
			.getPublicKeyAsHex(), orig.getToPublicKey(), orig.getAmount(), Instant.now().plus(Duration.ofMinutes(1L))
			.getEpochSecond());
		sendAndExpect4xxError(DTOUtils.serializeAndSign(modifiedRequest2, otherOwnedAccount), "Request was not " +
			"signed by owner of inputs");

		// Amount stayed the same during all failed attacks
		assertThat(accountService.getByClientPublicKey(receiverKey.getPubKey()).virtualBalance(), is(1337L));

	}

	@Test
	public void microPayment_increasesServerPotWhenMined() throws Exception {
		final ECKey senderKey = new ECKey();
		accountService.createAcount(senderKey);
		final ECKey receiverKey = new ECKey();
		accountService.createAcount(receiverKey);

		assertThat(walletService.microPaymentPot(), is(Coin.ZERO));

		TimeLockedAddress tla = accountService.createTimeLockedAddress(senderKey, validLockTime)
			.getTimeLockedAddress();
		Transaction fundingTx = FakeTxBuilder.createFakeTxWithoutChangeAddress(params, tla.getAddress(params));
		walletService.addWatching(tla.getAddress(params));
		walletService.getWallet().importKey(ECKey.fromPrivate(accountRepository.findByClientPublicKey(senderKey
			.getPubKey()).serverPrivateKey()));
		mineTransaction(fundingTx);

		SignedDTO dto = createMicroPaymentRequestDTO(senderKey, receiverKey, 1337L, tla, fundingTx.getOutput(0));
		sendAndExpect2xxSuccess(dto);

		Transaction openChannelTx = new Transaction(params, accountService.getByClientPublicKey(senderKey.getPubKey
			()).getChannelTransaction());
		mineTransaction(openChannelTx);

		assertThat(walletService.microPaymentPot(), is(Coin.valueOf((1337))));
	}

	@Test
	public void microPayment_setsCorrectBroadcastBefore() throws Exception {
		final ECKey senderKey = new ECKey();
		final ECKey serverKey = accountService.createAcount(senderKey);
		final ECKey receiverKey = new ECKey();
		accountService.createAcount(receiverKey);

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

		Transaction channelTx = new Transaction(params);
		channelTx.addOutput(P2PKOutput(channelTx, serverKey, 1000L));
		channelTx.addOutput(changeOutput(channelTx, tla3));
		channelTx.addInput(fundingTx1.getOutput(0));
		channelTx.addInput(fundingTx2.getOutput(0));
		channelTx.addInput(fundingTx3.getOutput(0));
		signInput(channelTx, 0, tla1.createRedeemScript(), senderKey);
		signInput(channelTx, 1, tla2.createRedeemScript(), senderKey);
		signInput(channelTx, 2, tla3.createRedeemScript(), senderKey);

		SignedDTO dto = createMicroPaymentRequestDTO(senderKey, receiverKey, channelTx, 1000L);
		sendAndExpect2xxSuccess(dto);

		long savedBroadcastBefore = accountRepository.findByClientPublicKey(senderKey.getPubKey()).getBroadcastBefore();
		assertThat(savedBroadcastBefore, is(tla2.getLockTime()));
	}

	public static SignedDTO createMicroPaymentRequestDTO(ECKey from, ECKey to, Long amount, TimeLockedAddress address,
														 TransactionOutput... usedOutputs) {
		ECKey serverPublicKey = ECKey.fromPublicOnly(address.getServerPubKey());
		long estimatedSize = calculateChannelTxSize(address, from, serverPublicKey, usedOutputs);
		final Coin amountToServer = Coin.valueOf(amount);
		final Coin fee = Coin.valueOf(estimatedSize * VALID_FEE);
		Coin valueOfUTXOs = Arrays.stream(usedOutputs).map(TransactionOutput::getValue).reduce(Coin.ZERO, Coin::plus);
		final Coin changeAmount = valueOfUTXOs.minus(amountToServer).minus(fee);
		Transaction microPaymentTransaction = createChannelTx(changeAmount.getValue(), from, serverPublicKey, address,
			amountToServer.getValue(), usedOutputs);
		return createMicroPaymentRequestDTO(from, to, microPaymentTransaction, amount);
	}

	private Transaction createChannelTx(TransactionOutput input, ECKey senderKey, ECKey serverPK, TimeLockedAddress
		changeAddress, long amountToServer) {
		return createChannelTx(input.getValue().getValue()
			- amountToServer, senderKey, serverPK, changeAddress, amountToServer, input);
	}

	public static Transaction createChannelTx(long amountToChange, ECKey senderKey, ECKey serverPK, TimeLockedAddress
		change, long amountToServer, TransactionOutput... usedOutputs) {
		Transaction channelTransaction = new Transaction(params);
		Arrays.asList(usedOutputs).forEach(channelTransaction::addInput);
		channelTransaction.addOutput(P2PKOutput(channelTransaction, serverPK, amountToServer));
		channelTransaction.addOutput(changeOutput(channelTransaction, change, amountToChange));
		signAllInputs(channelTransaction, change.createRedeemScript(), senderKey);
		return channelTransaction;
	}

	public static long calculateChannelTxSize(TimeLockedAddress change, ECKey senderKey, ECKey serverPK,
											  TransactionOutput... inputs) {
		Transaction tx = new Transaction(params);
		Arrays.asList(inputs).forEach(tx::addInput);
		tx.addOutput(P2PKOutput(tx, serverPK, 1L));
		tx.addOutput(changeOutput(tx, change, 1L));
		for (int i = 0; i < tx.getInputs().size(); i++) {
			TransactionSignature clientSig = tx.calculateSignature(i, senderKey, change.createRedeemScript()
				.getProgram(), SigHash.ALL, false);
			// Server sig is fake, but we're only interested in the resulting size of the script sig
			TransactionSignature serverSig = tx.calculateSignature(i, new ECKey(), change.createRedeemScript(),
				SigHash.ALL, false);
			Script finalSig = new ScriptBuilder().data(clientSig.encodeToBitcoin()).data(serverSig.encodeToBitcoin())
				.smallNum(1).data(change.createRedeemScript().getProgram()).build();
			tx.getInput(i).setScriptSig(finalSig);
		}
		return tx.bitcoinSerialize().length;
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
		return createMicroPaymentRequestDTO(from, to, tx, 100L, Instant.now().getEpochSecond());
	}

	public static SignedDTO createMicroPaymentRequestDTO(ECKey from, ECKey to, Transaction tx, Long amount) {
		return createMicroPaymentRequestDTO(from, to, tx, amount, Instant.now().getEpochSecond());
	}

	public static SignedDTO createMicroPaymentRequestDTO(ECKey from, ECKey to, Transaction tx, Long amount, Long nonce) {
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

	public static TransactionOutput P2PKOutput(Transaction forTransaction, ECKey to, long value) {
		return new TransactionOutput(params, forTransaction, Coin.valueOf(value), to.toAddress(params));
	}

	private TransactionOutput P2PKOutput(Transaction forTransaction, ECKey to) {
		return P2PKOutput(forTransaction, to, 100);
	}

	private TransactionOutput changeOutput(Transaction forTransaction, TimeLockedAddress changeTo) {
		return changeOutput(forTransaction, changeTo, 100);
	}

	public static TransactionOutput changeOutput(Transaction forTransaction, TimeLockedAddress changeTo, long value) {
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

	// Creates a transaction that spends the given output. Uses the TimeLockedAddress and the keys to create a valid
	// scriptSig
	private Transaction createSpendingTransactionForOutput(TransactionOutput output, TimeLockedAddress usedTLA, ECKey
		clientKey, ECKey serverKey) {
		Transaction tx = new Transaction(params);
		tx.addInput(output);
		TransactionOutput someOutput = new TransactionOutput(params, tx, Coin.SATOSHI, new ECKey().toAddress(params));
		tx.addOutput(someOutput);
		TransactionSignature serverSig = tx.calculateSignature(0, clientKey, usedTLA.createRedeemScript(), SigHash
			.ALL, false);
		TransactionSignature clientSig = tx.calculateSignature(0, serverKey, usedTLA.createRedeemScript(), SigHash
			.ALL, false);
		Script scriptSig = usedTLA.createScriptSigBeforeLockTime(clientSig, serverSig);
		tx.getInput(0).setScriptSig(scriptSig);
		return tx;
	}

	@Test
	public void virtualPayment_failsOnEmpty() throws Exception {
		mockMvc.perform(post(URL_VIRTUAL_PAYMENT)).andExpect(status().is4xxClientError());
	}

}

