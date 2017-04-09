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
import org.bitcoinj.store.BlockStoreException;
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
import static org.hamcrest.MatcherAssert.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/***
 * @author Sebastian Stephan
 */
public class MicroPaymentControllerTest extends CoinbleskTest {
	private static final String URL_MICRO_PAYMENT = "/payment/micropayment";
	private static final String URL_VIRTUAL_PAYMENT = "/payment/virtualpayment";

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
	private NetworkParameters params() {
		return appConfig.getNetworkParameters();
	}

	private static MockMvc mockMvc;

	@Before
	public void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(webAppContext).build();
	}

	@Test
	public void microPayment_failsOnEmpty() throws Exception {
		mockMvc.perform(post(URL_VIRTUAL_PAYMENT))
			.andExpect(status().is4xxClientError());
	}

	@Test
	public void microPayment_failsOnEmptyPayload() throws Exception {
		mockMvc.perform(post(URL_VIRTUAL_PAYMENT)
			.contentType(APPLICATION_JSON)
			.content("{}"))
			.andExpect(status().is4xxClientError());
	}

	@Test public void microPayment_failsOnWrongSignature() throws Exception {
		ECKey fromPublicKey = new ECKey();
		ECKey signingKey = new ECKey();
		Transaction microPaymentTransaction = new Transaction(params());
		MicroPaymentRequestDTO microPaymentRequestDTO = new MicroPaymentRequestDTO(
			DTOUtils.toHex(microPaymentTransaction.bitcoinSerialize()), fromPublicKey.getPublicKeyAsHex(),
			new ECKey().getPublicKeyAsHex(), 100L, 0L);
		SignedDTO dto = DTOUtils.serializeAndSign(microPaymentRequestDTO, signingKey);
		sendAndExpect4xxError(dto,  "Signature is not valid");
	}

	@Test public void microPayment_failsOnNoTransaction() throws Exception {
		ECKey clientKey = new ECKey();
		MicroPaymentRequestDTO microPaymentRequestDTO = new MicroPaymentRequestDTO(
			"", new ECKey().getPublicKeyAsHex(), new ECKey().getPublicKeyAsHex(), 10L, 0L);
		SignedDTO dto = DTOUtils.serializeAndSign(microPaymentRequestDTO, clientKey);
		sendAndExpect4xxError(dto, "");
	}

	@Test public void microPayment_failsOnEmptyTransaction() throws Exception {
		ECKey clientKey = new ECKey();
		Transaction microPaymentTransaction = new Transaction(params());
		SignedDTO dto = createMicroPaymentRequestDTO(clientKey, new ECKey(), microPaymentTransaction);
		sendAndExpect4xxError(dto, "Transaction had no inputs or no outputs");
	}

	@Test public void microPayment_failsOnUnknownUTXOs() throws Exception {
		ECKey clientKey = new ECKey();
		Transaction microPaymentTransaction = FakeTxBuilder.createFakeTx(params());
		SignedDTO dto = createMicroPaymentRequestDTO(clientKey, new ECKey(), microPaymentTransaction);
		sendAndExpect4xxError(dto,  "Transaction spends unknown UTXOs");
	}

	@Test public void microPayment_failsOnWrongAddressType() throws Exception {
		ECKey clientKey = new ECKey();

		Transaction fundingTx1 = FakeTxBuilder.createFakeTx(params()); // Creates a P2PKHash output, not what we want
		Transaction fundingTx2 = FakeTxBuilder.createFakeP2SHTx(params()); // Creates a P2SH output, this would be ok
		watchAndMineTransactions(fundingTx1, fundingTx2);

		Transaction microPaymentTransaction = new Transaction(params());
		microPaymentTransaction.addOutput(anyP2PKOutput(microPaymentTransaction));
		microPaymentTransaction.addInput(fundingTx1.getOutput(0));
		microPaymentTransaction.addInput(fundingTx2.getOutput(0));

		SignedDTO dto = createMicroPaymentRequestDTO(clientKey, new ECKey(), microPaymentTransaction);
		sendAndExpect4xxError(dto,  "Transaction must spent P2SH addresses");
	}

	@Test public void microPayment_failsOnUnknownTLAInputs() throws Exception {
		final ECKey clientKey = new ECKey();
		final ECKey serverKey = new ECKey();

		// Create a tla but without registering it in any way with the server
		TimeLockedAddress tla = new TimeLockedAddress(clientKey.getPubKey(), serverKey.getPubKey(), validLockTime);
		Transaction fundingTx = FakeTxBuilder.createFakeTxWithoutChangeAddress(params(), Coin.COIN,
			tla.getAddress(params()));
		watchAndMineTransactions(fundingTx);

		Transaction microPaymentTransaction = new Transaction(params());
		microPaymentTransaction.addInput(fundingTx.getOutput(0));
		microPaymentTransaction.addOutput(anyP2PKOutput(microPaymentTransaction));

		SignedDTO dto = createMicroPaymentRequestDTO(clientKey, new ECKey(), microPaymentTransaction);
		sendAndExpect4xxError(dto,  "Used TLA inputs are not known to server");
	}

	@Test public void microPayment_failsOnSpentUTXOs() throws Exception {
		final ECKey clientKey = new ECKey();
		final ECKey serverKeySender = accountService.createAcount(clientKey);

		TimeLockedAddress inputAddress = accountService.createTimeLockedAddress(clientKey, validLockTime)
			.getTimeLockedAddress();
		walletService.addWatching(inputAddress.getAddress(params()));
		Transaction fundingTx = FakeTxBuilder.createFakeTxWithoutChangeAddress(params(), Coin.COIN,
			inputAddress.getAddress(params()));
		mineTransaction(fundingTx);

		// Spend the funding tx
		walletService.getWallet().maybeCommitTx(createSpendingTransactionForOutput(fundingTx.getOutput(0),
			inputAddress, clientKey, serverKeySender));

		Transaction microPaymentTransaction = new Transaction(params());
		microPaymentTransaction.addInput(fundingTx.getOutput(0));
		microPaymentTransaction.addOutput(P2PKOutput(microPaymentTransaction, serverKeySender));
		signAllInputs(microPaymentTransaction, inputAddress.createRedeemScript(), clientKey);

		SignedDTO dto = createMicroPaymentRequestDTO(clientKey, new ECKey(), microPaymentTransaction);
		sendAndExpect4xxError(dto, "Input is already spent");
	}

	@Test public void microPayment_failsOnNonMinedUTXOs() throws Exception {
		final ECKey clientKey = new ECKey();
		final ECKey serverKeySender = accountService.createAcount(clientKey);

		final ECKey receiverKey = new ECKey();
		accountService.createAcount(receiverKey);

		TimeLockedAddress inputAddress = accountService.createTimeLockedAddress(clientKey, validLockTime)
			.getTimeLockedAddress();
		walletService.addWatching(inputAddress.getAddress(params()));

		Transaction fundingTx1 = FakeTxBuilder.createFakeTxWithoutChangeAddress(params(), Coin.COIN,
			inputAddress.getAddress(params()));
		walletService.getWallet().maybeCommitTx(fundingTx1);

		Transaction microPaymentTransaction = new Transaction(params());
		microPaymentTransaction.addInput(fundingTx1.getOutput(0));
		microPaymentTransaction.addOutput(P2PKOutput(microPaymentTransaction, serverKeySender));
		signAllInputs(microPaymentTransaction, inputAddress.createRedeemScript(), clientKey);

		SignedDTO dto = createMicroPaymentRequestDTO(clientKey, receiverKey, microPaymentTransaction);
		sendAndExpect4xxError(dto, "UTXO must be mined");
	}

	@Test public void microPayment_failsOnSoonLockedInputs() throws Exception {
		final ECKey clientKey = new ECKey();
		final long lockTime = Instant.now().plus(Duration.ofHours(20)).getEpochSecond();

		accountService.createAcount(clientKey);
		TimeLockedAddress inputAddress = accountService.createTimeLockedAddress(clientKey, lockTime)
			.getTimeLockedAddress();
		Transaction fundingTx = FakeTxBuilder.createFakeTxWithoutChangeAddress(params(), Coin.COIN,
			inputAddress.getAddress(params()));
		watchAndMineTransactions(fundingTx);

		Transaction microPaymentTransaction = new Transaction(params());
		microPaymentTransaction.addInput(fundingTx.getOutput(0));
		microPaymentTransaction.addOutput(anyP2PKOutput(microPaymentTransaction));

		SignedDTO dto = createMicroPaymentRequestDTO(clientKey, new ECKey(), microPaymentTransaction);
		sendAndExpect4xxError(dto,  "Inputs must be locked for 24 hours");
	}

	@Test public void microPayment_failsOnInputsBelongingToMultipleAccounts() throws Exception {
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
		Transaction fundingTx1 = FakeTxBuilder.createFakeTxWithoutChangeAddress(params(), Coin.COIN,
			addressClient1.getAddress(params()));
		Transaction fundingTx2 = FakeTxBuilder.createFakeTxWithoutChangeAddress(params(), Coin.COIN,
			addressClient2.getAddress(params()));
		watchAndMineTransactions(fundingTx1, fundingTx2);

		// Use them as inputs
		Transaction microPaymentTransaction = new Transaction(params());
		microPaymentTransaction.addInput(fundingTx1.getOutput(0));
		microPaymentTransaction.addInput(fundingTx2.getOutput(0));
		microPaymentTransaction.addOutput(anyP2PKOutput(microPaymentTransaction));

		SignedDTO dto = createMicroPaymentRequestDTO(clientKey1, new ECKey(), microPaymentTransaction);
		sendAndExpect4xxError(dto,  "Inputs must be from one account");
	}

	@Test public void microPayment_failsOnSignedByWrongAccount() throws Exception {
		final ECKey clientKey = new ECKey();

		accountService.createAcount(clientKey);
		TimeLockedAddress addressClient = accountService.createTimeLockedAddress(clientKey, validLockTime)
			.getTimeLockedAddress();
		Transaction fundingTx = FakeTxBuilder.createFakeTxWithoutChangeAddress(params(), Coin.COIN,
			addressClient.getAddress(params()));
		watchAndMineTransactions(fundingTx);

		Transaction microPaymentTransaction = new Transaction(params());
		microPaymentTransaction.addOutput(anyP2PKOutput(microPaymentTransaction));
		microPaymentTransaction.addInput(fundingTx.getOutput(0));


		SignedDTO dto = createMicroPaymentRequestDTO(new ECKey(), new ECKey(), microPaymentTransaction);
		sendAndExpect4xxError(dto,  "Request was not signed by owner of inputs");
	}

	@Test public void microPayment_failsOnNoOutputForServer() throws Exception {
		final ECKey senderKey = new ECKey();
		final ECKey receiverKey = new ECKey();

		accountService.createAcount(senderKey);
		TimeLockedAddress timeLockedAddress = accountService.createTimeLockedAddress(senderKey, validLockTime)
			.getTimeLockedAddress();
		Transaction fundingTx = FakeTxBuilder.createFakeTxWithoutChangeAddress(params(), Coin.COIN,
			timeLockedAddress.getAddress(params()));
		watchAndMineTransactions(fundingTx);

		Transaction microPaymentTransaction = new Transaction(params());
		microPaymentTransaction.addOutput(anyP2PKOutput(microPaymentTransaction));
		microPaymentTransaction.addInput(fundingTx.getOutput(0));
		signAllInputs(microPaymentTransaction, timeLockedAddress.createRedeemScript(), senderKey);

		SignedDTO dto = createMicroPaymentRequestDTO(senderKey, receiverKey, microPaymentTransaction);
		sendAndExpect4xxError(dto,  "Transaction must have exactly one output for server");
	}

	@Test public void microPayment_failsOnUnknownReceiver() throws Exception {
		final ECKey senderKey = new ECKey();
		final ECKey receiverKey = new ECKey();

		ECKey serverPublicKey = accountService.createAcount(senderKey);
		TimeLockedAddress timeLockedAddress = accountService.createTimeLockedAddress(senderKey, validLockTime)
			.getTimeLockedAddress();
		Transaction fundingTx = FakeTxBuilder.createFakeTxWithoutChangeAddress(params(), Coin.COIN,
			timeLockedAddress.getAddress(params()));
		watchAndMineTransactions(fundingTx);

		Transaction microPaymentTransaction = new Transaction(params());
		microPaymentTransaction.addOutput(P2PKOutput(microPaymentTransaction, serverPublicKey));
		microPaymentTransaction.addInput(fundingTx.getOutput(0));
		signAllInputs(microPaymentTransaction, timeLockedAddress.createRedeemScript(), senderKey);


		SignedDTO dto = createMicroPaymentRequestDTO(senderKey, receiverKey, microPaymentTransaction);
		sendAndExpect4xxError(dto,  "Receiver is unknown to server");
	}

	@Test public void microPayment_failsSendingToOneself() throws Exception {
		final ECKey senderKey = new ECKey();

		ECKey serverPublicKey = accountService.createAcount(senderKey);
		TimeLockedAddress timeLockedAddress = accountService.createTimeLockedAddress(senderKey, validLockTime)
			.getTimeLockedAddress();
		Transaction fundingTx = FakeTxBuilder.createFakeTxWithoutChangeAddress(params(), Coin.COIN,
			timeLockedAddress.getAddress(params()));
		watchAndMineTransactions(fundingTx);

		Transaction microPaymentTransaction = new Transaction(params());
		microPaymentTransaction.addInput(fundingTx.getOutput(0));
		microPaymentTransaction.addOutput(P2PKOutput(microPaymentTransaction, serverPublicKey));
		signAllInputs(microPaymentTransaction, timeLockedAddress.createRedeemScript(), senderKey);

		SignedDTO dto = createMicroPaymentRequestDTO(senderKey, senderKey, microPaymentTransaction);
		sendAndExpect4xxError(dto,  "Sender and receiver must be different");
	}

	@Test public void microPayment_failsWithMultipleChangeOutputs() throws Exception {
		final ECKey senderKey = new ECKey();
		final ECKey receiverKey = new ECKey();

		ECKey serverPublicKey = accountService.createAcount(senderKey);
		accountService.createAcount(receiverKey);

		TimeLockedAddress timeLockedAddress = accountService.createTimeLockedAddress(senderKey, validLockTime)
			.getTimeLockedAddress();
		Transaction fundingTx = FakeTxBuilder.createFakeTxWithoutChangeAddress(params(), Coin.COIN,
			timeLockedAddress.getAddress(params()));
		watchAndMineTransactions(fundingTx);

		Transaction microPaymentTransaction = new Transaction(params());
		microPaymentTransaction.addInput(fundingTx.getOutput(0));
		microPaymentTransaction.addOutput(P2PKOutput(microPaymentTransaction, serverPublicKey));
		microPaymentTransaction.addOutput(changeOutput(microPaymentTransaction, timeLockedAddress));
		microPaymentTransaction.addOutput(changeOutput(microPaymentTransaction, timeLockedAddress));
		signAllInputs(microPaymentTransaction, timeLockedAddress.createRedeemScript(), senderKey);

		SignedDTO dto = createMicroPaymentRequestDTO(senderKey, senderKey, microPaymentTransaction);
		sendAndExpect4xxError(dto,  "Cannot have multiple change outputs");
	}

	@Test public void microPayment_failsWhenChangeOutputIsSoonUnlocked() throws Exception {
		final ECKey senderKey = new ECKey();
		final ECKey receiverKey = new ECKey();

		ECKey serverPublicKey = accountService.createAcount(senderKey);
		accountService.createAcount(receiverKey);

		TimeLockedAddress inputAddress = accountService.createTimeLockedAddress(senderKey, validLockTime)
			.getTimeLockedAddress();
		TimeLockedAddress changeAddress = accountService.createTimeLockedAddress(senderKey,
			Instant.now().plus(Duration.ofHours(20)).getEpochSecond() ).getTimeLockedAddress();
		Transaction fundingTx = FakeTxBuilder.createFakeTxWithoutChangeAddress(params(), Coin.COIN,
			inputAddress.getAddress(params()));
		watchAndMineTransactions(fundingTx);

		Transaction microPaymentTransaction = new Transaction(params());
		microPaymentTransaction.addInput(fundingTx.getOutput(0));
		microPaymentTransaction.addOutput(P2PKOutput(microPaymentTransaction, serverPublicKey));
		microPaymentTransaction.addOutput(changeOutput(microPaymentTransaction, changeAddress));
		signAllInputs(microPaymentTransaction, inputAddress.createRedeemScript(), senderKey);

		SignedDTO dto = createMicroPaymentRequestDTO(senderKey, receiverKey, microPaymentTransaction);
		sendAndExpect4xxError(dto,  "Change cannot be send to address that is locked for less than 24 hours");
	}

	@Test public void microPayment_failsWhenTryingToSendToThirdParty() throws Exception {
		final ECKey senderKey = new ECKey();
		final ECKey receiverKey = new ECKey();
		ECKey serverPublicKey = accountService.createAcount(senderKey);
		accountService.createAcount(receiverKey);

		TimeLockedAddress inputAddress = accountService.createTimeLockedAddress(senderKey, validLockTime)
			.getTimeLockedAddress();
		Transaction fundingTx = FakeTxBuilder.createFakeTxWithoutChangeAddress(params(), Coin.COIN,
			inputAddress.getAddress(params()));
		watchAndMineTransactions(fundingTx);

		Transaction microPaymentTransaction = new Transaction(params());
		microPaymentTransaction.addInput(fundingTx.getOutput(0));
		microPaymentTransaction.addOutput(P2PKOutput(microPaymentTransaction, serverPublicKey));
		microPaymentTransaction.addOutput(anyP2PKOutput(microPaymentTransaction));
		signAllInputs(microPaymentTransaction, inputAddress.createRedeemScript(), senderKey);

		SignedDTO dto = createMicroPaymentRequestDTO(senderKey, receiverKey, microPaymentTransaction);
		sendAndExpect4xxError(dto,  "Sending to external addresses is not yet supported");
	}

	@Test public void microPayment_failsWhenChannelIsLocked() throws Exception {
		final ECKey senderKey = new ECKey();
		ECKey serverPublicKey = accountService.createAcount(senderKey);
		accountRepository.save(accountRepository.findByClientPublicKey(senderKey.getPubKey()).locked(true));

		final ECKey receiverKey = new ECKey();
		accountService.createAcount(receiverKey);

		TimeLockedAddress inputAddress = accountService.createTimeLockedAddress(senderKey, validLockTime)
			.getTimeLockedAddress();
		Transaction fundingTx = FakeTxBuilder.createFakeTxWithoutChangeAddress(params(), Coin.COIN,
			inputAddress.getAddress(params()));
		watchAndMineTransactions(fundingTx);

		Transaction microPaymentTransaction = new Transaction(params());
		microPaymentTransaction.addInput(fundingTx.getOutput(0));
		microPaymentTransaction.addOutput(P2PKOutput(microPaymentTransaction, serverPublicKey));
		signAllInputs(microPaymentTransaction, inputAddress.createRedeemScript(), senderKey);

		SignedDTO dto = createMicroPaymentRequestDTO(senderKey, receiverKey, microPaymentTransaction);
		sendAndExpect4xxError(dto,  "Channel is locked");
	}

	private static long validLockTime = Instant.now().plus(Duration.ofDays(30)).getEpochSecond();

	private void signAllInputs(Transaction tx, Script redeemScript, ECKey signingKey) {
		for (int i=0; i<tx.getInputs().size(); i++){
			TransactionSignature sig = tx.calculateSignature(i, signingKey, redeemScript.getProgram(), SigHash.ALL, false);
			tx.getInput(i).setScriptSig(new ScriptBuilder().data(sig.encodeToBitcoin()).build());
		}
	}

	private SignedDTO createMicroPaymentRequestDTO(ECKey from, ECKey to, Transaction tx) {
		MicroPaymentRequestDTO microPaymentRequestDTO = new MicroPaymentRequestDTO(
			DTOUtils.toHex(tx.bitcoinSerialize()), from.getPublicKeyAsHex(), to.getPublicKeyAsHex(), 100L, 0L);
		return DTOUtils.serializeAndSign(microPaymentRequestDTO, from);
	}

	private void sendAndExpect4xxError(SignedDTO dto, String expectedErrorMessage) throws Exception {
		MvcResult result = mockMvc.perform(post(URL_MICRO_PAYMENT)
			.contentType(APPLICATION_JSON)
			.content(DTOUtils.toJSON(dto)))
			.andExpect(status().is4xxClientError()).andReturn();
		String errorMessage = DTOUtils.fromJSON(result.getResponse().getContentAsString(), ErrorDTO.class).getError();
		assertThat(errorMessage, containsString(expectedErrorMessage));
	}

	private TransactionOutput anyP2PKOutput(Transaction forTransaction) {
		return new TransactionOutput(params(), forTransaction, Coin.valueOf(100), new ECKey().toAddress(params()));
	}
	private TransactionOutput P2PKOutput(Transaction forTransaction, ECKey to) {
		return new TransactionOutput(params(), forTransaction, Coin.valueOf(100), to.toAddress(params()));
	}
	private TransactionOutput changeOutput(Transaction forTransaction, TimeLockedAddress changeTo) {
		return new TransactionOutput(params(), forTransaction, Coin.valueOf(100), changeTo.getAddress(params()));
	}

	private void watchAndMineTransactions(Transaction... txs) throws PrunedException, BlockStoreException {
		for (Transaction tx : Arrays.asList(txs)) {
			watchAllOutputs(tx);
			mineTransaction(tx);
		}
	}

	private void watchAllOutputs(Transaction tx) {
		tx.getOutputs().forEach(output -> {
			if(output.getScriptPubKey().isSentToAddress())
				walletService.addWatching(output.getAddressFromP2PKHScript(params()));

			if(output.getScriptPubKey().isPayToScriptHash())
				walletService.addWatching(output.getAddressFromP2SH(params()));
			}
		);
	}
	private void mineTransaction(Transaction tx) throws BlockStoreException, PrunedException {
		Block lastBlock = walletService.blockChain().getChainHead().getHeader();
		Transaction spendTXClone = new Transaction(params(), tx.bitcoinSerialize());
		Block newBlock = FakeTxBuilder.makeSolvedTestBlock(lastBlock, spendTXClone);
		walletService.blockChain().add(newBlock);
	}

	// Creates a transaction that spends the given output. Uses the TimeLockedAddress and the keys to create a valid
	// scriptSig
	private Transaction createSpendingTransactionForOutput(TransactionOutput output, TimeLockedAddress usedTLA,
													ECKey clientKey, ECKey serverKey) {
		Transaction tx = new Transaction(params());
		tx.addInput(output);
		TransactionOutput someOutput =
			new TransactionOutput(params(), tx , Coin.SATOSHI, new ECKey().toAddress(params()));
		tx.addOutput(someOutput);
		TransactionSignature serverSig = tx.calculateSignature(0, clientKey, usedTLA.createRedeemScript(),
			SigHash.ALL, false);
		TransactionSignature clientSig = tx.calculateSignature(0, serverKey, usedTLA.createRedeemScript(),
			SigHash.ALL, false);
		Script scriptSig = usedTLA.createScriptSigBeforeLockTime(clientSig, serverSig);
		tx.getInput(0).setScriptSig(scriptSig);
		return tx;
	}

	@Test
	public void virtualPayment_failsOnEmpty() throws Exception {
		mockMvc.perform(post(URL_VIRTUAL_PAYMENT))
			.andExpect(status().is4xxClientError());
	}

}

