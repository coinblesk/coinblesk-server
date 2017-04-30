package com.coinblesk.server.controller;

import com.coinblesk.dto.ErrorDTO;
import com.coinblesk.dto.SignedDTO;
import com.coinblesk.dto.VirtualPaymentRequestDTO;
import com.coinblesk.server.config.AppConfig;
import com.coinblesk.server.service.AccountService;
import com.coinblesk.server.utilTest.CoinbleskTest;
import com.coinblesk.server.utils.DTOUtils;
import org.bitcoinj.core.ECKey;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.Instant;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @author Sebastian Stephan
 */
public class VirtualPaymentTest extends CoinbleskTest {
	public static final String URL_VIRTUAL_PAYMENT = "/payment/virtualpayment";
	private static MockMvc mockMvc;
	private static long validNonce()  { return Instant.now().toEpochMilli(); }
	@Autowired
	private WebApplicationContext webAppContext;
	@Autowired
	private AccountService accountService;
	@Autowired
	private AppConfig appConfig;

	@Before
	public void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(webAppContext).build();
	}

	@Test
	public void virtualPayment_failsOnEmpty() throws Exception {
		mockMvc.perform(post(URL_VIRTUAL_PAYMENT)).andExpect(status().is4xxClientError());
	}

	@Test
	public void virtualPayment_failsOnEmptyPayload() throws Exception {
		mockMvc.perform(post(URL_VIRTUAL_PAYMENT).contentType(APPLICATION_JSON).content("{}")).andExpect(status()
			.is4xxClientError());
	}

	@Test
	public void virtualPayment_failsOnUnknownSender() throws Exception {
		final ECKey senderKey = new ECKey();
		final ECKey receiverKey = new ECKey();
		accountService.createAcount(receiverKey);

		SignedDTO dto = DTOUtils.serializeAndSign(createDTO(senderKey, receiverKey, 100L, validNonce()), senderKey);
		sendAndExpect4xxError(dto, "Could not find user with public key");
	}

	private VirtualPaymentRequestDTO createDTO(ECKey sender, ECKey receiver, long value, long nonce) {
		return new VirtualPaymentRequestDTO(sender.getPublicKeyAsHex(), receiver.getPublicKeyAsHex(), value, validNonce());
	}

	private void sendAndExpect4xxError(SignedDTO dto, String expectedErrorMessage) throws Exception {
		MvcResult result = mockMvc.perform(post(URL_VIRTUAL_PAYMENT).contentType(APPLICATION_JSON).content(DTOUtils
			.toJSON(dto))).andExpect(status().is4xxClientError()).andReturn();
		String errorMessage = DTOUtils.fromJSON(result.getResponse().getContentAsString(), ErrorDTO.class).getError();
		assertThat(errorMessage, containsString(expectedErrorMessage));
	}

	private void sendAndExpect2xxSuccess(SignedDTO dto) throws Exception {
		mockMvc.perform(post(URL_VIRTUAL_PAYMENT).contentType(APPLICATION_JSON).content(DTOUtils.toJSON(dto))).andExpect
			(status().is2xxSuccessful());
	}

}
