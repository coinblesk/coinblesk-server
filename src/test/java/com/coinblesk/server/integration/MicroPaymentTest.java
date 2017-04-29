package com.coinblesk.server.integration;

import com.coinblesk.bitcoin.TimeLockedAddress;
import com.coinblesk.dto.*;
import com.coinblesk.server.config.AppConfig;
import com.coinblesk.server.entity.Account;
import com.coinblesk.server.service.AccountService;
import com.coinblesk.server.service.ForexService;
import com.coinblesk.server.service.MicropaymentService;
import com.coinblesk.server.service.WalletService;
import com.coinblesk.server.utils.DTOUtils;
import org.bitcoinj.core.*;
import org.bitcoinj.core.listeners.TransactionConfidenceEventListener;
import org.junit.*;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;

import static com.coinblesk.server.controller.MicroPaymentControllerTest.URL_MICRO_PAYMENT;
import static com.coinblesk.server.controller.MicroPaymentControllerTest.URL_VIRTUAL_PAYMENT;
import static com.coinblesk.server.controller.MicroPaymentControllerTest.createMicroPaymentRequestDTO;
import static com.coinblesk.server.controller.PaymentControllerTest.URL_CREATE_TIME_LOCKED_ADDRESS;
import static com.coinblesk.server.controller.PaymentControllerTest.URL_KEY_EXCHANGE;
import static com.coinblesk.server.integration.helper.RegtestHelper.generateBlock;
import static com.coinblesk.server.integration.helper.RegtestHelper.sendToAddress;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * This test simulates a real, complete payment exchange.
 * Will only be run if environment variable INTEGRATION_TEST is set to true.
 * Is not run part of the test suite, as it requires a regtest node to be active on localhost.
 *
 * Activate this test by setting INTEGRATION_TEST=true environment variable.
 *
 * TODO: refactor such that it runs also on testnet
 *
 * @author Sebastian Stephan
 */
