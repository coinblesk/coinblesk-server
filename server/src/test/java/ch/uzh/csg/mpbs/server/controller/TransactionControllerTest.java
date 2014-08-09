package ch.uzh.csg.mpbs.server.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.KeyPair;

import javax.servlet.http.HttpSession;

import org.codehaus.jackson.map.ObjectMapper;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import ch.uzh.csg.mbps.customserialization.Currency;
import ch.uzh.csg.mbps.customserialization.DecoderFactory;
import ch.uzh.csg.mbps.customserialization.PKIAlgorithm;
import ch.uzh.csg.mbps.customserialization.PaymentRequest;
import ch.uzh.csg.mbps.customserialization.PaymentResponse;
import ch.uzh.csg.mbps.customserialization.ServerPaymentRequest;
import ch.uzh.csg.mbps.customserialization.ServerPaymentResponse;
import ch.uzh.csg.mbps.customserialization.ServerResponseStatus;
import ch.uzh.csg.mbps.keys.CustomKeyPair;
import ch.uzh.csg.mbps.responseobject.CreateTransactionTransferObject;
import ch.uzh.csg.mbps.responseobject.CustomResponseObject;
import ch.uzh.csg.mbps.responseobject.GetHistoryTransferObject;
import ch.uzh.csg.mbps.server.dao.UserAccountDAO;
import ch.uzh.csg.mbps.server.domain.PayOutTransaction;
import ch.uzh.csg.mbps.server.domain.UserAccount;
import ch.uzh.csg.mbps.server.security.KeyHandler;
import ch.uzh.csg.mbps.server.service.TransactionService;
import ch.uzh.csg.mbps.server.service.UserAccountService;
import ch.uzh.csg.mbps.server.util.Constants;
import ch.uzh.csg.mbps.server.util.exceptions.EmailAlreadyExistsException;
import ch.uzh.csg.mbps.server.util.exceptions.InvalidEmailException;
import ch.uzh.csg.mbps.server.util.exceptions.InvalidUsernameException;
import ch.uzh.csg.mbps.server.util.exceptions.UserAccountNotFoundException;
import ch.uzh.csg.mbps.server.util.exceptions.UsernameAlreadyExistsException;
import ch.uzh.csg.mbps.util.Converter;

import com.azazar.bitcoin.jsonrpcclient.BitcoinException;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {
		"file:src/main/webapp/WEB-INF/applicationContext.xml",
		"file:src/main/webapp/WEB-INF/mvc-dispatcher-servlet.xml",
		"file:src/main/webapp/WEB-INF/spring-security.xml" })
@WebAppConfiguration
public class TransactionControllerTest {
	
	@Autowired
	private WebApplicationContext webAppContext;
	
	@Autowired
	private FilterChainProxy springSecurityFilterChain;
	
	private static MockMvc mockMvc;
	
	private static boolean initialized = false;
	private static UserAccount test1_1;
	private static UserAccount test1_2;
	private static UserAccount test2_1;
	private static UserAccount test2_2;
	private static UserAccount test3_1;
	private static UserAccount test3_2;
	private static UserAccount test4_1;
	private static UserAccount test4_2;
	private static UserAccount test5_1;
	private static UserAccount test6_1;
	private static UserAccount test6_2;
	private static UserAccount test7_1;
	private static UserAccount test8_1;
	
	private String password = "asdf";
	
	private static final BigDecimal TRANSACTION_AMOUNT = new BigDecimal(10.1).setScale(8, RoundingMode.HALF_UP);
	
