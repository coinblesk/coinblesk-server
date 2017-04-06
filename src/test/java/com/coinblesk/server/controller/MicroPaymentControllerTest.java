package com.coinblesk.server.controller;

import com.coinblesk.bitcoin.TimeLockedAddress;
import com.coinblesk.server.config.AppConfig;
import com.coinblesk.server.dto.ErrorDTO;
import com.coinblesk.server.dto.MicroPaymentRequestDTO;
import com.coinblesk.server.dto.SignedDTO;
import com.coinblesk.server.service.WalletService;
import com.coinblesk.server.utilTest.CoinbleskTest;
import com.coinblesk.server.utilTest.FakeTxBuilder;
import com.coinblesk.server.utils.DTOUtils;
import org.bitcoinj.core.*;
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

import static org.hamcrest.CoreMatchers.containsString;
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

	@Autowired
	private WebApplicationContext webAppContext;

	@Autowired
	WalletService walletService;

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

	@Test public void microPayment_failsOnNoTransaction() throws Exception {
		ECKey clientKey = new ECKey();
		MicroPaymentRequestDTO microPaymentRequestDTO = new MicroPaymentRequestDTO(
			"", clientKey.getPublicKeyAsHex(), new ECKey().getPublicKeyAsHex(), 10L);
		SignedDTO dto = DTOUtils.serializeAndSign(microPaymentRequestDTO, clientKey);
		sendAndExpect4xxError("/payment/micropayment", dto, "");
	}

	@Test public void microPayment_failsOnEmptyTransaction() throws Exception {
		ECKey clientKey = new ECKey();
		Transaction microPaymentTransaction = new Transaction(params());
		SignedDTO dto = createMicroPaymentRequestDTO(clientKey, new ECKey(), microPaymentTransaction, 100L);
		sendAndExpect4xxError("/payment/micropayment", dto, "Invalid transaction");
	}

	@Test public void microPayment_failsOnUnknownUTXOs() throws Exception {
		ECKey clientKey = new ECKey();
		Transaction microPaymentTransaction = FakeTxBuilder.createFakeTx(params());
		SignedDTO dto = createMicroPaymentRequestDTO(clientKey, new ECKey(), microPaymentTransaction, 100L);
		sendAndExpect4xxError("/payment/micropayment", dto,  "Transaction spends unknown UTXOs");
	}

	@Test public void microPayment_failsOnWrongAddressType() throws Exception {
		ECKey clientKey = new ECKey();
		Transaction inputTx1 = FakeTxBuilder.createFakeTx(params()); // Creates a P2PKHash output, not what we want
		Transaction inputTx2 = FakeTxBuilder.createFakeP2SHTx(params()); // Creates a P2SH output, this would be ok

		Transaction microPaymentTransaction = new Transaction(params());
		microPaymentTransaction.addOutput(someP2PKHOutput(microPaymentTransaction));
		microPaymentTransaction.addInput(inputTx1.getOutput(0));
		microPaymentTransaction.addInput(inputTx2.getOutput(0));

		// Making sure the wallet watches the transactions used as inputs and they are mined
		watchAllOutputs(inputTx1);
		mineTransaction(inputTx1);
		watchAllOutputs(inputTx2);
		mineTransaction(inputTx2);

		SignedDTO dto = createMicroPaymentRequestDTO(clientKey, new ECKey(), microPaymentTransaction, 100L);
		sendAndExpect4xxError("/payment/micropayment", dto,  "Transaction must spent P2SH addresses");
	}

	@Test public void microPayment_failsOnUnknownTLAInputs() throws Exception {
		final ECKey clientKey = new ECKey();
		final ECKey serverKey = new ECKey();
		final long lockTime = Instant.now().plus(Duration.ofDays(30)).getEpochSecond();

		// Create a tla but without registering it in any way with the server
		TimeLockedAddress tla = new TimeLockedAddress(clientKey.getPubKey(), serverKey.getPubKey(), lockTime);
		Transaction tlaTxToSpend = FakeTxBuilder.createFakeTxWithoutChangeAddress(params(), Coin.COIN,
			tla.getAddress(params()));

		Transaction microPaymentTransaction = new Transaction(params());
		microPaymentTransaction.addOutput(someP2PKHOutput(microPaymentTransaction));
		microPaymentTransaction.addInput(tlaTxToSpend.getOutput(0));

		// Making sure the wallet watches the transaction used as input and it is are mined
		watchAllOutputs(tlaTxToSpend);
		mineTransaction(tlaTxToSpend);

		SignedDTO dto = createMicroPaymentRequestDTO(clientKey, new ECKey(), microPaymentTransaction, 100l);
		sendAndExpect4xxError("/payment/micropayment", dto,  "Used TLA inputs are not known to server");
	}

	private SignedDTO createMicroPaymentRequestDTO(ECKey from, ECKey to, Transaction tx, long amount) {
		MicroPaymentRequestDTO microPaymentRequestDTO = new MicroPaymentRequestDTO(
			DTOUtils.toHex(tx.bitcoinSerialize()), from.getPublicKeyAsHex(), to.getPublicKeyAsHex(), amount);
		return DTOUtils.serializeAndSign(microPaymentRequestDTO, from);
	}

	private void sendAndExpect4xxError(String url, SignedDTO dto, String expectedErrorMessage) throws Exception {
		MvcResult result = mockMvc.perform(post(url)
			.contentType(APPLICATION_JSON)
			.content(DTOUtils.toJSON(dto)))
			.andExpect(status().is4xxClientError()).andReturn();
		String errorMessage = DTOUtils.fromJSON(result.getResponse().getContentAsString(), ErrorDTO.class).getError();
		assertThat(errorMessage, containsString(expectedErrorMessage));
	}

	private TransactionOutput someP2PKHOutput(Transaction forTransaction) {
		return new TransactionOutput(params(), forTransaction, Coin.valueOf(100), new ECKey().toAddress(params()));
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

	@Test
	public void virtualPayment_failsOnEmpty() throws Exception {
		mockMvc.perform(post(URL_VIRTUAL_PAYMENT))
			.andExpect(status().is4xxClientError());
	}

}