@SpringBootTest
@RunWith(SpringRunner.class)
@TestPropertySource(properties = {
	"spring.datasource.url: jdbc:h2:mem:testdb",
	"bitcoin.net: regtest",
	"coinblesk.maximumChannelAmountUSD:10",
	"bitcoin.potprivkey:114122219063265213986617636129320327184744405155083861238127390715845993923252",
	"bitcoin.potCreationTime:1492214400"
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
	private ForexService forexService;

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
		tenCents = Coin.valueOf(BigDecimal.valueOf(100000000).divide(forexService.getExchangeRate("BTC", "USD"), BigDecimal.ROUND_UP).divide(BigDecimal.TEN).longValue());
		oneUSD = tenCents.multiply(10);
	}

	@Test
	public void fullTest() throws Exception {
		// Accounts for everyone
		createAccount(KEY_BOB);
		createAccount(KEY_ALICE);
		createAccount(KEY_MERCHANT);

		// Bob creates an address
		TimeLockedAddress addressBob = createAddress(KEY_BOB, inAMonth());

		// Bob loads ~100 USD to his account
		blockUntilAddressChanged(() -> sendToAddress(addressBob.getAddress(params).toBase58(), oneUSD.times(100)),
			addressBob.getAddress(params));

		// Bob tries his first micro payment of 1 USD to Alice, which fails because the 100 USD transaction is not
		// yet mined
		SignedDTO dto1 = createMicroPaymentRequestDTO(KEY_BOB, KEY_ALICE, oneUSD.getValue(), addressBob,
			params, getUTXOsForAddress(addressBob));
		mockMvc.perform(post(URL_MICRO_PAYMENT).contentType(APPLICATION_JSON).content(DTOUtils.toJSON(dto1)))
			.andExpect(content().string(containsString("UTXO must be mined")));

		// Wait for block to be mined
		blockUntilAddressChanged(() -> generateBlock(1), addressBob.getAddress(params));

		// Now that it is mined it should work
		sendAndExpectSuccess(URL_MICRO_PAYMENT, dto1);

		/* Check that we have the following state:
		+----------+-----------------+--------+-----------------------------+
		| Account  | Virtual Balance | Locked | PendingPaymentChannelAmount |
		+----------+-----------------+--------+-----------------------------+
		| Bob      | 0               | false  | 1 USD                       |
		| Alice    | 1 USD           | false  | null                        |
		| Merchant | 0               | false  | null                        |
		+----------+-----------------+--------+-----------------------------+
		*/
		assertAccountState(KEY_BOB, Coin.ZERO, false, oneUSD);
		assertAccountState(KEY_ALICE, oneUSD, false, Coin.ZERO);
		assertAccountState(KEY_MERCHANT, Coin.ZERO, false, Coin.ZERO);

		// Alice uses the 1 USD virtual balance to pay the merchant 30 cents.
		VirtualPaymentRequestDTO req1 = new VirtualPaymentRequestDTO(KEY_ALICE.getPublicKeyAsHex(),
			KEY_MERCHANT.getPublicKeyAsHex(), tenCents.multiply(3).getValue(), now());
		SignedDTO dto2 = DTOUtils.serializeAndSign(req1, KEY_ALICE);
		sendAndExpectSuccess(URL_VIRTUAL_PAYMENT, dto2);

		/*
		+----------+-----------------+--------+-----------------------------+
		| Account  | Virtual Balance | Locked | PendingPaymentChannelAmount |
		+----------+-----------------+--------+-----------------------------+
		| Bob      | 0               | false  | 1 USD                       |
		| Alice    | 0.70 USD        | false  | null                        |
		| Merchant | 0.30 USD        | false  | null                        |
		+----------+-----------------+--------+-----------------------------+
		 */
		assertAccountState(KEY_BOB, Coin.ZERO, false, oneUSD);
		assertAccountState(KEY_ALICE, tenCents.multiply(7), false, Coin.ZERO);
		assertAccountState(KEY_MERCHANT, tenCents.multiply(3), false, Coin.ZERO);

		// Alice creates an account and sends loads it with USD 30 and a block is mined
		TimeLockedAddress addressAlice = createAddress(KEY_ALICE, inAMonth());
		blockUntilAddressChanged(() -> sendToAddress(addressAlice.getAddress(params).toBase58(), oneUSD.times(30)),
			addressAlice.getAddress(params));
		blockUntilAddressChanged(() -> generateBlock(1), addressAlice.getAddress(params));

		// Alice makes a micro payment of USD 8 to the merchant.
		SignedDTO dto3 = createMicroPaymentRequestDTO(KEY_ALICE, KEY_MERCHANT, oneUSD.multiply(8).getValue(), addressAlice,
			params, getUTXOsForAddress(addressAlice));
		sendAndExpectSuccess(URL_MICRO_PAYMENT, dto3);

		/*
		+----------+-----------------+--------+-----------------------------+
		| Account  | Virtual Balance | Locked | PendingPaymentChannelAmount |
		+----------+-----------------+--------+-----------------------------+
		| Bob      | 0               | false  | 1 USD                       |
		| Alice    | 0.70 USD        | false  | USD 8                       |
		| Merchant | 8.30 USD        | false  | null                        |
		+----------+-----------------+--------+-----------------------------+ */
		assertAccountState(KEY_BOB, Coin.ZERO, false, oneUSD);
		assertAccountState(KEY_ALICE, tenCents.multiply(7), false, oneUSD.multiply(8));
		assertAccountState(KEY_MERCHANT, tenCents.multiply(83), false, Coin.ZERO);

		// Alice sends another 4 Dollar via micro payment to Bob. This should not work as it would bring the pending
		// channel amount > USD 10
		Thread.sleep(1001);
		SignedDTO dto4 = createMicroPaymentRequestDTO(KEY_ALICE, KEY_BOB, oneUSD.multiply(4).getValue(), addressAlice,
			params, oneUSD.multiply(8).getValue(), getUTXOsForAddress(addressAlice));
		sendAndExpectError(URL_MICRO_PAYMENT, dto4);

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

	private void blockUntilAddressChanged(Runnable eventThatCausesChange, Address address) throws InterruptedException {
		CountDownLatch latchMined = new CountDownLatch(1);
		TransactionConfidenceEventListener listener2 = (wallet, tx) -> {
			tx.getOutputs().stream()
				.filter(out -> {
					return Objects.equals(address, out.getAddressFromP2PKHScript(params)) ||
						Objects.equals(address, out.getAddressFromP2SH(params));
				})
				.findAny()
				.ifPresent(transactionOutput -> latchMined.countDown());
		};
		walletService.getWallet().addTransactionConfidenceEventListener(listener2);
		eventThatCausesChange.run();
		latchMined.await();
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

	private void createAccount(ECKey publicKey) throws Exception {
		KeyExchangeRequestDTO dto = new KeyExchangeRequestDTO(publicKey.getPublicKeyAsHex());
		mockMvc.perform(post(URL_KEY_EXCHANGE).contentType(APPLICATION_JSON).content(DTOUtils.toJSON(dto)))
			.andExpect(status().is2xxSuccessful());
	}

	private void sendAndExpectSuccess(String url, SignedDTO dto) throws Exception {
		mockMvc.perform(post(url).contentType(APPLICATION_JSON).content(DTOUtils.toJSON(dto)))
			.andExpect(status().is2xxSuccessful());
	}
	private void sendAndExpectError(String url, SignedDTO dto) throws Exception {
		mockMvc.perform(post(url).contentType(APPLICATION_JSON).content(DTOUtils.toJSON(dto)))
			.andExpect(status().is4xxClientError());
	}

}
