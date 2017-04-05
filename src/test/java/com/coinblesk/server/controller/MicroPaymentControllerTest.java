package com.coinblesk.server.controller;

import com.coinblesk.server.config.AppConfig;
import com.coinblesk.server.dto.ErrorDTO;
import com.coinblesk.server.dto.MicroPaymentRequestDTO;
import com.coinblesk.server.dto.SignedDTO;
import com.coinblesk.server.utilTest.CoinbleskTest;
import com.coinblesk.server.utilTest.FakeTxBuilder;
import com.coinblesk.server.utils.DTOUtils;
import io.jsonwebtoken.lang.Assert;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionInput;
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
	private AppConfig appConfig;

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
		Transaction tx = new Transaction(appConfig.getNetworkParameters());

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

	@Test
	public void virtualPayment_failsOnEmpty() throws Exception {
		mockMvc.perform(post(URL_VIRTUAL_PAYMENT))
			.andExpect(status().is4xxClientError());
	}

}