	@Before
	public void setUp() throws Exception {
		UserAccountService.enableTestingMode();
		
		if (!initialized) {
			mockMvc = MockMvcBuilders.webAppContextSetup(webAppContext).addFilter(springSecurityFilterChain).build();
			test1_1 = new UserAccount("test1_1", "test1_1@bitcoin.csg.uzh.ch", password);
			test1_2 = new UserAccount("test1_2", "test1_2@bitcoin.csg.uzh.ch", password);
			test2_1 = new UserAccount("test2_1", "test2_1@bitcoin.csg.uzh.ch", password);
			test2_2 = new UserAccount("test2_2", "test2_2@bitcoin.csg.uzh.ch", password);
			test3_1 = new UserAccount("test3_1", "test3_1@bitcoin.csg.uzh.ch", password);
			test3_2 = new UserAccount("test3_2", "test3_2@bitcoin.csg.uzh.ch", password);
			test4_1 = new UserAccount("test4_1", "test4_1@bitcoin.csg.uzh.ch", password);
			test4_2 = new UserAccount("test4_2", "test4_2@bitcoin.csg.uzh.ch", password);
			test5_1 = new UserAccount("test5_1", "test5_1@bitcoin.csg.uzh.ch", password);
			test6_1 = new UserAccount("test6_1", "test6_1@bitcoin.csg.uzh.ch", password);
			test6_2 = new UserAccount("test6_2", "test6_2@bitcoin.csg.uzh.ch", password);
			test7_1 = new UserAccount("test7_1", "test7_1@bitcoin.csg.uzh.ch", password);
			test8_1 = new UserAccount("test8_1", "test8_1@bitcoin.csg.uzh.ch", password);
			
			KeyPair keypair = KeyHandler.generateKeyPair();
			
			Constants.SERVER_KEY_PAIR = new CustomKeyPair(PKIAlgorithm.DEFAULT.getCode(), (byte) 1, KeyHandler.encodePublicKey(keypair.getPublic()), KeyHandler.encodePrivateKey(keypair.getPrivate()));
				
			initialized = true;
		}
	}
	
	@After
	public void tearDown() {
		UserAccountService.disableTestingMode();
	}
	
	@Test
	public void testCreateTransaction_failNotAuthenticated() throws Exception {
		assertTrue(UserAccountService.getInstance().createAccount(test1_1));
		assertTrue(UserAccountService.getInstance().createAccount(test1_2));
		
		UserAccount payerAccount = UserAccountService.getInstance().getByUsername(test1_1.getUsername());
		UserAccount payeeAccount  = UserAccountService.getInstance().getByUsername(test1_2.getUsername());
		payerAccount.setEmailVerified(true);
		payerAccount.setBalance(TRANSACTION_AMOUNT.add(BigDecimal.ONE));
		UserAccountDAO.updateAccount(payerAccount);
		payeeAccount.setEmailVerified(true);
		payeeAccount.setBalance(TRANSACTION_AMOUNT);
		UserAccountDAO.updateAccount(payeeAccount);
		
		KeyPair keyPairPayer = KeyHandler.generateKeyPair();
		
		byte keyNumberPayer = UserAccountService.getInstance().saveUserPublicKey(payerAccount.getId(), PKIAlgorithm.DEFAULT, KeyHandler.encodePublicKey(keyPairPayer.getPublic()));
		
		PaymentRequest paymentRequestPayer = new PaymentRequest(
				PKIAlgorithm.DEFAULT, 
				keyNumberPayer, 
				payerAccount.getUsername(), 
				payeeAccount.getUsername(), 
				Currency.BTC, 
				Converter.getLongFromBigDecimal(TRANSACTION_AMOUNT),
				System.currentTimeMillis());
		paymentRequestPayer.sign(keyPairPayer.getPrivate());
		
		ServerPaymentRequest request = new ServerPaymentRequest(paymentRequestPayer);
		CreateTransactionTransferObject ctto = new CreateTransactionTransferObject(request);
		
		ObjectMapper mapper = new ObjectMapper();
		String asString = mapper.writeValueAsString(ctto);
		
		mockMvc.perform(post("/transaction/create").secure(false).contentType(MediaType.APPLICATION_JSON).content(asString))
				.andExpect(status().isUnauthorized());
	}
	
