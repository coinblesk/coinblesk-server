package com.coinblesk.server.integration;

import static com.coinblesk.server.controller.MicroPaymentTest.URL_MICRO_PAYMENT;
import static com.coinblesk.server.controller.MicroPaymentTest.buildRequestDTO;
import static com.coinblesk.server.controller.MicroPaymentTest.createExternalPaymentRequestDTO;
import static com.coinblesk.server.controller.PaymentControllerTest.URL_CREATE_TIME_LOCKED_ADDRESS;
import static com.coinblesk.server.controller.PaymentControllerTest.URL_KEY_EXCHANGE;
import static com.coinblesk.server.controller.PayoutTest.URL_PAYOUT;
import static com.coinblesk.server.controller.VirtualPaymentTest.URL_VIRTUAL_PAYMENT;
import static com.coinblesk.server.integration.helper.RegtestHelper.generateBlock;
import static com.coinblesk.server.integration.helper.RegtestHelper.sendToAddress;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;

import com.coinblesk.dto.*;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.core.listeners.TransactionConfidenceEventListener;
import org.junit.Assume;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.coinblesk.bitcoin.TimeLockedAddress;
import com.coinblesk.dto.CreateAddressRequestDTO;
import com.coinblesk.dto.CreateAddressResponseDTO;
import com.coinblesk.dto.KeyExchangeRequestDTO;
import com.coinblesk.dto.KeyExchangeResponseDTO;
import com.coinblesk.dto.SignedDTO;
import com.coinblesk.dto.VirtualPaymentRequestDTO;
import com.coinblesk.enumerator.ForexCurrency;
import com.coinblesk.server.config.AppConfig;
import com.coinblesk.server.entity.Account;
import com.coinblesk.server.service.AccountService;
import com.coinblesk.server.service.ForexFiatService;
import com.coinblesk.server.service.MicropaymentService;
import com.coinblesk.server.service.WalletService;
import com.coinblesk.server.utilTest.PaymentChannel;
import com.coinblesk.util.DTOUtils;

/**
 * This test simulates a real, complete payment exchange.
 * Will only be run if environment variable INTEGRATION_TEST is set to true.
 * Is not run part of the test suite, as it requires a regtest node to be active on localhost.
 *
 * Activate this test by setting INTEGRATION_TEST=true environment variable.
 *
 *
 * @author Sebastian Stephan
 */
