package com.coinblesk.server.controller;

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

		mockMvc.perform(post(URL_MICRO_PAYMENT)
			.contentType(APPLICATION_JSON)
			.content(DTOUtils.toJSON(dto)))
			.andExpect(status().is4xxClientError());

	}

	@Test public void microPayment_failsOnEmptyTransaction() throws Exception {
		ECKey clientKey = new ECKey();
		Transaction tx = new Transaction(params());

		MicroPaymentRequestDTO microPaymentRequestDTO = new MicroPaymentRequestDTO(
			DTOUtils.toHex(tx.bitcoinSerialize()), clientKey.getPublicKeyAsHex(), new ECKey().getPublicKeyAsHex(), 10L);
		SignedDTO dto = DTOUtils.serializeAndSign(microPaymentRequestDTO, clientKey);

		MvcResult result = mockMvc.perform(post(URL_MICRO_PAYMENT)
			.contentType(APPLICATION_JSON)
			.content(DTOUtils.toJSON(dto)))
			.andExpect(status().is4xxClientError()).andReturn();

		String errorMessage = DTOUtils.fromJSON(result.getResponse().getContentAsString(), ErrorDTO.class).getError();
		assertThat(errorMessage, containsString("Invalid transaction"));
	}

	@Test public void microPayment_failsOnUnknownUTXOs() throws Exception {
		ECKey clientKey = new ECKey();
		Transaction spentTx = FakeTxBuilder.createFakeTx(params());

		MicroPaymentRequestDTO microPaymentRequestDTO = new MicroPaymentRequestDTO(
			DTOUtils.toHex(spentTx.bitcoinSerialize()), clientKey.getPublicKeyAsHex(), new ECKey().getPublicKeyAsHex(), 10L);
		SignedDTO dto = DTOUtils.serializeAndSign(microPaymentRequestDTO, clientKey);

		MvcResult result = mockMvc.perform(post("/payment/micropayment")
			.contentType(APPLICATION_JSON)
			.content(DTOUtils.toJSON(dto)))
			.andExpect(status().is4xxClientError()).andReturn();

		String errorMessage = DTOUtils.fromJSON(result.getResponse().getContentAsString(), ErrorDTO.class).getError();
		assertThat(errorMessage, containsString("Transaction spends unknown UTXOs"));
	}

	@Test public void microPayment_failsOnWrongAddressType() throws Exception {
		ECKey clientKey = new ECKey();
		Transaction spent1 = FakeTxBuilder.createFakeTx(params()); // Creates a P2PKHash output, not what we want
		Transaction spent2 = FakeTxBuilder.createFakeP2SHTx(params()); // Creates a P2SH output, this would be ok

		Transaction t = new Transaction(params());
		t.addOutput(new TransactionOutput(params(), t, Coin.valueOf(100), new ECKey().toAddress(params())));
		t.addInput(spent1.getOutput(0));
		t.addInput(spent2.getOutput(0));

		// Making sure the wallet watches the transaction used as inputs and they are mined
		watchAllOutputs(spent1);
		mineTransaction(spent1);
		watchAllOutputs(spent2);
		mineTransaction(spent2);

		MicroPaymentRequestDTO microPaymentRequestDTO = new MicroPaymentRequestDTO(
			DTOUtils.toHex(t.bitcoinSerialize()), clientKey.getPublicKeyAsHex(), new ECKey().getPublicKeyAsHex(), 10L);
		SignedDTO dto = DTOUtils.serializeAndSign(microPaymentRequestDTO, clientKey);

		MvcResult result = mockMvc.perform(post("/payment/micropayment")
			.contentType(APPLICATION_JSON)
			.content(DTOUtils.toJSON(dto)))
			.andExpect(status().is4xxClientError()).andReturn();

		String errorMessage = DTOUtils.fromJSON(result.getResponse().getContentAsString(), ErrorDTO.class).getError();
		assertThat(errorMessage, containsString("Transaction must spent P2SH addresses"));
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