	@Test
	public void testSendMoney() throws Exception {
		assertTrue(UserAccountService.getInstance().createAccount(test2_1));
		assertTrue(UserAccountService.getInstance().createAccount(test2_2));
		
		UserAccount payerAccount = UserAccountService.getInstance().getByUsername(test2_1.getUsername());
		UserAccount payeeAccount  = UserAccountService.getInstance().getByUsername(test2_2.getUsername());
		payerAccount.setEmailVerified(true);
		payerAccount.setBalance(TRANSACTION_AMOUNT.add(BigDecimal.ONE));
		UserAccountDAO.updateAccount(payerAccount);
		payeeAccount.setEmailVerified(true);
		payeeAccount.setBalance(TRANSACTION_AMOUNT);
		UserAccountDAO.updateAccount(payeeAccount);
		
		KeyPair payerKeyPair = KeyHandler.generateKeyPair();
	
		byte keyNumberPayer = UserAccountService.getInstance().saveUserPublicKey(payerAccount.getId(), PKIAlgorithm.DEFAULT, KeyHandler.encodePublicKey(payerKeyPair.getPublic()));
		
		PaymentRequest paymentRequestPayer = new PaymentRequest(
				PKIAlgorithm.DEFAULT, 
				keyNumberPayer, 
				payerAccount.getUsername(), 
				payeeAccount.getUsername(), 
				Currency.BTC, 
				Converter.getLongFromBigDecimal(TRANSACTION_AMOUNT),
				Currency.CHF, 
				Converter.getLongFromBigDecimal(new BigDecimal("0.5")), 
				System.currentTimeMillis());

		paymentRequestPayer.sign(payerKeyPair.getPrivate());
		
		ServerPaymentRequest spr = new ServerPaymentRequest(paymentRequestPayer);
		
		CreateTransactionTransferObject ctto = new CreateTransactionTransferObject(spr);
		
		BigDecimal payerBalanceBefore = payerAccount.getBalance();
		BigDecimal payeeBalanceBefore = payeeAccount.getBalance();
		
		ObjectMapper mapper = new ObjectMapper();
		String asString = mapper.writeValueAsString(ctto);
		
		HttpSession session = loginAndGetSession(test2_1.getUsername(), password);
		
		MvcResult mvcResult = mockMvc.perform(post("/transaction/create").secure(false).session((MockHttpSession) session).contentType(MediaType.APPLICATION_JSON).content(asString))
				.andExpect(status().isOk())
				.andReturn();
		
		CustomResponseObject result = mapper.readValue(mvcResult.getResponse().getContentAsString(), CustomResponseObject.class);
		
		byte[] serverPaymentResponseEncoded = result.getServerPaymentResponse();
		ServerPaymentResponse serverPaymentResponse = DecoderFactory.decode(ServerPaymentResponse.class, serverPaymentResponseEncoded);
	
		UserAccount payerAccountUpdated = UserAccountService.getInstance().getById(payerAccount.getId());
		UserAccount payeeAccountUpdated = UserAccountService.getInstance().getById(payeeAccount.getId());
		
		assertTrue(result.isSuccessful());
		assertEquals(ServerResponseStatus.SUCCESS, serverPaymentResponse.getPaymentResponsePayer().getStatus());
		
		assertEquals(0, payerBalanceBefore.subtract(TRANSACTION_AMOUNT).compareTo(payerAccountUpdated.getBalance()));
		assertEquals(0, payeeBalanceBefore.add(TRANSACTION_AMOUNT).compareTo(payeeAccountUpdated.getBalance()));
		
		assertTrue(serverPaymentResponse.getPaymentResponsePayer().verify(KeyHandler.decodePublicKey(Constants.SERVER_KEY_PAIR.getPublicKey())));
		
		PaymentResponse responsePayer = serverPaymentResponse.getPaymentResponsePayer();
		
		assertEquals(paymentRequestPayer.getAmount(), responsePayer.getAmount());
		assertEquals(paymentRequestPayer.getUsernamePayer(), responsePayer.getUsernamePayer());
		assertEquals(paymentRequestPayer.getUsernamePayee(), responsePayer.getUsernamePayee());
	}
	
