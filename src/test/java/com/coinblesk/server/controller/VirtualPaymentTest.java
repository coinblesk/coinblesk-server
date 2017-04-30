package com.coinblesk.server.controller;

import com.coinblesk.dto.*;
import com.coinblesk.server.config.AppConfig;
import com.coinblesk.server.dao.AccountRepository;
import com.coinblesk.server.entity.Account;
import com.coinblesk.server.exceptions.UserNotFoundException;
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

import javax.annotation.Signed;
import java.time.Instant;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
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
	private AccountRepository accountRepository;
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

	@Test
	public void virtualPayment_failsOnUnknownReceiver() throws Exception {
		final ECKey senderKey = new ECKey();
		final ECKey receiverKey = new ECKey();
		accountService.createAcount(senderKey);
		giveUserBalance(senderKey,100L);

		SignedDTO dto = DTOUtils.serializeAndSign(createDTO(senderKey, receiverKey, 100L, validNonce()), senderKey);
		sendAndExpect4xxError(dto, "Could not find user with public key");
	}

	@Test
	public void virtualPayment_failsOnReplayedRequest() throws Exception {
		final ECKey senderKey = new ECKey();
		final ECKey receiverKey = new ECKey();
		accountService.createAcount(senderKey);
		accountService.createAcount(receiverKey);
		giveUserBalance(senderKey,100L);

		SignedDTO dto = DTOUtils.serializeAndSign(createDTO(senderKey, receiverKey, 10L, validNonce()), senderKey);
		sendAndExpect2xxSuccess(dto);
		sendAndExpect4xxError(dto, "Invalid nonce"); // Same nonce
		SignedDTO dto2 = DTOUtils.serializeAndSign(createDTO(senderKey, receiverKey, 10L, validNonce()), senderKey);
		sendAndExpect2xxSuccess(dto2);
		sendAndExpect4xxError(dto, "Invalid nonce"); // Older nonce
	}

	@Test
	public void virtualPayment_failsOnSignedByWrongAccount() throws Exception {
		final ECKey senderKey = new ECKey();
		final ECKey receiverKey = new ECKey();
		accountService.createAcount(senderKey);
		accountService.createAcount(receiverKey);
		giveUserBalance(senderKey,100L);

		SignedDTO dto = DTOUtils.serializeAndSign(createDTO(senderKey, receiverKey, 10L, validNonce()), receiverKey);
		sendAndExpect4xxError(dto, "Signature is not valid");
	}

	@Test
	public void virtualPayment_failsOnModifiedPayload() throws Exception {
		final ECKey senderKey = new ECKey();
		final ECKey receiverKey = new ECKey();
		accountService.createAcount(senderKey);
		accountService.createAcount(receiverKey);
		giveUserBalance(senderKey,100L);

		VirtualPaymentRequestDTO payload = createDTO(senderKey, receiverKey, 10L, validNonce());
		SignedDTO dto = DTOUtils.serializeAndSign(payload, senderKey);
		VirtualPaymentRequestDTO payload_modified = createDTO(senderKey, receiverKey, 20L, validNonce());
		dto = new SignedDTO(DTOUtils.toBase64(DTOUtils.toJSON(payload_modified)), dto.getSignature());
		sendAndExpect4xxError(dto, "Signature is not valid");
	}

	@Test
	public void virtualPayment_failsOnInsufficientFunds() throws Exception {
		final ECKey senderKey = new ECKey();
		final ECKey receiverKey = new ECKey();
		accountService.createAcount(senderKey);
		accountService.createAcount(receiverKey);
		giveUserBalance(senderKey,100L);

		SignedDTO dto = DTOUtils.serializeAndSign(createDTO(senderKey, receiverKey, 101L, validNonce()), senderKey);
		sendAndExpect4xxError(dto, "Insufficient funds");
	}

	@Test
	public void virtualPayment_failsOnZeroAmount() throws Exception {
		final ECKey senderKey = new ECKey();
		final ECKey receiverKey = new ECKey();
		accountService.createAcount(senderKey);
		accountService.createAcount(receiverKey);
		giveUserBalance(senderKey,100L);

		SignedDTO dto = DTOUtils.serializeAndSign(createDTO(senderKey, receiverKey, 0L, validNonce()), senderKey);
		sendAndExpect4xxError(dto, "Invalid amount");
	}

	@Test
	public void virtualPayment_failsOnNegativeAmount() throws Exception {
		final ECKey senderKey = new ECKey();
		final ECKey receiverKey = new ECKey();
		accountService.createAcount(senderKey);
		accountService.createAcount(receiverKey);
		giveUserBalance(senderKey,100L);
		giveUserBalance(receiverKey,100L);

		SignedDTO dto = DTOUtils.serializeAndSign(createDTO(senderKey, receiverKey, -50L, validNonce()), senderKey);
		sendAndExpect4xxError(dto, "Invalid amount");
	}

	@Test
	public void virtualPayment_increasesReceiverBalance() throws Exception {
		final ECKey senderKey = new ECKey();
		final ECKey receiverKey = new ECKey();
		accountService.createAcount(senderKey);
		accountService.createAcount(receiverKey);
		giveUserBalance(senderKey,1337);
		giveUserBalance(receiverKey,42L);

		SignedDTO dto = DTOUtils.serializeAndSign(createDTO(senderKey, receiverKey, 13L, validNonce()), senderKey);
		sendAndExpect2xxSuccess(dto);
		assertThat(getBalance(receiverKey), is(55L));
	}

	@Test
	public void virtualPayment_decreasesSenderBalance() throws Exception {
		final ECKey senderKey = new ECKey();
		final ECKey receiverKey = new ECKey();
		accountService.createAcount(senderKey);
		accountService.createAcount(receiverKey);
		giveUserBalance(senderKey,1337);
		giveUserBalance(receiverKey,42L);

		SignedDTO dto = DTOUtils.serializeAndSign(createDTO(senderKey, receiverKey, 13L, validNonce()), senderKey);
		sendAndExpect2xxSuccess(dto);
		assertThat(getBalance(senderKey), is(1324L));
	}

	@Test
	public void virtualPayment_cannotSendToSelf() throws Exception {
		final ECKey senderKey = new ECKey();
		accountService.createAcount(senderKey);
		giveUserBalance(senderKey,1337);

		SignedDTO dto = DTOUtils.serializeAndSign(createDTO(senderKey, senderKey, 13L, validNonce()), senderKey);
		sendAndExpect4xxError(dto, "The sender and receiver cannot be the same entities");
	}

	@Test
	public void virtualPayment_responseContainsCorrectBalances() throws Exception {
		final ECKey senderKey = new ECKey();
		final ECKey receiverKey = new ECKey();
		accountService.createAcount(senderKey);
		accountService.createAcount(receiverKey);
		giveUserBalance(senderKey,1337);
		giveUserBalance(receiverKey,42L);

		SignedDTO dto = DTOUtils.serializeAndSign(createDTO(senderKey, receiverKey, 100L, validNonce()), senderKey);
		MultiSignedDTO resposne = sendAndExpect2xxSuccess(dto);
		VirtualPaymentResponseDTO responseDTO = DTOUtils.fromJSON(DTOUtils.fromBase64(resposne.getPayload()),
			VirtualPaymentResponseDTO.class);

		assertThat(responseDTO.getNewBalanceSender(), is(getBalance(senderKey)));
		assertThat(responseDTO.getNewBalanceReceiver(), is(getBalance(receiverKey)));
		assertThat(responseDTO.getAmountTransfered(), is(100L));
	}

	@Test
	public void virtualPayment_responseIsCorrectlySigned() throws Exception {
		final ECKey senderKey = new ECKey();
		final ECKey receiverKey = new ECKey();
		ECKey serverKeySender = accountService.createAcount(senderKey);
		ECKey serverKeyReceiver = accountService.createAcount(receiverKey);
		giveUserBalance(senderKey,1337);
		giveUserBalance(receiverKey,42L);

		SignedDTO dto = DTOUtils.serializeAndSign(createDTO(senderKey, receiverKey, 100L, validNonce()), senderKey);
		MultiSignedDTO response = sendAndExpect2xxSuccess(dto);
		DTOUtils.validateSignature(response.getPayload(), response.getSignatureForSender(), serverKeySender);
		DTOUtils.validateSignature(response.getPayload(), response.getSignatureForReceiver(), serverKeyReceiver);

	}

	private long getBalance(ECKey account) throws UserNotFoundException {
		return accountService.getVirtualBalanceByClientPublicKey(account.getPubKey()).getBalance();
	}

	private void giveUserBalance(ECKey user, long balance) {
		Account account = accountService.getByClientPublicKey(user.getPubKey());
		accountRepository.save(account.virtualBalance(balance));
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

	private MultiSignedDTO sendAndExpect2xxSuccess(SignedDTO dto) throws Exception {
		MvcResult res = mockMvc.perform(post(URL_VIRTUAL_PAYMENT).contentType(APPLICATION_JSON)
			.content(DTOUtils.toJSON(dto))).andExpect(status().is2xxSuccessful())
			.andReturn();
		return  DTOUtils.fromJSON(res.getResponse().getContentAsString(), MultiSignedDTO.class);
	}

}
