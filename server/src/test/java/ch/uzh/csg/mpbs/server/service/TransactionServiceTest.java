package ch.uzh.csg.mpbs.server.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.math.BigDecimal;
import java.security.KeyPair;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import ch.uzh.csg.mbps.customserialization.Currency;
import ch.uzh.csg.mbps.customserialization.PKIAlgorithm;
import ch.uzh.csg.mbps.customserialization.PaymentRequest;
import ch.uzh.csg.mbps.customserialization.PaymentResponse;
import ch.uzh.csg.mbps.customserialization.ServerPaymentRequest;
import ch.uzh.csg.mbps.customserialization.ServerPaymentResponse;
import ch.uzh.csg.mbps.customserialization.ServerResponseStatus;
import ch.uzh.csg.mbps.keys.CustomKeyPair;
import ch.uzh.csg.mbps.model.HistoryTransaction;
import ch.uzh.csg.mbps.server.dao.TransactionDAO;
import ch.uzh.csg.mbps.server.dao.UserAccountDAO;
import ch.uzh.csg.mbps.server.domain.DbTransaction;
import ch.uzh.csg.mbps.server.domain.UserAccount;
import ch.uzh.csg.mbps.server.security.KeyHandler;
import ch.uzh.csg.mbps.server.service.TransactionService;
import ch.uzh.csg.mbps.server.service.UserAccountService;
import ch.uzh.csg.mbps.server.util.Config;
import ch.uzh.csg.mbps.server.util.Constants;
import ch.uzh.csg.mbps.server.util.HibernateUtil;
import ch.uzh.csg.mbps.server.util.exceptions.TransactionException;
import ch.uzh.csg.mbps.util.Converter;

public class TransactionServiceTest {
	private static final BigDecimal TRANSACTION_AMOUNT = new BigDecimal("1.50000000");
	
	private boolean initialized = false;
	