	@Test
	public void testSendMoney_failNotAuthenticatedUser() throws Exception {
		assertTrue(UserAccountService.getInstance().createAccount(test3_1));
		assertTrue(UserAccountService.getInstance().createAccount(test3_2));
		
		UserAccount payerAccount = UserAccountService.getInstance().getByUsername(test3_1.getUsername());
		UserAccount payeeAccount  = UserAccountService.getInstance().getByUsername(test3_2.getUsername());
		payerAccount.setEmailVerified(true);
		payerAccount.setBalance(TRANSACTION_AMOUNT.add(BigDecimal.ONE));
		UserAccountDAO.updateAccount(payerAccount);
		payeeAccount.setEmailVerified(true);
		payeeAccount.setBalance(TRANSACTION_AMOUNT);
		UserAccountDAO.updateAccount(payeeAccount);
		
		KeyPair payerKeyPair = KeyHandler.generateKeyPair();
	
		byte keyNumberPayer = UserAccountService.getInstance().saveUserPublicKey(payerAccount.getId(), PKIAlgorithm.DEFAULT, KeyHandler.encodePublicKey(payerKeyPair.getPublic()));
		
		PaymentRequest paymentRequestPayer = new PaymentRequest(
				PKIAlgorithm.DEFAULT, 
				keyNumberPayer, 
				payerAccount.getUsername(), 
				payeeAccount.getUsername(), 
				Currency.BTC, 
				Converter.getLongFromBigDecimal(TRANSACTION_AMOUNT),
				Currency.CHF, 
				Converter.getLongFromBigDecimal(new BigDecimal("0.5")), 
				System.currentTimeMillis());

		paymentRequestPayer.sign(payerKeyPair.getPrivate());
		
		ServerPaymentRequest spr = new ServerPaymentRequest(paymentRequestPayer);
		
		CreateTransactionTransferObject ctto = new CreateTransactionTransferObject(spr);
		
		BigDecimal payerBalanceBefore = payerAccount.getBalance();
		BigDecimal payeeBalanceBefore = payeeAccount.getBalance();
		
		ObjectMapper mapper = new ObjectMapper();
		String asString = mapper.writeValueAsString(ctto);
		
		HttpSession session = loginAndGetSession(test3_2.getUsername(), password);
		
		MvcResult mvcResult = mockMvc.perform(post("/transaction/create").secure(false).session((MockHttpSession) session).contentType(MediaType.APPLICATION_JSON).content(asString))
				.andExpect(status().isOk())
				.andReturn();
		
		CustomResponseObject result = mapper.readValue(mvcResult.getResponse().getContentAsString(), CustomResponseObject.class);
		
		byte[] serverPaymentResponseEncoded = result.getServerPaymentResponse();
		ServerPaymentResponse serverPaymentResponse = DecoderFactory.decode(ServerPaymentResponse.class, serverPaymentResponseEncoded);
	
		UserAccount payerAccountUpdated = UserAccountService.getInstance().getById(payerAccount.getId());
		UserAccount payeeAccountUpdated = UserAccountService.getInstance().getById(payeeAccount.getId());
		
		assertTrue(result.isSuccessful());
		assertEquals(ServerResponseStatus.FAILURE, serverPaymentResponse.getPaymentResponsePayer().getStatus());
		assertEquals(TransactionService.NOT_AUTHENTICATED_USER, serverPaymentResponse.getPaymentResponsePayer().getReason());
		
		assertTrue(serverPaymentResponse.getPaymentResponsePayer().verify(KeyHandler.decodePublicKey(Constants.SERVER_KEY_PAIR.getPublicKey())));
		
		assertTrue(payerBalanceBefore.equals(payerAccountUpdated.getBalance()));
		assertTrue(payeeBalanceBefore.equals(payeeAccountUpdated.getBalance()));
		
		PaymentResponse responsePayer = serverPaymentResponse.getPaymentResponsePayer();
		
		assertEquals(paymentRequestPayer.getAmount(), responsePayer.getAmount());
		assertEquals(paymentRequestPayer.getUsernamePayer(), responsePayer.getUsernamePayer());
		assertEquals(paymentRequestPayer.getUsernamePayee(), responsePayer.getUsernamePayee());
	}
	
