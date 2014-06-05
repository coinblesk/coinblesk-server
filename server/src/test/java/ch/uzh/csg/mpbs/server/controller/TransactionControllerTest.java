package ch.uzh.csg.mpbs.server.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.KeyPair;
import java.security.SignedObject;

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

import ch.uzh.csg.mbps.model.Transaction;
import ch.uzh.csg.mbps.responseobject.CreateTransactionTransferObject;
import ch.uzh.csg.mbps.responseobject.CustomResponseObject;
import ch.uzh.csg.mbps.responseobject.GetHistoryTransferObject;
import ch.uzh.csg.mbps.server.dao.UserAccountDAO;
import ch.uzh.csg.mbps.server.domain.PayOutTransaction;
import ch.uzh.csg.mbps.server.domain.UserAccount;
import ch.uzh.csg.mbps.server.service.UserAccountService;
import ch.uzh.csg.mbps.server.util.Constants;
import ch.uzh.csg.mbps.server.util.exceptions.EmailAlreadyExistsException;
import ch.uzh.csg.mbps.server.util.exceptions.InvalidEmailException;
import ch.uzh.csg.mbps.server.util.exceptions.InvalidUsernameException;
import ch.uzh.csg.mbps.server.util.exceptions.UserAccountNotFoundException;
import ch.uzh.csg.mbps.server.util.exceptions.UsernameAlreadyExistsException;
import ch.uzh.csg.mbps.util.KeyHandler;
import ch.uzh.csg.mbps.util.Pair;
import ch.uzh.csg.mbps.util.Serializer;

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
	private static UserAccount test1;
	private static UserAccount test2;
	private static UserAccount test3;
	private static UserAccount test4;
	private static UserAccount test5;
	private static UserAccount test6;
	private static UserAccount test6_1;
	private static UserAccount test6_2;
	private static UserAccount test7;
	private static UserAccount test7_1;
	private static UserAccount test8;
	private static UserAccount test9;
	private static UserAccount test9_1;
	private static final BigDecimal TRANSACTION_AMOUNT = new BigDecimal(10.1).setScale(8, RoundingMode.HALF_UP);
	
	@Before
	public void setUp() throws Exception {
		UserAccountService.enableTestingMode();
		
		if (!initialized) {
			mockMvc = MockMvcBuilders.webAppContextSetup(webAppContext).addFilter(springSecurityFilterChain).build();
			
			test1 = new UserAccount("test1", "test1@bitcoin.csg.uzh.ch", "asdf");
			test2 = new UserAccount("test2", "test2@bitcoin.csg.uzh.ch", "i-don't-need-one");
			test3 = new UserAccount("test3", "test3@bitcoin.csg.uzh.ch", "i-don't-need-one");
			test4 = new UserAccount("test4", "test4@bitcoin.csg.uzh.ch", "i-don't-need-one");
			test5 = new UserAccount("test5", "test5@bitcoin.csg.uzh.ch", "i-don't-need-one");
			test6 = new UserAccount("test6", "test6@bitcoin.csg.uzh.ch", "i-don't-need-one");
			test6_1 = new UserAccount("test6_1", "test6_1@bitcoin.csg.uzh.ch", "i-don't-need-one");
			test6_2 = new UserAccount("test6_2", "test6_2@bitcoin.csg.uzh.ch", "i-don't-need-one");
			test7 = new UserAccount("test7", "test7@bitcoin.csg.uzh.ch", "i-don't-need-one");
			test7_1 = new UserAccount("test7_1", "test7_1@bitcoin.csg.uzh.ch", "i-don't-need-one");
			test8 = new UserAccount("test8", "test8@bitcoin.csg.uzh.ch", "i-don't-need-one");
			test9 = new UserAccount("test9", "test9@bitcoin.csg.uzh.ch", "i-don't-need-one");
			test9_1 = new UserAccount("test9_1", "test9_+@bitcoin.csg.uzh.ch", "i-don't-need-one");
			
			
			KeyPair keypair = KeyHandler.generateKeys();
			
			Constants.PRIVATEKEY = keypair.getPrivate();
			Constants.PUBLICKEY = keypair.getPublic();
				
			initialized = true;
		}
	}
	
	@After
	public void tearDown() {
		UserAccountService.disableTestingMode();
	}
	
	@Test
	public void testCreateTransaction_failNotAuthenticated() throws Exception {
		assertTrue(UserAccountService.getInstance().createAccount(test1));
		assertTrue(UserAccountService.getInstance().createAccount(test2));
		
		test1 = UserAccountService.getInstance().getByUsername(test1.getUsername());
		test2 = UserAccountService.getInstance().getByUsername(test2.getUsername());
		
		Transaction buyerTransaction = new Transaction(test1.getTransactionNumber(), test2.getTransactionNumber(), test1.getUsername(), test2.getUsername(), TRANSACTION_AMOUNT, "", BigDecimal.ZERO);
		Transaction sellerTransaction = new Transaction(test1.getTransactionNumber(), test2.getTransactionNumber(), test1.getUsername(), test2.getUsername(), TRANSACTION_AMOUNT, "", BigDecimal.ZERO);
		
		SignedObject signedTransactionBuyer = KeyHandler.signTransaction(buyerTransaction, test1.getPrivateKey());
		SignedObject signedTransactionSeller = KeyHandler.signTransaction(sellerTransaction, test2.getPrivateKey());

		ObjectMapper mapper = new ObjectMapper();
		String asString = mapper.writeValueAsString(new Pair<SignedObject>(signedTransactionBuyer, signedTransactionSeller));
		
		mockMvc.perform(post("/transaction/create").secure(false).contentType(MediaType.APPLICATION_JSON).content(asString))
				.andExpect(status().isUnauthorized());
	}
	
	@Test
	public void testCreateTransaction() throws Exception {
		assertTrue(UserAccountService.getInstance().createAccount(test3));
		test3 = UserAccountService.getInstance().getByUsername(test3.getUsername());
		test3.setEmailVerified(true);
		test3.setBalance(TRANSACTION_AMOUNT);
		UserAccountDAO.updateAccount(test3);
		
		String plainTextPw = test4.getPassword();
		assertTrue(UserAccountService.getInstance().createAccount(test4));
		test4 = UserAccountService.getInstance().getByUsername(test4.getUsername());
		test4.setEmailVerified(true);
		UserAccountDAO.updateAccount(test4);
		
		Transaction buyerTransaction = new Transaction(test3.getTransactionNumber(), test4.getTransactionNumber(), test3.getUsername(), test4.getUsername(), TRANSACTION_AMOUNT, "", BigDecimal.ZERO);
		Transaction sellerTransaction = new Transaction(test3.getTransactionNumber(), test4.getTransactionNumber(), test3.getUsername(), test4.getUsername(), TRANSACTION_AMOUNT, "", BigDecimal.ZERO);
		
		SignedObject signedTransactionBuyer = KeyHandler.signTransaction(buyerTransaction, test3.getPrivateKey());
		SignedObject signedTransactionSeller = KeyHandler.signTransaction(sellerTransaction, test4.getPrivateKey());
		
		CreateTransactionTransferObject transferObject = new CreateTransactionTransferObject(signedTransactionBuyer, signedTransactionSeller);
		
		ObjectMapper mapper = new ObjectMapper();
		String asString = mapper.writeValueAsString(transferObject);
		
		HttpSession session = loginAndGetSession(test4.getUsername(), plainTextPw);
		
		MvcResult mvcResult = mockMvc.perform(post("/transaction/create").secure(false).session((MockHttpSession) session).contentType(MediaType.APPLICATION_JSON).content(asString))
				.andExpect(status().isOk())
				.andReturn();
		
		CustomResponseObject result = mapper.readValue(mvcResult.getResponse().getContentAsString(), CustomResponseObject.class);
		
		assertEquals(true, result.isSuccessful());
		CreateTransactionTransferObject ctto = result.getCreateTransactionTO();
		assertNotNull(ctto);
		
		SignedObject sellerSignedObject = ctto.getSellerSignedObject();
		SignedObject buyerSignedObject = ctto.getBuyerSignedObject();
		assertNotNull(sellerSignedObject);
		assertNotNull(buyerSignedObject);
		
		test3 = UserAccountService.getInstance().getById(test3.getId());
		test4 = UserAccountService.getInstance().getById(test4.getId());
		
		assertTrue(KeyHandler.verifyObject(sellerSignedObject, Constants.PUBLICKEY));
		assertTrue(KeyHandler.verifyObject(buyerSignedObject, Constants.PUBLICKEY));
		
		Transaction tx = KeyHandler.retrieveTransaction(sellerSignedObject);
		
		assertEquals(buyerTransaction.getAmount(), tx.getAmount());
		assertEquals(buyerTransaction.getBuyerUsername(), tx.getBuyerUsername());
		assertEquals(buyerTransaction.getSellerUsername(), tx.getSellerUsername());
		assertEquals(buyerTransaction.getTransactionNrBuyer(), tx.getTransactionNrBuyer());
		assertEquals(buyerTransaction.getTransactionNrSeller(), tx.getTransactionNrSeller());
		
		assertEquals(sellerTransaction.getAmount(), tx.getAmount());
		assertEquals(sellerTransaction.getBuyerUsername(), tx.getBuyerUsername());
		assertEquals(sellerTransaction.getSellerUsername(), tx.getSellerUsername());
		assertEquals(sellerTransaction.getTransactionNrBuyer(), tx.getTransactionNrBuyer());
		assertEquals(sellerTransaction.getTransactionNrSeller(), tx.getTransactionNrSeller());
	}
	
	@Test
	public void testCreateTransaction_similarToClient() throws Exception {
		assertTrue(UserAccountService.getInstance().createAccount(test8));
		test8 = UserAccountService.getInstance().getByUsername(test8.getUsername());
		test8.setEmailVerified(true);
		test8.setBalance(TRANSACTION_AMOUNT);
		UserAccountDAO.updateAccount(test8);
		
		String plainTextPw = test9.getPassword();
		assertTrue(UserAccountService.getInstance().createAccount(test9));
		test9 = UserAccountService.getInstance().getByUsername(test9.getUsername());
		test9.setEmailVerified(true);
		UserAccountDAO.updateAccount(test9);
		
		//create transaction object from seller
		Transaction sellerTransaction = new Transaction();
		sellerTransaction.setAmount(TRANSACTION_AMOUNT);
		sellerTransaction.setSellerUsername(test9.getUsername());
		sellerTransaction.setTransactionNrSeller(test9.getTransactionNumber());
		byte[] serializedSellerTransaction = serialize(sellerTransaction);
		
		//buyer receives the serialized seller transaction object
		Transaction buyerTransaction = deserialize(serializedSellerTransaction);
		//buyer adds his information to the tx object
		buyerTransaction.setBuyerUsername(test8.getUsername());
		buyerTransaction.setTransactionNrBuyer(test8.getTransactionNumber());
		//buyer signes the object with his private key
		SignedObject signTransactionBuyer = KeyHandler.signTransaction(buyerTransaction, test8.getPrivateKey());
		//buyer serializes the signed object before sending to the seller
		byte[] serializedBuyerTransaction = Serializer.serialize(signTransactionBuyer);
		
		//seller receives the serialized buyer transaction object
		SignedObject signedObjectFromBuyer = Serializer.deserialize(serializedBuyerTransaction);
		Transaction transactionFromBuyer = KeyHandler.retrieveTransaction(signedObjectFromBuyer);
		//seller reads the buyer infos and completes his transaction object
		sellerTransaction = new Transaction();
		sellerTransaction.setAmount(TRANSACTION_AMOUNT);
		sellerTransaction.setSellerUsername(test9.getUsername());
		sellerTransaction.setTransactionNrSeller(test9.getTransactionNumber());
		sellerTransaction.setBuyerUsername(transactionFromBuyer.getBuyerUsername());
		sellerTransaction.setTransactionNrBuyer(transactionFromBuyer.getTransactionNrBuyer());
		//seller signs the transaction object
		SignedObject signedObjectSeller = KeyHandler.signTransaction(sellerTransaction, test9.getPrivateKey());
		
		//seller creates CreateTransactionTransferObject to send to server
		CreateTransactionTransferObject transferObject = new CreateTransactionTransferObject(signedObjectFromBuyer, signedObjectSeller);
		
		ObjectMapper mapper = new ObjectMapper();
		String asString = mapper.writeValueAsString(transferObject);
		
		HttpSession session = loginAndGetSession(test9.getUsername(), plainTextPw);
		
		MvcResult mvcResult = mockMvc.perform(post("/transaction/create").secure(false).session((MockHttpSession) session).contentType(MediaType.APPLICATION_JSON).content(asString))
				.andExpect(status().isOk())
				.andReturn();
		
		CustomResponseObject result = mapper.readValue(mvcResult.getResponse().getContentAsString(), CustomResponseObject.class);
		
		assertEquals(true, result.isSuccessful());
		CreateTransactionTransferObject ctto = result.getCreateTransactionTO();
		assertNotNull(ctto);
		
		SignedObject sellerSignedObject = ctto.getSellerSignedObject();
		SignedObject buyerSignedObject = ctto.getBuyerSignedObject();
		assertNotNull(sellerSignedObject);
		assertNotNull(buyerSignedObject);
		
		test8 = UserAccountService.getInstance().getById(test8.getId());
		test9 = UserAccountService.getInstance().getById(test9.getId());
		
		assertTrue(KeyHandler.verifyObject(sellerSignedObject, Constants.PUBLICKEY));
		assertTrue(KeyHandler.verifyObject(buyerSignedObject, Constants.PUBLICKEY));
		
		Transaction tx = KeyHandler.retrieveTransaction(sellerSignedObject);
		
		assertEquals(buyerTransaction.getAmount(), tx.getAmount());
		assertEquals(buyerTransaction.getBuyerUsername(), tx.getBuyerUsername());
		assertEquals(buyerTransaction.getSellerUsername(), tx.getSellerUsername());
		assertEquals(buyerTransaction.getTransactionNrBuyer(), tx.getTransactionNrBuyer());
		assertEquals(buyerTransaction.getTransactionNrSeller(), tx.getTransactionNrSeller());
		
		assertEquals(sellerTransaction.getAmount(), tx.getAmount());
		assertEquals(sellerTransaction.getBuyerUsername(), tx.getBuyerUsername());
		assertEquals(sellerTransaction.getSellerUsername(), tx.getSellerUsername());
		assertEquals(sellerTransaction.getTransactionNrBuyer(), tx.getTransactionNrBuyer());
		assertEquals(sellerTransaction.getTransactionNrSeller(), tx.getTransactionNrSeller());
	}

	private byte[] serialize(Transaction sellerTransaction) throws IOException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
	    ObjectOutputStream oos = new ObjectOutputStream(baos);
	    oos.writeObject(sellerTransaction);
	    return baos.toByteArray();
	}
	
	private Transaction deserialize(byte[] bytes) throws IOException, ClassNotFoundException {
		ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
		ObjectInputStream ois = new ObjectInputStream(bais);
		return (Transaction) ois.readObject();
	}

	@Test
	public void testGetHistory_failNotAuthenticated() throws Exception {
		assertTrue(UserAccountService.getInstance().createAccount(test5));
		test5 = UserAccountService.getInstance().getByUsername(test5.getUsername());
		test5.setEmailVerified(true);
		test5.setBalance(TRANSACTION_AMOUNT.multiply(new BigDecimal(3)));
		UserAccountDAO.updateAccount(test5);
		
		mockMvc.perform(get("/transaction/history").secure(false)).andExpect(status().isUnauthorized());
	}
	
	@Test
	public void testGetHistory() throws Exception {
		assertTrue(UserAccountService.getInstance().createAccount(test6));
		test6 = UserAccountService.getInstance().getByUsername(test6.getUsername());
		test6.setEmailVerified(true);
		test6.setBalance(TRANSACTION_AMOUNT.multiply(new BigDecimal(3).setScale(8,RoundingMode.HALF_UP)));
		UserAccountDAO.updateAccount(test6);
		
		String plainTextPw = test7.getPassword();
		assertTrue(UserAccountService.getInstance().createAccount(test7));
		test7 = UserAccountService.getInstance().getByUsername(test7.getUsername());
		test7.setEmailVerified(true);
		UserAccountDAO.updateAccount(test7);
		
		Transaction buyerTransaction = new Transaction(test6.getTransactionNumber(), test7.getTransactionNumber(), test6.getUsername(), test7.getUsername(), TRANSACTION_AMOUNT, "", BigDecimal.ZERO);
		Transaction sellerTransaction = new Transaction(test6.getTransactionNumber(), test7.getTransactionNumber(), test6.getUsername(), test7.getUsername(), TRANSACTION_AMOUNT, "", BigDecimal.ZERO);
		
		SignedObject signedTransactionBuyer = KeyHandler.signTransaction(buyerTransaction, test6.getPrivateKey());
		SignedObject signedTransactionSeller = KeyHandler.signTransaction(sellerTransaction, test7.getPrivateKey());
		
		CreateTransactionTransferObject transferObject = new CreateTransactionTransferObject(signedTransactionBuyer, signedTransactionSeller);
		
		ObjectMapper mapper = new ObjectMapper();
		String asString = mapper.writeValueAsString(transferObject);
		
		HttpSession session = loginAndGetSession(test7.getUsername(), plainTextPw);
		
		MvcResult mvcResult = mockMvc.perform(post("/transaction/create").secure(false).session((MockHttpSession) session).contentType(MediaType.APPLICATION_JSON).content(asString))
				.andExpect(status().isOk())
				.andReturn();
		
		CustomResponseObject cro = mapper.readValue(mvcResult.getResponse().getContentAsString(), CustomResponseObject.class);
		assertTrue(cro.isSuccessful());
		
		mvcResult = mockMvc.perform(get("/transaction/history")
				.param("txPage", "0")
				.param("txPayInPage", "0")
				.param("txPayOutPage", "0")
				.secure(false).session((MockHttpSession) session))
				.andExpect(status().isOk())
				.andReturn();
		
		CustomResponseObject cro2 = mapper.readValue(mvcResult.getResponse().getContentAsString(), CustomResponseObject.class);
		assertTrue(cro2.isSuccessful());
		
		GetHistoryTransferObject ghto = cro2.getGetHistoryTO();
		assertNotNull(ghto);
		assertEquals(1, ghto.getTransactionHistory().size());
		
		logout(mvcResult);
		
		mvcResult = mockMvc.perform(get("/transaction/history").secure(false).session((MockHttpSession) session))
				.andExpect(status().isUnauthorized())
				.andReturn();
	}
	
	@Test
	public void testGetMainActivityRequests() throws Exception {
		assertTrue(UserAccountService.getInstance().createAccount(test6_2));
		test6_2 = UserAccountService.getInstance().getByUsername(test6_2.getUsername());
		test6_2.setEmailVerified(true);
		test6_2.setBalance(new BigDecimal("100"));
		UserAccountDAO.updateAccount(test6_2);
		
		String plainTextPw = test7_1.getPassword();
		assertTrue(UserAccountService.getInstance().createAccount(test7_1));
		test7_1 = UserAccountService.getInstance().getByUsername(test7_1.getUsername());
		test7_1.setEmailVerified(true);
		UserAccountDAO.updateAccount(test7_1);
		ObjectMapper mapper = null;
		
		HttpSession session = loginAndGetSession(test7_1.getUsername(), plainTextPw);
		MvcResult mvcResult = null;
		for(int i = 0; i<8;i++){
			test6_2 = UserAccountService.getInstance().getByUsername(test6_2.getUsername());
			test7_1 = UserAccountService.getInstance().getByUsername(test7_1.getUsername());
			
			Transaction buyerTransaction = new Transaction(test6_2.getTransactionNumber(), test7_1.getTransactionNumber(), test6_2.getUsername(), test7_1.getUsername(), TRANSACTION_AMOUNT, "", BigDecimal.ZERO);
			Transaction sellerTransaction = new Transaction(test6_2.getTransactionNumber(), test7_1.getTransactionNumber(), test6_2.getUsername(), test7_1.getUsername(), TRANSACTION_AMOUNT, "", BigDecimal.ZERO);
			
			SignedObject signedTransactionBuyer = KeyHandler.signTransaction(buyerTransaction, test6_2.getPrivateKey());
			SignedObject signedTransactionSeller = KeyHandler.signTransaction(sellerTransaction, test7_1.getPrivateKey());
			
			CreateTransactionTransferObject transferObject = new CreateTransactionTransferObject(signedTransactionBuyer, signedTransactionSeller);
			
			mapper = new ObjectMapper();
			String asString = mapper.writeValueAsString(transferObject);
			mvcResult = mockMvc.perform(post("/transaction/create").secure(false).session((MockHttpSession) session).contentType(MediaType.APPLICATION_JSON).content(asString))
					.andExpect(status().isOk())
					.andReturn();
			
			CustomResponseObject cro = mapper.readValue(mvcResult.getResponse().getContentAsString(), CustomResponseObject.class);
			assertTrue(cro.isSuccessful());
		}
		
		
		
		mvcResult = mockMvc.perform(get("/transaction/mainActivityRequests")
				.secure(false).session((MockHttpSession) session))
				.andExpect(status().isOk())
				.andReturn();
		
		CustomResponseObject cro2 = mapper.readValue(mvcResult.getResponse().getContentAsString(), CustomResponseObject.class);
		assertTrue(cro2.isSuccessful());
		BigDecimal exchangeRate = new BigDecimal(cro2.getMessage());
		assertTrue(exchangeRate.compareTo(BigDecimal.ZERO) >= 0);
		
		assertNotNull(cro2.getReadAccountTO().getUserAccount().getBalance());
		
		GetHistoryTransferObject ghto = cro2.getGetHistoryTO();
		assertNotNull(ghto);
		assertEquals(3, ghto.getTransactionHistory().size());
		assertEquals(0, ghto.getPayInTransactionHistory().size());
		assertEquals(0, ghto.getPayOutTransactionHistory().size());
		
		logout(mvcResult);
		
		mvcResult = mockMvc.perform(get("/transaction/mainActivityRequests").secure(false).session((MockHttpSession) session))
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
	public void getExchangeRateTest() throws Exception{
		createAccountAndVerifyAndReload(test6_1, BigDecimal.ONE);
		String plainTextPw = test6_1.getPassword();
		
		
		ObjectMapper mapper = new ObjectMapper();
		
		HttpSession session = loginAndGetSession(test6_1.getUsername(), plainTextPw);
		
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
	public void payOut() throws Exception{
		createAccountAndVerifyAndReload(test9_1, BigDecimal.ONE);
		String plainTextPw = test9_1.getPassword();
		UserAccount fromDB = UserAccountService.getInstance().getByUsername(test9_1.getUsername());
		
		PayOutTransaction pot = new PayOutTransaction();
		pot.setUserID(fromDB.getId());
		pot.setBtcAddress("mtSKrDw1f1NfstiiwEWzhwYdt96dNQGa1S");
		pot.setAmount(new BigDecimal("0.5"));
		
		ObjectMapper mapper = new ObjectMapper();
		String asString = mapper.writeValueAsString(pot);
		
		HttpSession session = loginAndGetSession(test9_1.getUsername(), plainTextPw);
		
		MvcResult mvcResult = mockMvc.perform(post("/transaction/payOut").secure(false).session((MockHttpSession) session).contentType(MediaType.APPLICATION_JSON).content(asString))
				.andExpect(status().isOk())
				.andReturn();
		
		CustomResponseObject result = mapper.readValue(mvcResult.getResponse().getContentAsString(), CustomResponseObject.class);
		
		assertTrue(result.isSuccessful());
		
	}
	
}