	@Before
	public void setUp() throws Exception {
		UserAccountService.enableTestingMode();
		if (!initialized) {
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
	public void testCreateTransaction() throws Exception {
		UserAccount payerAccount = createAccountAndVerifyAndReload("TDAOT_1", "TDAOT_1@bitcoin.csg.uzh.ch", "my-password", TRANSACTION_AMOUNT.add(new BigDecimal("1.00000000")));
		UserAccount payeeAccount = createAccountAndVerifyAndReload("TDAOT_2", "TDAOT_2@bitcoin.csg.uzh.ch", "my-password", BigDecimal.ZERO);
		
		KeyPair payerKeyPair = KeyHandler.generateKeyPair();
		KeyPair payeeKeyPair = KeyHandler.generateKeyPair();
	
		byte keyNumberPayer = UserAccountService.getInstance().saveUserPublicKey(payerAccount.getId(), PKIAlgorithm.DEFAULT, KeyHandler.encodePublicKey(payerKeyPair.getPublic()));
		byte keyNumberPayee = UserAccountService.getInstance().saveUserPublicKey(payeeAccount.getId(), PKIAlgorithm.DEFAULT, KeyHandler.encodePublicKey(payeeKeyPair.getPublic()));
		
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
		
		PaymentRequest paymentRequestPayee = new PaymentRequest(
				PKIAlgorithm.DEFAULT, 
				keyNumberPayee, 
				payerAccount.getUsername(), 
				payeeAccount.getUsername(), 
				Currency.BTC, 
				Converter.getLongFromBigDecimal(TRANSACTION_AMOUNT),
				Currency.CHF, 
				Converter.getLongFromBigDecimal(new BigDecimal("0.5")), 
				paymentRequestPayer.getTimestamp());

		paymentRequestPayee.sign(payeeKeyPair.getPrivate());
		
		ServerPaymentRequest request = new ServerPaymentRequest(paymentRequestPayer, paymentRequestPayee);
		
		int nofTransaction = getAllTransactions().size();
		
		BigDecimal payerBalanceBefore = payerAccount.getBalance();
		BigDecimal payeeBalanceBefore = payeeAccount.getBalance();
		
		ServerPaymentResponse response = TransactionService.getInstance().createTransaction(request);
	
		assertEquals(nofTransaction+1, getAllTransactions().size());
		
		UserAccount payerAccountUpdated = UserAccountService.getInstance().getById(payerAccount.getId());
		UserAccount payeeAccountUpdated = UserAccountService.getInstance().getById(payeeAccount.getId());
		
		assertEquals(0, payerBalanceBefore.subtract(TRANSACTION_AMOUNT).compareTo(payerAccountUpdated.getBalance()));
		assertEquals(0, payeeBalanceBefore.add(TRANSACTION_AMOUNT).compareTo(payeeAccountUpdated.getBalance()));
		
		assertTrue(response.getPaymentResponsePayer().verify(KeyHandler.decodePublicKey(Constants.SERVER_KEY_PAIR.getPublicKey())));
		
		assertNull(response.getPaymentResponsePayee());
		
		PaymentResponse responsePayer = response.getPaymentResponsePayer();
		
		assertEquals(paymentRequestPayer.getAmount(), responsePayer.getAmount());
		assertEquals(paymentRequestPayer.getUsernamePayer(), responsePayer.getUsernamePayer());
		assertEquals(paymentRequestPayer.getUsernamePayee(), responsePayer.getUsernamePayee());
	}
	
	@Test
	public void testCreateDirectSendTransaction() throws Exception {
		UserAccount payerAccount = createAccountAndVerifyAndReload("TDAOT_3", "TDAOT_3@bitcoin.csg.uzh.ch", "my-password", TRANSACTION_AMOUNT.add(new BigDecimal("1.00000000")));
		UserAccount payeeAccount = createAccountAndVerifyAndReload("TDAOT_4", "TDAOT_4@bitcoin.csg.uzh.ch", "my-password", BigDecimal.ZERO);
		
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
		
		ServerPaymentRequest request = new ServerPaymentRequest(paymentRequestPayer);
		
		int nofTransaction = getAllTransactions().size();
		
		BigDecimal payerBalanceBefore = payerAccount.getBalance();
		BigDecimal payeeBalanceBefore = payeeAccount.getBalance();
		
		ServerPaymentResponse response = TransactionService.getInstance().createTransaction(request);
	
		assertEquals(nofTransaction+1, getAllTransactions().size());
		
		UserAccount payerAccountUpdated = UserAccountService.getInstance().getById(payerAccount.getId());
		UserAccount payeeAccountUpdated = UserAccountService.getInstance().getById(payeeAccount.getId());
		
		assertEquals(0, payerBalanceBefore.subtract(TRANSACTION_AMOUNT).compareTo(payerAccountUpdated.getBalance()));
		assertEquals(0, payeeBalanceBefore.add(TRANSACTION_AMOUNT).compareTo(payeeAccountUpdated.getBalance()));
		
		assertTrue(response.getPaymentResponsePayer().verify(KeyHandler.decodePublicKey(Constants.SERVER_KEY_PAIR.getPublicKey())));
		
		PaymentResponse responsePayer = response.getPaymentResponsePayer();
		
		assertEquals(paymentRequestPayer.getAmount(), responsePayer.getAmount());
		assertEquals(paymentRequestPayer.getUsernamePayer(), responsePayer.getUsernamePayer());
		assertEquals(paymentRequestPayer.getUsernamePayee(), responsePayer.getUsernamePayee());
	}
	
	@Test
	public void testCreateTransaction_FailDuplicateRequest() throws Exception {
		UserAccount payerAccount = createAccountAndVerifyAndReload("TDAOT_5", "TDAOT_5@bitcoin.csg.uzh.ch", "my-password", TRANSACTION_AMOUNT.multiply(new BigDecimal(3)));
		UserAccount payeeAccount = createAccountAndVerifyAndReload("TDAOT_6", "TDAOT_6@bitcoin.csg.uzh.ch", "my-password", BigDecimal.ZERO);
		
		KeyPair payerKeyPair = KeyHandler.generateKeyPair();
		byte keyNumberPayer = UserAccountService.getInstance().saveUserPublicKey(payerAccount.getId(), PKIAlgorithm.DEFAULT, KeyHandler.encodePublicKey(payerKeyPair.getPublic()));
		
		PaymentRequest paymentRequestPayer = new PaymentRequest(
				PKIAlgorithm.DEFAULT, 
				keyNumberPayer, 
				payerAccount.getUsername(), 
				payeeAccount.getUsername(), 
				Currency.BTC, 
				Converter.getLongFromBigDecimal(TRANSACTION_AMOUNT),
				System.currentTimeMillis());
		paymentRequestPayer.sign(payerKeyPair.getPrivate());
		
		ServerPaymentRequest request = new ServerPaymentRequest(paymentRequestPayer);
		
		int nofTransaction = getAllTransactions().size();
		
		BigDecimal payerBalanceBefore = payerAccount.getBalance();
		BigDecimal payeeBalanceBefore = payeeAccount.getBalance();
		
		ServerPaymentResponse response = TransactionService.getInstance().createTransaction(request);
	
		nofTransaction++;
		assertEquals(nofTransaction, getAllTransactions().size());
		
		UserAccount payerAccountUpdated = UserAccountService.getInstance().getById(payerAccount.getId());
		UserAccount payeeAccountUpdated = UserAccountService.getInstance().getById(payeeAccount.getId());
		
		assertEquals(0, payerBalanceBefore.subtract(TRANSACTION_AMOUNT).compareTo(payerAccountUpdated.getBalance()));
		assertEquals(0, payeeBalanceBefore.add(TRANSACTION_AMOUNT).compareTo(payeeAccountUpdated.getBalance()));
		
		assertTrue(response.getPaymentResponsePayer().verify(KeyHandler.decodePublicKey(Constants.SERVER_KEY_PAIR.getPublicKey())));
		assertNull(response.getPaymentResponsePayee());
		
		PaymentResponse responsePayer = response.getPaymentResponsePayer();
		assertEquals(paymentRequestPayer.getUsernamePayer(), responsePayer.getUsernamePayer());
		assertEquals(paymentRequestPayer.getUsernamePayee(), responsePayer.getUsernamePayee());
		assertEquals(paymentRequestPayer.getCurrency().getCode(), responsePayer.getCurrency().getCode());
		assertEquals(paymentRequestPayer.getAmount(), responsePayer.getAmount());
		
		// now launch the same request again - this must fail
		payerBalanceBefore = UserAccountService.getInstance().getById(payerAccount.getId()).getBalance();
		payeeBalanceBefore = UserAccountService.getInstance().getById(payeeAccount.getId()).getBalance();
		response = TransactionService.getInstance().createTransaction(request);
		
		// same nofTransaction
		assertEquals(nofTransaction, getAllTransactions().size());
		
		//balance unchanged
		assertEquals(payerBalanceBefore, UserAccountService.getInstance().getById(payerAccount.getId()).getBalance());
		assertEquals(payeeBalanceBefore, UserAccountService.getInstance().getById(payeeAccount.getId()).getBalance());
		
		assertTrue(response.getPaymentResponsePayer().verify(KeyHandler.decodePublicKey(Constants.SERVER_KEY_PAIR.getPublicKey())));
		assertNull(response.getPaymentResponsePayee());
		
		responsePayer = response.getPaymentResponsePayer();
		assertEquals(ServerResponseStatus.DUPLICATE_REQUEST, responsePayer.getStatus());
		
		assertEquals(paymentRequestPayer.getUsernamePayer(), responsePayer.getUsernamePayer());
		assertEquals(paymentRequestPayer.getUsernamePayee(), responsePayer.getUsernamePayee());
		assertEquals(paymentRequestPayer.getCurrency().getCode(), responsePayer.getCurrency().getCode());
		assertEquals(paymentRequestPayer.getAmount(), responsePayer.getAmount());
	}
	
	@Test
	public void testCreateTransaction_FailNegativeBalance() throws Exception {
		UserAccount payerAccount = createAccountAndVerifyAndReload("TDAOT_7", "TDAOT_7@bitcoin.csg.uzh.ch", "my-password", TRANSACTION_AMOUNT.subtract(new BigDecimal(1)));
		UserAccount payeeAccount = createAccountAndVerifyAndReload("TDAOT_8", "TDAOT_8@bitcoin.csg.uzh.ch", "my-password", BigDecimal.ZERO);
		
		KeyPair payerKeyPair = KeyHandler.generateKeyPair();
		byte keyNumberPayer = UserAccountService.getInstance().saveUserPublicKey(payerAccount.getId(), PKIAlgorithm.DEFAULT, KeyHandler.encodePublicKey(payerKeyPair.getPublic()));
		
		PaymentRequest paymentRequestPayer = new PaymentRequest(
				PKIAlgorithm.DEFAULT, 
				keyNumberPayer, 
				payerAccount.getUsername(), 
				payeeAccount.getUsername(), 
				Currency.BTC, 
				Converter.getLongFromBigDecimal(TRANSACTION_AMOUNT),
				System.currentTimeMillis());
		
		paymentRequestPayer.sign(payerKeyPair.getPrivate());
		
		ServerPaymentRequest request = new ServerPaymentRequest(paymentRequestPayer);
		
		int nofTransaction = getAllTransactions().size();
		
		BigDecimal payerBalanceBefore = UserAccountService.getInstance().getById(payerAccount.getId()).getBalance();
		BigDecimal payeeBalanceBefore = UserAccountService.getInstance().getById(payeeAccount.getId()).getBalance();
		
		boolean exceptionThrown = false;
		try {
			TransactionService.getInstance().createTransaction(request);
		} catch (TransactionException e) {
			exceptionThrown = true;
			assertEquals(TransactionService.BALANCE, e.getMessage());
		}
		
		assertTrue(exceptionThrown);
		
		assertEquals(nofTransaction, getAllTransactions().size());
		
		assertEquals(payerBalanceBefore, UserAccountService.getInstance().getById(payerAccount.getId()).getBalance());
		assertEquals(payeeBalanceBefore, UserAccountService.getInstance().getById(payeeAccount.getId()).getBalance());
	}
	
	@Test
	public void testCreateTransaction_FailInvalidSignature() throws Exception {
		UserAccount payerAccount = createAccountAndVerifyAndReload("TDAOT_9", "TDAOT_9@bitcoin.csg.uzh.ch", "my-password", TRANSACTION_AMOUNT);
		UserAccount payeeAccount = createAccountAndVerifyAndReload("TDAOT_10", "TDAOT_10@bitcoin.csg.uzh.ch", "my-password", BigDecimal.ZERO);
		
		KeyPair payerKeyPair = KeyHandler.generateKeyPair();
		
		// payer signs with a key pair which the server does not know - this is equals to a invalid signature
		PaymentRequest paymentRequestPayer = new PaymentRequest(
				PKIAlgorithm.DEFAULT, 
				(byte) 12, 
				payerAccount.getUsername(), 
				payeeAccount.getUsername(), 
				Currency.BTC, 
				Converter.getLongFromBigDecimal(TRANSACTION_AMOUNT),
				System.currentTimeMillis());
		
		paymentRequestPayer.sign(payerKeyPair.getPrivate());
		
		ServerPaymentRequest request = new ServerPaymentRequest(paymentRequestPayer);
		
		int nofTransaction = getAllTransactions().size();
		
		BigDecimal payerBalanceBefore = UserAccountService.getInstance().getById(payerAccount.getId()).getBalance();
		BigDecimal payeeBalanceBefore = UserAccountService.getInstance().getById(payeeAccount.getId()).getBalance();
		
		boolean exceptionThrown = false;
		try {
			TransactionService.getInstance().createTransaction(request);
		} catch (TransactionException e) {
			exceptionThrown = true;
			assertEquals(TransactionService.PAYMENT_REFUSE, e.getMessage());
		}
		
		assertTrue(exceptionThrown);
		
		assertEquals(nofTransaction, getAllTransactions().size());
		
		assertEquals(payerBalanceBefore, UserAccountService.getInstance().getById(payerAccount.getId()).getBalance());
		assertEquals(payeeBalanceBefore, UserAccountService.getInstance().getById(payeeAccount.getId()).getBalance());
	}
	
	@Test
	public void testGetHistory() throws Exception {
		UserAccount payerAccount = createAccountAndVerifyAndReload("TDAOT_11", "TDAOT_11@bitcoin.csg.uzh.ch", "my-password", TRANSACTION_AMOUNT.multiply(new BigDecimal(4)));
		UserAccount payeeAccount = createAccountAndVerifyAndReload("TDAOT_12", "TDAOT_12@bitcoin.csg.uzh.ch", "my-password", BigDecimal.ZERO);
		
		KeyPair keyPairPayer = KeyHandler.generateKeyPair();
		KeyPair keyPairPayee = KeyHandler.generateKeyPair();
	
		byte keyNumberPayer = UserAccountService.getInstance().saveUserPublicKey(payerAccount.getId(), PKIAlgorithm.DEFAULT, KeyHandler.encodePublicKey(keyPairPayer.getPublic()));
		byte keyNumberPayee = UserAccountService.getInstance().saveUserPublicKey(payeeAccount.getId(), PKIAlgorithm.DEFAULT, KeyHandler.encodePublicKey(keyPairPayee.getPublic()));
		
		//transaction #1 - TDAOT_11 buys from TDAOT_12
		PaymentRequest paymentRequestPayer = new PaymentRequest(
				PKIAlgorithm.DEFAULT, 
				keyNumberPayer, 
				payerAccount.getUsername(), 
				payeeAccount.getUsername(), 
				Currency.BTC, 
				Converter.getLongFromBigDecimal(TRANSACTION_AMOUNT),
				System.currentTimeMillis());
		paymentRequestPayer.sign(keyPairPayer.getPrivate());
		
		PaymentRequest paymentRequestPayee = new PaymentRequest(
				PKIAlgorithm.DEFAULT, 
				keyNumberPayee, 
				payerAccount.getUsername(), 
				payeeAccount.getUsername(), 
				Currency.BTC, 
				Converter.getLongFromBigDecimal(TRANSACTION_AMOUNT),
				paymentRequestPayer.getTimestamp());
		paymentRequestPayee.sign(keyPairPayee.getPrivate());
		
		ServerPaymentRequest request = new ServerPaymentRequest(paymentRequestPayer, paymentRequestPayee);
		TransactionService.getInstance().createTransaction(request);
		
		//transaction #2 - TDAOT_12 buys from TDAOT_11
		paymentRequestPayer = new PaymentRequest(
				PKIAlgorithm.DEFAULT, 
				keyNumberPayee, 
				payeeAccount.getUsername(), 
				payerAccount.getUsername(), 
				Currency.BTC, 
				Converter.getLongFromBigDecimal(TRANSACTION_AMOUNT),
				System.currentTimeMillis());
		paymentRequestPayer.sign(keyPairPayee.getPrivate());
		
		paymentRequestPayee = new PaymentRequest(
				PKIAlgorithm.DEFAULT, 
				keyNumberPayer, 
				payeeAccount.getUsername(), 
				payerAccount.getUsername(), 
				Currency.BTC, 
				Converter.getLongFromBigDecimal(TRANSACTION_AMOUNT),
				paymentRequestPayer.getTimestamp());
		paymentRequestPayee.sign(keyPairPayer.getPrivate());
		
		request = new ServerPaymentRequest(paymentRequestPayer, paymentRequestPayee);
		TransactionService.getInstance().createTransaction(request);
		
		int nofTransactionsPayer = TransactionService.getInstance().getHistory(payerAccount.getUsername(), 0).size();
		assertEquals(2, nofTransactionsPayer);
		
		int nofTransactionsPayee = TransactionService.getInstance().getHistory(payeeAccount.getUsername(), 0).size();
		assertEquals(2, nofTransactionsPayee);
		
		
		UserAccount account3 = createAccountAndVerifyAndReload("TDAOT_13", "TDAOT_13@bitcoin.csg.uzh.ch", "my-password", BigDecimal.ZERO);
		KeyPair keyPairAccount3 = KeyHandler.generateKeyPair();
		
		byte keyNumberAccount3 = UserAccountService.getInstance().saveUserPublicKey(account3.getId(), PKIAlgorithm.DEFAULT, KeyHandler.encodePublicKey(keyPairAccount3.getPublic()));
		
		//transaction #3 - TDAOT_11 buys from TDAOT_13
		paymentRequestPayer = new PaymentRequest(
				PKIAlgorithm.DEFAULT, 
				keyNumberPayer, 
				payerAccount.getUsername(), 
				account3.getUsername(), 
				Currency.BTC, 
				Converter.getLongFromBigDecimal(TRANSACTION_AMOUNT),
				System.currentTimeMillis());
		paymentRequestPayer.sign(keyPairPayer.getPrivate());
		
		paymentRequestPayee = new PaymentRequest(
				PKIAlgorithm.DEFAULT, 
				keyNumberAccount3, 
				payerAccount.getUsername(), 
				account3.getUsername(), 
				Currency.BTC, 
				Converter.getLongFromBigDecimal(TRANSACTION_AMOUNT),
				paymentRequestPayer.getTimestamp());
		paymentRequestPayee.sign(keyPairAccount3.getPrivate());
		
		request = new ServerPaymentRequest(paymentRequestPayer, paymentRequestPayee);
		TransactionService.getInstance().createTransaction(request);
		
		int nofTransactions11 = TransactionService.getInstance().getHistory("TDAOT_11", 0).size();
		assertEquals(3, nofTransactions11);
		
		int nofTransactions12 = TransactionService.getInstance().getHistory("TDAOT_12", 0).size();
		assertEquals(2, nofTransactions12);
		
		int nofTransactions13 = TransactionService.getInstance().getHistory("TDAOT_13", 0).size();
		assertEquals(1, nofTransactions13);
	}
	
	@Test
	public void testGetHistory_testMaximumResult() throws Exception {
		UserAccount fromDB = createAccountAndVerifyAndReload("TDAOT_14", "TDAOT_14@bitcoin.csg.uzh.ch", "my-password", new BigDecimal("0.0"));
		UserAccount fromDB2 = createAccountAndVerifyAndReload("TDAOT_15", "TDAOT_15@bitcoin.csg.uzh.ch", "my-password", new BigDecimal("0.0"));
		
		assertEquals(0, TransactionService.getInstance().getHistory(fromDB.getUsername(), 0).size());
		
		DbTransaction tx;
		
		ArrayList<Date> dates = new ArrayList<Date>();
		int nofTransactions = 0;
		int additionalTx = 2;
		
		while (nofTransactions < Config.TRANSACTIONS_MAX_RESULTS+additionalTx) {
			tx = new DbTransaction();
			tx.setId(nofTransactions);
			Date d = new Date();
			dates.add(d);
			tx.setTimestamp(d);
			tx.setAmount(new BigDecimal("0.00001"));
			tx.setUsernamePayer(fromDB.getUsername());
			tx.setUsernamePayee(fromDB2.getUsername());
			
			TransactionDAO.createTransaction(tx, fromDB, fromDB2);
		
			fromDB = UserAccountService.getInstance().getByUsername(fromDB.getUsername());
			fromDB2 = UserAccountService.getInstance().getByUsername(fromDB2.getUsername());
			nofTransactions++;
		}
		
		assertTrue(nofTransactions > Config.TRANSACTIONS_MAX_RESULTS);
		ArrayList<HistoryTransaction> history = TransactionService.getInstance().getHistory(fromDB.getUsername(), 0);
		assertEquals(Config.TRANSACTIONS_MAX_RESULTS, history.size());
		
		//assert that the list is in descending order
		for (int i=0; i<history.size()-1; i++) {
			assertTrue(history.get(i).getTimestamp().compareTo(history.get(i+1).getTimestamp()) == 1);
		}
		
		//assert that the first and the second tx are not contained in the list (due to the order by clause)
		for (int i=0; i<history.size(); i++) {
			for (int j=0; j<additionalTx; j++) {
				assertFalse(dates.get(j).equals(history.get(i).getTimestamp()));
			}
		}
		
		//get the secod page
		ArrayList<HistoryTransaction> history2 = TransactionService.getInstance().getHistory(fromDB.getUsername(), 1);
		assertEquals(additionalTx, history2.size());
		
		//assert that the first and the second tx are now in the list, since we fetched the second page
		for (int i=0; i<additionalTx; i++) {
			boolean isInList = false;
			for (int j=0; j<history2.size(); j++) {
				if (dates.get(i).equals(history2.get(j).getTimestamp())) {
					isInList = true;
					break;
				}
			}
			assertTrue(isInList);
		}
		
		//test get the number of results
		long historyCount = TransactionService.getInstance().getHistoryCount(fromDB.getUsername());
		assertEquals(Config.TRANSACTIONS_MAX_RESULTS+additionalTx, historyCount);
	}
	
	private UserAccount createAccountAndVerifyAndReload(String username, String email, String password, BigDecimal bigDecimal) throws Exception {
		UserAccount buyerAccount = new UserAccount(username, email, password);
		assertTrue(UserAccountService.getInstance().createAccount(buyerAccount));
		buyerAccount = UserAccountService.getInstance().getByUsername(buyerAccount.getUsername());
		buyerAccount.setEmailVerified(true);
		buyerAccount.setBalance(bigDecimal);
		UserAccountDAO.updateAccount(buyerAccount);
		return buyerAccount;
	}

	@SuppressWarnings("unchecked")
	private List<DbTransaction> getAllTransactions() {
		SessionFactory sessionFactory = HibernateUtil.getSessionFactory();
		Session session = sessionFactory.openSession();
		session.beginTransaction();
		List<DbTransaction> list = session.createQuery("from DB_TRANSACTION").list();
		
		session.close();
		return list;
	}
	
}