	@Test
	public void testCreateTransaction() throws Exception {
		assertTrue(UserAccountService.getInstance().createAccount(test4_1));
		test4_1 = UserAccountService.getInstance().getByUsername(test4_1.getUsername());
		test4_1.setEmailVerified(true);
		test4_1.setBalance(TRANSACTION_AMOUNT);
		UserAccountDAO.updateAccount(test4_1);
		
		String plainTextPw = test4_2.getPassword();
		assertTrue(UserAccountService.getInstance().createAccount(test4_2));
		test4_2 = UserAccountService.getInstance().getByUsername(test4_2.getUsername());
		test4_2.setEmailVerified(true);
		UserAccountDAO.updateAccount(test4_2);
		
		KeyPair keyPairPayer = KeyHandler.generateKeyPair();
		byte keyNumberPayer = UserAccountService.getInstance().saveUserPublicKey(test4_1.getId(), PKIAlgorithm.DEFAULT, KeyHandler.encodePublicKey(keyPairPayer.getPublic()));
		
		KeyPair keyPairPayee = KeyHandler.generateKeyPair();
		byte keyNumberPayee = UserAccountService.getInstance().saveUserPublicKey(test4_2.getId(), PKIAlgorithm.DEFAULT, KeyHandler.encodePublicKey(keyPairPayee.getPublic()));
		
		long timestamp = System.currentTimeMillis();
		
		PaymentRequest paymentRequestPayer = new PaymentRequest(
				PKIAlgorithm.DEFAULT, 
				keyNumberPayer, 
				test4_1.getUsername(), 
				test4_2.getUsername(), 
				Currency.BTC, 
				Converter.getLongFromBigDecimal(TRANSACTION_AMOUNT),
				timestamp);
		paymentRequestPayer.sign(keyPairPayer.getPrivate());
		
		PaymentRequest paymentRequestPayee = new PaymentRequest(
				PKIAlgorithm.DEFAULT, 
				keyNumberPayee, 
				test4_1.getUsername(), 
				test4_2.getUsername(), 
				Currency.BTC, 
				Converter.getLongFromBigDecimal(TRANSACTION_AMOUNT),
				timestamp);
		paymentRequestPayee.sign(keyPairPayee.getPrivate());
		
		ServerPaymentRequest request = new ServerPaymentRequest(paymentRequestPayer, paymentRequestPayee);
		CreateTransactionTransferObject ctto = new CreateTransactionTransferObject(request);
		
		ObjectMapper mapper = new ObjectMapper();
		String asString = mapper.writeValueAsString(ctto);
		
		BigDecimal payerBalanceBefore = UserAccountService.getInstance().getByUsername(test4_1.getUsername()).getBalance();
		BigDecimal payeeBalanceBefore = UserAccountService.getInstance().getByUsername(test4_2.getUsername()).getBalance();
		
		
		HttpSession session = loginAndGetSession(test4_2.getUsername(), plainTextPw);
		
		MvcResult mvcResult = mockMvc.perform(post("/transaction/create").secure(false).session((MockHttpSession) session).contentType(MediaType.APPLICATION_JSON).content(asString))
				.andExpect(status().isOk())
				.andReturn();
		
		CustomResponseObject result = mapper.readValue(mvcResult.getResponse().getContentAsString(), CustomResponseObject.class);
		assertTrue(result.isSuccessful());
		assertNotNull(result.getServerPaymentResponse());
		ServerPaymentResponse response = DecoderFactory.decode(ServerPaymentResponse.class, result.getServerPaymentResponse());
		assertNotNull(response);
		assertNotNull(response.getPaymentResponsePayer());
		
		assertEquals(ServerResponseStatus.SUCCESS, response.getPaymentResponsePayer().getStatus());
		assertTrue(response.getPaymentResponsePayer().verify(KeyHandler.decodePublicKey(Constants.SERVER_KEY_PAIR.getPublicKey())));
		
		assertEquals(payerBalanceBefore.subtract(TRANSACTION_AMOUNT), UserAccountService.getInstance().getById(test4_1.getId()).getBalance());
		assertEquals(payeeBalanceBefore.add(TRANSACTION_AMOUNT), UserAccountService.getInstance().getById(test4_2.getId()).getBalance());
	}
	