@SpringBootTest
@RunWith(SpringRunner.class)
@TestPropertySource(properties = {
	"spring.datasource.url: jdbc:h2:mem:testdb",
	"bitcoin.net: regtest",
	"coinblesk.maximumChannelAmountUSD:10"
})
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class MicroPaymentTest {
	private final static Logger LOG = LoggerFactory.getLogger(MicroPaymentTest.class);

	private final ECKey KEY_BOB      = new ECKey();
	private final ECKey KEY_ALICE    = new ECKey();
	private final ECKey KEY_MERCHANT = new ECKey();
	private NetworkParameters params;
	private Coin oneUSD;
	private Coin tenCents;

	private static MockMvc mockMvc;

	@Autowired
	private WebApplicationContext webAppContext;

	@Autowired
	private AccountService accountService;

	@Autowired
	private ForexFiatService forexService;

	@Autowired
	private MicropaymentService micropaymentService;

	@Autowired
	private WalletService walletService;

	@Autowired
	private AppConfig appConfig;

	@BeforeClass
	public static void beforeClass() throws Exception {
		// Only run class if environment variable is set, ignore otherwise
		Assume.assumeThat(System.getenv().get("INTEGRATION_TEST"), is(anyOf(equalTo("true"), equalTo("1"))));
	}

	@Before
	public void setUp() throws Exception {
		mockMvc = MockMvcBuilders.webAppContextSetup(webAppContext).build();
		params = appConfig.getNetworkParameters();
		tenCents = Coin.valueOf(BigDecimal.valueOf(100000000).divide(forexService.getExchangeRate(ForexCurrency.BTC, ForexCurrency.USD), BigDecimal.ROUND_UP).divide(BigDecimal.TEN).longValue());
		oneUSD = tenCents.multiply(10);
		generateBlock(101);
	}

	@Test
	public void fullTest() throws Exception {
		// Accounts for everyone
		createAccount(KEY_BOB);
		createAccount(KEY_ALICE);
		createAccount(KEY_MERCHANT);

		ECKey serverMicroPotKey = appConfig.getMicroPaymentPotPrivKey();

		// Server pot gets 100 dollar
		blockUntilAddressChanged(() -> {
			sendToAddress(serverMicroPotKey.toAddress(params).toBase58(), oneUSD.times(100));
			generateBlock(1);
		}, serverMicroPotKey.toAddress(params), 1);

		// Bob creates an address
		TimeLockedAddress addressBob = createAddress(KEY_BOB, inAMonth());
		PaymentChannel channelBob = new PaymentChannel(params, addressBob.getAddress(params), KEY_BOB, serverMicroPotKey);

		// Bob loads ~100 USD to his account
		blockUntilAddressChanged(() -> sendToAddress(addressBob.getAddress(params).toBase58(), oneUSD.times(100)),
			addressBob.getAddress(params), -1);
		channelBob.addInputs(addressBob, getUTXOsForAddress(addressBob));

		// Bob tries his first micro payment of 1 USD to Alice, which fails because the 100 USD transaction is not
		// yet mined
		channelBob.addToServerOutput(oneUSD);
		SignedDTO dto1 = buildRequestDTO(KEY_BOB, KEY_ALICE, channelBob.buildTx(), oneUSD.getValue());
		mockMvc.perform(post(URL_MICRO_PAYMENT).contentType(APPLICATION_JSON).content(DTOUtils.toJSON(dto1)))
			.andExpect(content().string(containsString("UTXO must be mined")));

		// Wait for block to be mined
		blockUntilAddressChanged(() -> generateBlock(1), addressBob.getAddress(params), 1);

		// Now that it is mined it should work
		sendAndExpectSuccess(URL_MICRO_PAYMENT, dto1);

		/* Check that we have the following state:
		+----------+-----------------+--------+-----------------------------+
		| Account  | Virtual Balance | Locked | PendingPaymentChannelAmount |
		+----------+-----------------+--------+-----------------------------+
		| Bob      | USD 0.00        | false  | USD 1.00                    |
		| Alice    | USD 1.00        | false  | USD 0.00                    |
		| Merchant | USD 0.00        | false  | USD 0.00                    |
		+----------+-----------------+--------+-----------------------------+
		| Pot                                 USD 100.00                    |
		+-------------------------------------------------------------------+ */
		assertAccountState(KEY_BOB, Coin.ZERO, false, oneUSD);
		assertAccountState(KEY_ALICE, oneUSD, false, Coin.ZERO);
		assertAccountState(KEY_MERCHANT, Coin.ZERO, false, Coin.ZERO);
		assertPotSize(oneUSD.multiply(100));

		// Alice uses the 1 USD virtual balance to pay the merchant 90 cents.
		VirtualPaymentRequestDTO req1 = new VirtualPaymentRequestDTO(KEY_ALICE.getPublicKeyAsHex(),
			KEY_MERCHANT.getPublicKeyAsHex(), tenCents.multiply(9).getValue(), now());
		SignedDTO dto2 = DTOUtils.serializeAndSign(req1, KEY_ALICE);
		sendAndExpectSuccess(URL_VIRTUAL_PAYMENT, dto2);
		/*
		+----------+-----------------+--------+-----------------------------+
		| Account  | Virtual Balance | Locked | PendingPaymentChannelAmount |
		+----------+-----------------+--------+-----------------------------+
		| Bob      | USD 0.00        | false  | USD 1.00                    |
		| Alice    | USD 0.10        | false  | USD 0.00                    |
		| Merchant | USD 0.90        | false  | USD 0.00                    |
		+----------+-----------------+--------+-----------------------------+
		| Pot                                 USD 100.00                    |
		+-------------------------------------------------------------------+ */
		assertAccountState(KEY_BOB, Coin.ZERO, false, oneUSD);
		assertAccountState(KEY_ALICE, tenCents.multiply(1), false, Coin.ZERO);
		assertAccountState(KEY_MERCHANT, tenCents.multiply(9), false, Coin.ZERO);
		assertPotSize(oneUSD.multiply(100));

		// Alice creates an account and sends loads it with USD 30 and a block is mined
		TimeLockedAddress addressAlice = createAddress(KEY_ALICE, inAMonth());
		blockUntilAddressChanged(() -> {
				sendToAddress(addressAlice.getAddress(params).toBase58(), oneUSD.times(30));
				generateBlock(1);
			}, addressAlice.getAddress(params), 1);
		PaymentChannel channelAlice = new PaymentChannel(params, addressAlice.getAddress(params), KEY_ALICE, serverMicroPotKey)
			.addInputs(addressAlice, getUTXOsForAddress(addressAlice));

		// Alice makes a micro payment of USD 8.00 to the merchant.
		channelAlice.addToServerOutput(oneUSD.multiply(8));
		SignedDTO dto3 = buildRequestDTO(KEY_ALICE, KEY_MERCHANT, channelAlice.buildTx(), oneUSD.multiply(8).getValue());
		sendAndExpectSuccess(URL_MICRO_PAYMENT, dto3);

		/*
		+----------+-----------------+--------+-----------------------------+
		| Account  | Virtual Balance | Locked | PendingPaymentChannelAmount |
		+----------+-----------------+--------+-----------------------------+
		| Bob      | USD 0.00        | false  | USD 1.00                    |
		| Alice    | USD 0.10        | false  | USD 8.00                    |
		| Merchant | USD 8.90        | false  | USD 0.00                    |
		+----------+-----------------+--------+-----------------------------+
		| Pot                                 USD 100.00                    |
		+-------------------------------------------------------------------+ */
		assertAccountState(KEY_BOB, Coin.ZERO, false, oneUSD);
		assertAccountState(KEY_ALICE, tenCents.multiply(1), false, oneUSD.multiply(8));
		assertAccountState(KEY_MERCHANT, tenCents.multiply(89), false, Coin.ZERO);
		assertPotSize(oneUSD.multiply(100));

		// Alice sends another 4 Dollar via micro payment to Bob. This should not work as it would bring the pending
		// channel amount > USD 10
		channelAlice.addToServerOutput(oneUSD.multiply(4));
		SignedDTO dto4 = buildRequestDTO(KEY_ALICE, KEY_MERCHANT, channelAlice.buildTx(), oneUSD.multiply(4).getValue());
		sendAndExpectError(URL_MICRO_PAYMENT, dto4);
		channelAlice.setServerOutput(oneUSD.multiply(8)); // reset

		// Alice closes the channel, by sending the 4 dollar directly to bob
		channelAlice.addOutput(addressBob.getAddress(params), oneUSD.multiply(4));
		SignedDTO dto5 = createExternalPaymentRequestDTO(KEY_ALICE, channelAlice.buildTx());
		sendAndExpectSuccess(URL_MICRO_PAYMENT, dto5);

		/*
		+----------+-----------------+--------+-----------------------------+
		| Account  | Virtual Balance | Locked | PendingPaymentChannelAmount |
		+----------+-----------------+--------+-----------------------------+
		| Bob      | USD 0.00        | false  | USD 1.00                    |
		| Alice    | USD 0.10        |  true  | USD 8.00                    |
		| Merchant | USD 8.90        | false  | USD 0.00                    |
		+----------+-----------------+--------+-----------------------------+
		| Pot                                 USD 100.00                    |
		+-------------------------------------------------------------------+ */
		assertAccountState(KEY_BOB, Coin.ZERO, false, oneUSD);
		assertAccountState(KEY_ALICE, tenCents.multiply(1), true, oneUSD.multiply(8));
		assertAccountState(KEY_MERCHANT, tenCents.multiply(89), false, Coin.ZERO);
		assertPotSize(oneUSD.multiply(100));

		blockUntilAddressChanged(() -> generateBlock(1), serverMicroPotKey.toAddress(params), 1);

		// Channel gets reopened after block, pot size increases
		/*
		+----------+-----------------+--------+-----------------------------+
		| Account  | Virtual Balance | Locked | PendingPaymentChannelAmount |
		+----------+-----------------+--------+-----------------------------+
		| Bob      | USD 0.00        | false  | USD 1.00                    |
		| Alice    | USD 0.10        | false  | USD 0.00                    |
		| Merchant | USD 8.90        | false  | USD 0.00                    |
		+----------+-----------------+--------+-----------------------------+
		| Pot                                 USD 108.00                    |
		+-------------------------------------------------------------------+ */
		assertAccountState(KEY_BOB, Coin.ZERO, false, oneUSD);
		assertAccountState(KEY_ALICE, tenCents.multiply(1), false, Coin.ZERO);
		assertAccountState(KEY_MERCHANT, tenCents.multiply(89), false, Coin.ZERO);
		assertPotSize(oneUSD.multiply(108));

		// Alice tries to pay out (but fails as 10 cents are not enough to cover fee)
		PayoutRequestDTO payoutRequest = new PayoutRequestDTO(KEY_ALICE.getPublicKeyAsHex(),
			addressAlice.getAddress(params).toBase58());
		SignedDTO signedPayoutRequest = DTOUtils.serializeAndSign(payoutRequest, KEY_ALICE);
		sendAndExpectError(URL_PAYOUT, signedPayoutRequest);

		// Everything stayed the same though
		assertAccountState(KEY_BOB, Coin.ZERO, false, oneUSD);
		assertAccountState(KEY_ALICE, tenCents, false, Coin.ZERO);
		assertAccountState(KEY_MERCHANT, tenCents.multiply(89), false, Coin.ZERO);
		assertPotSize(oneUSD.multiply(108));

		// Merchant pays out to third party address
		payoutRequest = new PayoutRequestDTO(KEY_MERCHANT.getPublicKeyAsHex(),
			new ECKey().toAddress(params).toBase58());
		signedPayoutRequest = DTOUtils.serializeAndSign(payoutRequest, KEY_MERCHANT);
		sendAndExpectSuccess(URL_PAYOUT, signedPayoutRequest);
		blockUntilAddressChanged(() -> generateBlock(1), serverMicroPotKey.toAddress(params), 1);
		/*
		+----------+-----------------+--------+-----------------------------+
		| Account  | Virtual Balance | Locked | PendingPaymentChannelAmount |
		+----------+-----------------+--------+-----------------------------+
		| Bob      | USD 0.00        | false  | USD 1.00                    |
		| Alice    | USD 0.10        | false  | USD 0.00                    |
		| Merchant | USD 0.00        | false  | USD 0.00                    |
		+----------+-----------------+--------+-----------------------------+
		| Pot                                 USD  99.10                    |
		+-------------------------------------------------------------------+ */
		assertAccountState(KEY_BOB, Coin.ZERO, false, oneUSD);
		assertAccountState(KEY_ALICE, tenCents, false, Coin.ZERO);
		assertAccountState(KEY_MERCHANT, Coin.ZERO, false, Coin.ZERO);
		assertPotSize(tenCents.multiply(991));

		// To make this complete, Bob sends Alice some coins so that she can pay out, then bob closes his channel
		// and Alice pays out
		channelBob.addToServerOutput(oneUSD);
		sendAndExpectSuccess(URL_MICRO_PAYMENT, buildRequestDTO(KEY_BOB, KEY_ALICE, channelBob.buildTx(), oneUSD.getValue()));
		sendAndExpectSuccess(URL_MICRO_PAYMENT, createExternalPaymentRequestDTO(KEY_BOB, channelBob.buildTx()));
		sendAndExpectSuccess(URL_PAYOUT, DTOUtils.serializeAndSign(
			new PayoutRequestDTO(KEY_ALICE.getPublicKeyAsHex(), addressAlice.getAddress(params).toBase58()), KEY_ALICE));
		blockUntilAddressChanged(() -> {}, addressAlice.getAddress(params), -1);
		blockUntilAddressChanged(() -> generateBlock(1), serverMicroPotKey.toAddress(params), 1);

		/*
		+----------+-----------------+--------+-----------------------------+
		| Account  | Virtual Balance | Locked | PendingPaymentChannelAmount |
		+----------+-----------------+--------+-----------------------------+
		| Bob      | USD 0.00        | false  | USD 0.00                    |
		| Alice    | USD 0.00        | false  | USD 0.00                    |
		| Merchant | USD 0.00        | false  | USD 0.00                    |
		+----------+-----------------+--------+-----------------------------+
		| Pot                                 USD 100.00                    |
		+-------------------------------------------------------------------+ */
		assertAccountState(KEY_BOB, Coin.ZERO, false, Coin.ZERO);
		assertAccountState(KEY_ALICE, Coin.ZERO, false, Coin.ZERO);
		assertAccountState(KEY_MERCHANT, Coin.ZERO, false, Coin.ZERO);
		assertPotSize(oneUSD.multiply(100));

	}

	private void assertPotSize(Coin value) {
		assertThat(micropaymentService.getMicroPaymentPotValue(), is(value));
	}

	private void assertAccountState(ECKey forAccount, Coin virtualBalance, boolean locked, Coin pendingChannelValue) {
		assertVirtualBalance(forAccount, virtualBalance);
		assertLockedChannel(forAccount, locked);
		assertPendingPaymentchannel(forAccount, pendingChannelValue);
	}

	private void assertVirtualBalance(ECKey forPublicKey, Coin value) {
		assertThat(accountService.getByClientPublicKey(forPublicKey.getPubKey()).virtualBalance(), is(value.getValue()));
	}
	private void assertLockedChannel(ECKey forPublicKey, boolean locked) {
		Account account = accountService.getByClientPublicKey(forPublicKey.getPubKey());
		assertThat(account.isLocked(), is(locked));
	}
	private void assertPendingPaymentchannel(ECKey forPublicKey, Coin value) {
		Account account = accountService.getByClientPublicKey(forPublicKey.getPubKey());
		assertThat(micropaymentService.getPendingChannelValue(account), is(value));
	}

	private void blockUntilAddressChanged(Runnable eventThatCausesChange, Address address, int depth) throws InterruptedException {
		CountDownLatch latchMined = new CountDownLatch(1);
		TransactionConfidenceEventListener listener2 = (wallet, tx) -> {
			tx.getOutputs().stream()
				.filter(out -> {
					return (Objects.equals(address, out.getAddressFromP2PKHScript(params)) ||
						Objects.equals(address, out.getAddressFromP2SH(params)))
						&& out.getParentTransactionDepthInBlocks() >= depth;
				})
				.findAny()
				.ifPresent(transactionOutput -> latchMined.countDown());
		};
		walletService.getWallet().addTransactionConfidenceEventListener(listener2);
		eventThatCausesChange.run();
		latchMined.await();
		Thread.sleep(100); // Allow for handlers
		walletService.getWallet().removeTransactionConfidenceEventListener(listener2);
	}

	private TransactionOutput[] getUTXOsForAddress(TimeLockedAddress address) {
		return walletService.getWallet().getWatchedOutputs(true).stream()
			.filter(o -> Objects.equals(o.getAddressFromP2SH(params), address.getAddress(params)))
			.toArray(TransactionOutput[]::new);
	}

	private static long inAMonth() {
		return Instant.now().plus(Duration.ofDays(30)).getEpochSecond();
	}

	private static long now() {
		return Instant.now().getEpochSecond();
	}

	private TimeLockedAddress createAddress(ECKey forClient, long lockTime) throws Exception {
		CreateAddressRequestDTO innerDTO = new CreateAddressRequestDTO(forClient.getPublicKeyAsHex(), lockTime);
		SignedDTO requestDTO = DTOUtils.serializeAndSign(innerDTO, forClient);

		String responseString = mockMvc.perform(post(URL_CREATE_TIME_LOCKED_ADDRESS).contentType(APPLICATION_JSON)
			.content(DTOUtils.toJSON(requestDTO))).andExpect(status().is2xxSuccessful()).andReturn().getResponse()
			.getContentAsString();

		CreateAddressResponseDTO response = DTOUtils.parseAndValidate(DTOUtils.fromJSON(responseString, SignedDTO.class),
			CreateAddressResponseDTO.class);
		return new TimeLockedAddress(DTOUtils.fromHex(response.getClientPublicKey()),
			DTOUtils.fromHex(response.getServerPublicKey()), response.getLockTime());
	}

	private ECKey createAccount(ECKey publicKey) throws Exception {
		KeyExchangeRequestDTO dto = new KeyExchangeRequestDTO(publicKey.getPublicKeyAsHex());
		MvcResult res = mockMvc.perform(post(URL_KEY_EXCHANGE).contentType(APPLICATION_JSON).content(DTOUtils.toJSON(dto)))
			.andExpect(status().is2xxSuccessful()).andReturn();
		KeyExchangeResponseDTO responseDTO = DTOUtils.fromJSON(res.getResponse().getContentAsString(),
			KeyExchangeResponseDTO.class);
		return DTOUtils.getECKeyFromHexPublicKey(responseDTO.getServerPublicKey());
	}

	private void sendAndExpectSuccess(String url, SignedDTO dto) throws Exception {
		mockMvc.perform(post(url).contentType(APPLICATION_JSON).content(DTOUtils.toJSON(dto)))
			.andExpect(status().is2xxSuccessful());
	}
	private void sendAndExpectError(String url, SignedDTO dto) throws Exception {
		String error = mockMvc.perform(post(url).contentType(APPLICATION_JSON).content(DTOUtils.toJSON(dto)))
			.andExpect(status().is4xxClientError()).andReturn().getResponse().getContentAsString();
		System.out.println(error);
	}

}
