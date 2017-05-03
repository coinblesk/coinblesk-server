package com.coinblesk.server.controller;

import com.coinblesk.dto.*;
import com.coinblesk.server.dao.AccountRepository;
import com.coinblesk.server.entity.Account;
import com.coinblesk.server.exceptions.UserNotFoundException;
import com.coinblesk.server.service.AccountService;
import com.coinblesk.server.utilTest.CoinbleskTest;
import com.coinblesk.util.DTOUtils;
import org.bitcoinj.core.ECKey;
import org.hamcrest.CoreMatchers;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import javax.persistence.EntityManager;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.*;

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
	private final static Logger LOG = LoggerFactory.getLogger(VirtualPaymentTest.class);
	public static final String URL_VIRTUAL_PAYMENT = "/payment/virtualpayment";
	private static MockMvc mockMvc;
	private static long validNonce()  { return Instant.now().toEpochMilli(); }
	private volatile boolean stop;
	@Autowired
	private WebApplicationContext webAppContext;
	@Autowired
	private AccountService accountService;
	@Autowired
	private AccountRepository accountRepository;

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
		accountService.createAccount(receiverKey);

		SignedDTO dto = DTOUtils.serializeAndSign(createDTO(senderKey, receiverKey, 100L, validNonce()), senderKey);
		sendAndExpect4xxError(dto, "Could not find user with public key");
	}

	@Test
	public void virtualPayment_failsOnUnknownReceiver() throws Exception {
		final ECKey senderKey = new ECKey();
		final ECKey receiverKey = new ECKey();
		accountService.createAccount(senderKey);
		giveUserBalance(senderKey,100L);

		SignedDTO dto = DTOUtils.serializeAndSign(createDTO(senderKey, receiverKey, 100L, validNonce()), senderKey);
		sendAndExpect4xxError(dto, "Could not find user with public key");
	}

	@Test
	public void virtualPayment_failsOnReplayedRequest() throws Exception {
		final ECKey senderKey = new ECKey();
		final ECKey receiverKey = new ECKey();
		accountService.createAccount(senderKey);
		accountService.createAccount(receiverKey);
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
		accountService.createAccount(senderKey);
		accountService.createAccount(receiverKey);
		giveUserBalance(senderKey,100L);

		SignedDTO dto = DTOUtils.serializeAndSign(createDTO(senderKey, receiverKey, 10L, validNonce()), receiverKey);
		sendAndExpect4xxError(dto, "Signature is not valid");
	}

	@Test
	public void virtualPayment_failsOnModifiedPayload() throws Exception {
		final ECKey senderKey = new ECKey();
		final ECKey receiverKey = new ECKey();
		accountService.createAccount(senderKey);
		accountService.createAccount(receiverKey);
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
		accountService.createAccount(senderKey);
		accountService.createAccount(receiverKey);
		giveUserBalance(senderKey,100L);

		SignedDTO dto = DTOUtils.serializeAndSign(createDTO(senderKey, receiverKey, 101L, validNonce()), senderKey);
		sendAndExpect4xxError(dto, "Insufficient funds");
	}

	@Test
	public void virtualPayment_failsOnZeroAmount() throws Exception {
		final ECKey senderKey = new ECKey();
		final ECKey receiverKey = new ECKey();
		accountService.createAccount(senderKey);
		accountService.createAccount(receiverKey);
		giveUserBalance(senderKey,100L);

		SignedDTO dto = DTOUtils.serializeAndSign(createDTO(senderKey, receiverKey, 0L, validNonce()), senderKey);
		sendAndExpect4xxError(dto, "Invalid amount");
	}

	@Test
	public void virtualPayment_failsOnNegativeAmount() throws Exception {
		final ECKey senderKey = new ECKey();
		final ECKey receiverKey = new ECKey();
		accountService.createAccount(senderKey);
		accountService.createAccount(receiverKey);
		giveUserBalance(senderKey,100L);
		giveUserBalance(receiverKey,100L);

		SignedDTO dto = DTOUtils.serializeAndSign(createDTO(senderKey, receiverKey, -50L, validNonce()), senderKey);
		sendAndExpect4xxError(dto, "Invalid amount");
	}

	@Test
	public void virtualPayment_increasesReceiverBalance() throws Exception {
		final ECKey senderKey = new ECKey();
		final ECKey receiverKey = new ECKey();
		accountService.createAccount(senderKey);
		accountService.createAccount(receiverKey);
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
		accountService.createAccount(senderKey);
		accountService.createAccount(receiverKey);
		giveUserBalance(senderKey,1337);
		giveUserBalance(receiverKey,42L);

		SignedDTO dto = DTOUtils.serializeAndSign(createDTO(senderKey, receiverKey, 13L, validNonce()), senderKey);
		sendAndExpect2xxSuccess(dto);
		assertThat(getBalance(senderKey), is(1324L));
	}

	@Test
	public void virtualPayment_cannotSendToSelf() throws Exception {
		final ECKey senderKey = new ECKey();
		accountService.createAccount(senderKey);
		giveUserBalance(senderKey,1337);

		SignedDTO dto = DTOUtils.serializeAndSign(createDTO(senderKey, senderKey, 13L, validNonce()), senderKey);
		sendAndExpect4xxError(dto, "The sender and receiver cannot be the same entities");
	}

	@Test
	public void virtualPayment_responseContainsCorrectBalances() throws Exception {
		final ECKey senderKey = new ECKey();
		final ECKey receiverKey = new ECKey();
		accountService.createAccount(senderKey);
		accountService.createAccount(receiverKey);
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
		ECKey serverKeySender = accountService.createAccount(senderKey);
		ECKey serverKeyReceiver = accountService.createAccount(receiverKey);
		giveUserBalance(senderKey,1337);
		giveUserBalance(receiverKey,42L);

		SignedDTO dto = DTOUtils.serializeAndSign(createDTO(senderKey, receiverKey, 100L, validNonce()), senderKey);
		MultiSignedDTO response = sendAndExpect2xxSuccess(dto);
		DTOUtils.validateSignature(response.getPayload(), response.getSignatureForSender(), serverKeySender);
		DTOUtils.validateSignature(response.getPayload(), response.getSignatureForReceiver(), serverKeyReceiver);

	}

	@Test
	@Transactional(propagation = Propagation.NOT_SUPPORTED) // Otherwise threads don't see changes...
	// See: https://www.javacodegeeks.com/2011/12/spring-pitfalls-transactional-tests.html
	public void loadTest() throws InterruptedException, UserNotFoundException, ExecutionException {
		final ECKey keyA = new ECKey();
		final ECKey keyB = new ECKey();
		final ECKey keyC = new ECKey();
		try {
			accountService.createAccount(keyA);
			accountService.createAccount(keyB);
			accountService.createAccount(keyC);
			giveUserBalance(keyA, 10000L);
			giveUserBalance(keyB, 10000L);
			giveUserBalance(keyC, 10000L);
			ExecutorService executor = Executors.newFixedThreadPool(4);
			stop = false;
			Callable<Void> stopper = () -> {
				Thread.sleep(10000);
				stop = true;
				return null;
			};
			executor.submit(stopper);
			List<Future<Exception>> res = executor.invokeAll(
				Arrays.asList(sender(keyA, keyB), sender(keyB, keyC), sender(keyC, keyA)));
			for (Future<Exception> f : res) {
				assertThat("There was an error in one of the senders", f.get(), CoreMatchers.nullValue());
			}

			long balanceA = accountService.getVirtualBalanceByClientPublicKey(keyA.getPubKey()).getBalance();
			long balanceB = accountService.getVirtualBalanceByClientPublicKey(keyB.getPubKey()).getBalance();
			long balanceC = accountService.getVirtualBalanceByClientPublicKey(keyC.getPubKey()).getBalance();
			assertThat(balanceA + balanceB + balanceC, is(30000L));
			System.out.println(balanceA);
			System.out.println(balanceB);
			System.out.println(balanceC);
		} finally {
			// Clean up since we had no transaction.
			accountService.deleteAccount(keyA);
			accountService.deleteAccount(keyB);
			accountService.deleteAccount(keyC);
		}
	}

	private Callable<Exception> sender(ECKey from, ECKey to) {
		Random rand = new Random();
		return () -> {
			while(!stop) {
				long value = rand.nextInt(99) + 1;
				SignedDTO dto = DTOUtils.serializeAndSign(createDTO(from, to, value, validNonce()), from);
				MvcResult res = mockMvc.perform(post(URL_VIRTUAL_PAYMENT).contentType(APPLICATION_JSON)
					.content(DTOUtils.toJSON(dto))).andReturn();
				if (res.getResponse().getStatus() == HttpStatus.OK.value()) {
					VirtualPaymentResponseDTO resDTO = DTOUtils.fromJSON(DTOUtils.fromBase64(
						DTOUtils.fromJSON(res.getResponse().getContentAsString(), MultiSignedDTO.class)
							.getPayload()), VirtualPaymentResponseDTO.class);
					LOG.info("Transfered: {}, Sender: {}, Receiver: {}", resDTO.getAmountTransfered(),
						resDTO.getNewBalanceSender(), resDTO.getNewBalanceReceiver());
				} else {
					ErrorDTO err = DTOUtils.fromJSON(res.getResponse().getContentAsString(), ErrorDTO.class);
					if (err.getError().contains("Insufficient funds")) {
						Thread.sleep(rand.nextInt(10));
					} else {
						return new RuntimeException(err.getError());
					}
				}
			}
			return null;
		};
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