	@Test
	public void testGetHistory_failNotAuthenticated() throws Exception {
		assertTrue(UserAccountService.getInstance().createAccount(test5_1));
		test5_1 = UserAccountService.getInstance().getByUsername(test5_1.getUsername());
		test5_1.setEmailVerified(true);
		test5_1.setBalance(TRANSACTION_AMOUNT.multiply(new BigDecimal(3)));
		UserAccountDAO.updateAccount(test5_1);
		
		mockMvc.perform(get("/transaction/history").secure(false)).andExpect(status().isUnauthorized());
	}
	
	@Test
	public void testGetHistory() throws Exception {
		assertTrue(UserAccountService.getInstance().createAccount(test6_1));
		test6_1 = UserAccountService.getInstance().getByUsername(test6_1.getUsername());
		test6_1.setEmailVerified(true);
		test6_1.setBalance(TRANSACTION_AMOUNT);
		UserAccountDAO.updateAccount(test6_1);
		
		String plainTextPw = test6_2.getPassword();
		assertTrue(UserAccountService.getInstance().createAccount(test6_2));
		test6_2 = UserAccountService.getInstance().getByUsername(test6_2.getUsername());
		test6_2.setEmailVerified(true);
		UserAccountDAO.updateAccount(test6_2);
		
		KeyPair keyPairPayer = KeyHandler.generateKeyPair();
		byte keyNumberPayer = UserAccountService.getInstance().saveUserPublicKey(test6_1.getId(), PKIAlgorithm.DEFAULT, KeyHandler.encodePublicKey(keyPairPayer.getPublic()));
		
		PaymentRequest paymentRequestPayer = new PaymentRequest(
				PKIAlgorithm.DEFAULT, 
				keyNumberPayer, 
				test6_1.getUsername(), 
				test6_2.getUsername(), 
				Currency.BTC, 
				Converter.getLongFromBigDecimal(TRANSACTION_AMOUNT),
				System.currentTimeMillis());
		paymentRequestPayer.sign(keyPairPayer.getPrivate());
		
		ServerPaymentRequest request = new ServerPaymentRequest(paymentRequestPayer);
		CreateTransactionTransferObject ctto = new CreateTransactionTransferObject(request);
		
		ObjectMapper mapper = new ObjectMapper();
		String asString = mapper.writeValueAsString(ctto);
		
		
		HttpSession session = loginAndGetSession(test6_1.getUsername(), plainTextPw);
		
		MvcResult mvcResult = mockMvc.perform(post("/transaction/create").secure(false).session((MockHttpSession) session).contentType(MediaType.APPLICATION_JSON).content(asString))
				.andExpect(status().isOk())
				.andReturn();
		
		CustomResponseObject result = mapper.readValue(mvcResult.getResponse().getContentAsString(), CustomResponseObject.class);
		assertEquals(true, result.isSuccessful());
		assertNotNull(result.getServerPaymentResponse());
		ServerPaymentResponse response = DecoderFactory.decode(ServerPaymentResponse.class, result.getServerPaymentResponse());
		assertNotNull(response);
		assertNotNull(response.getPaymentResponsePayer());
		assertEquals(ServerResponseStatus.SUCCESS, response.getPaymentResponsePayer().getStatus());
		
		mvcResult = mockMvc.perform(get("/transaction/history")
				.param("txPage", "0")
				.param("txPayInPage", "0")
				.param("txPayOutPage", "0")
				.secure(false).session((MockHttpSession) session))
				.andExpect(status().isOk())
				.andReturn();
		
		CustomResponseObject cro = mapper.readValue(mvcResult.getResponse().getContentAsString(), CustomResponseObject.class);
		assertTrue(cro.isSuccessful());
		
		GetHistoryTransferObject ghto = cro.getGetHistoryTO();
		assertNotNull(ghto);
		assertEquals(1, ghto.getTransactionHistory().size());
		
		logout(mvcResult);
		
		mvcResult = mockMvc.perform(get("/transaction/history").secure(false).session((MockHttpSession) session))
				.andExpect(status().isUnauthorized())
				.andReturn();
	}
	
	private void logout(MvcResult result) {
		result.getRequest().getSession().invalidate();
	}
	
	private HttpSession loginAndGetSession(String username, String plainTextPassword) throws Exception {
		HttpSession session = mockMvc.perform(post("/j_spring_security_check").secure(false).param("j_username", username).param("j_password", plainTextPassword))
				.andExpect(status().isOk())
				.andReturn()
				.getRequest()
				.getSession();
		
		return session;
	}
	
	private void createAccountAndVerifyAndReload(UserAccount userAccount, BigDecimal balance) throws UsernameAlreadyExistsException, UserAccountNotFoundException, BitcoinException, InvalidUsernameException, InvalidEmailException, EmailAlreadyExistsException {
		assertTrue(UserAccountService.getInstance().createAccount(userAccount));
		userAccount = UserAccountService.getInstance().getByUsername(userAccount.getUsername());
		userAccount.setEmailVerified(true);
		userAccount.setBalance(balance);
		UserAccountDAO.updateAccount(userAccount);
	}
	
	@Test
	public void testGetExchangeRateTest() throws Exception{
		createAccountAndVerifyAndReload(test7_1, BigDecimal.ONE);
		String plainTextPw = test7_1.getPassword();
		
		
		ObjectMapper mapper = new ObjectMapper();
		
		HttpSession session = loginAndGetSession(test7_1.getUsername(), plainTextPw);
		
		MvcResult mvcResult = mockMvc.perform(get("/transaction/exchange-rate").secure(false).session((MockHttpSession) session))
				.andExpect(status().isOk())
				.andReturn();
		
		CustomResponseObject cro2 = mapper.readValue(mvcResult.getResponse().getContentAsString(), CustomResponseObject.class);
		
		assertTrue(cro2.isSuccessful());
		
		String exchangeRate = cro2.getMessage();
		assertNotNull(exchangeRate);
		Double er = Double.valueOf(exchangeRate);
		assertTrue(er>0);
	}

	@Test
	public void testPayOut() throws Exception{
		createAccountAndVerifyAndReload(test8_1, BigDecimal.ONE);
		String plainTextPw = test8_1.getPassword();
		UserAccount fromDB = UserAccountService.getInstance().getByUsername(test8_1.getUsername());
		
		PayOutTransaction pot = new PayOutTransaction();
		pot.setUserID(fromDB.getId());
		pot.setBtcAddress("mtSKrDw1f1NfstiiwEWzhwYdt96dNQGa1S");
		pot.setAmount(new BigDecimal("0.5"));
		
		ObjectMapper mapper = new ObjectMapper();
		String asString = mapper.writeValueAsString(pot);
		
		HttpSession session = loginAndGetSession(test8_1.getUsername(), plainTextPw);
		
		MvcResult mvcResult = mockMvc.perform(post("/transaction/payOut").secure(false).session((MockHttpSession) session).contentType(MediaType.APPLICATION_JSON).content(asString))
				.andExpect(status().isOk())
				.andReturn();
		
		CustomResponseObject result = mapper.readValue(mvcResult.getResponse().getContentAsString(), CustomResponseObject.class);
		
		assertTrue(result.isSuccessful());
	}
	
}
