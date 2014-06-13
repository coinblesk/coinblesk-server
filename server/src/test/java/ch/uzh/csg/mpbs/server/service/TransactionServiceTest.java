package ch.uzh.csg.mpbs.server.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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
		assertTrue(response.getPaymentResponsePayee().verify(KeyHandler.decodePublicKey(Constants.SERVER_KEY_PAIR.getPublicKey())));
		
		PaymentResponse responsePayer = response.getPaymentResponsePayer();
		PaymentResponse responsePayee = response.getPaymentResponsePayee();
		
		assertEquals(paymentRequestPayer.getAmount(), responsePayer.getAmount());
		assertEquals(paymentRequestPayer.getUsernamePayer(), responsePayer.getUsernamePayer());
		assertEquals(paymentRequestPayer.getUsernamePayee(), responsePayer.getUsernamePayee());

		assertEquals(paymentRequestPayee.getAmount(), responsePayee.getAmount());
		assertEquals(paymentRequestPayee.getUsernamePayer(), responsePayee.getUsernamePayer());
		assertEquals(paymentRequestPayee.getUsernamePayee(), responsePayee.getUsernamePayee());

	}
	
	@Test
	public void testCreateTransaction_similarToClient() throws Exception {
		UserAccount buyerAccount = createAccountAndVerifyAndReload("TDAOT_20", "TDAOT_20@bitcoin.csg.uzh.ch", "my-password", TRANSACTION_AMOUNT.add(new BigDecimal("1.00000000")));
		UserAccount sellerAccount = createAccountAndVerifyAndReload("TDAOT_21", "TDAOT_21@bitcoin.csg.uzh.ch", "my-password", BigDecimal.ZERO);
		
		//TODO jeton: adopt to new stuff!
//		//create transaction object from seller
//		Transaction sellerTransaction = new Transaction();
//		sellerTransaction.setAmount(TRANSACTION_AMOUNT);
//		sellerTransaction.setSellerUsername(sellerAccount.getUsername());
//		sellerTransaction.setTransactionNrSeller(sellerAccount.getTransactionNumber());
//		byte[] serializedSellerTransaction = serialize(sellerTransaction);
//		
//		//buyer receives the serialized seller transaction object
//		Transaction buyerTransaction = deserialize(serializedSellerTransaction);
//		//buyer adds his information to the tx object
//		buyerTransaction.setBuyerUsername(buyerAccount.getUsername());
//		buyerTransaction.setTransactionNrBuyer(buyerAccount.getTransactionNumber());
//		//buyer signes the object with his private key
//		SignedObject signTransactionBuyer = KeyHandler.signTransaction(buyerTransaction, buyerAccount.getPrivateKey());
//		//buyer serializes the signed object before sending to the seller
//		byte[] serializedBuyerTransaction = Serializer.serialize(signTransactionBuyer);
//		
//		//seller receives the serialized buyer transaction object
//		SignedObject signedObjectFromBuyer = Serializer.deserialize(serializedBuyerTransaction);
//		Transaction transactionFromBuyer = KeyHandler.retrieveTransaction(signedObjectFromBuyer);
//		//seller reads the buyer infos and completes his transaction object
//		sellerTransaction = new Transaction();
//		sellerTransaction.setAmount(TRANSACTION_AMOUNT);
//		sellerTransaction.setSellerUsername(sellerAccount.getUsername());
//		sellerTransaction.setTransactionNrSeller(sellerAccount.getTransactionNumber());
//		sellerTransaction.setBuyerUsername(transactionFromBuyer.getBuyerUsername());
//		sellerTransaction.setTransactionNrBuyer(transactionFromBuyer.getTransactionNrBuyer());
//		//seller signs the transaction object
//		SignedObject signedObjectSeller = KeyHandler.signTransaction(sellerTransaction, sellerAccount.getPrivateKey());
//		
//		//seller creates CreateTransactionTransferObject to send to server
//		Pair<SignedObject> txRequest = new Pair<SignedObject>(signedObjectFromBuyer, signedObjectSeller);
//		
//		int nofTransaction = getAllTransactions().size();
//		
//		long buyerTxNrBefore = buyerAccount.getTransactionNumber();
//		long sellerTxNrBefore = sellerAccount.getTransactionNumber();
//		BigDecimal buyerBalanceBefore = buyerAccount.getBalance();
//		BigDecimal sellerBalanceBefore = sellerAccount.getBalance();
//		
//		SignedObject txResponse = TransactionService.getInstance().createTransaction(txRequest);
//		assertEquals(nofTransaction+1, getAllTransactions().size());
//		
//		UserAccount buyerAccountUpdated = UserAccountService.getInstance().getById(buyerAccount.getId());
//		UserAccount sellerAccountUpdated = UserAccountService.getInstance().getById(sellerAccount.getId());
//		
//		assertEquals(buyerTxNrBefore+1, buyerAccountUpdated.getTransactionNumber());
//		assertEquals(sellerTxNrBefore+1, sellerAccountUpdated.getTransactionNumber());
//		assertEquals(0, buyerBalanceBefore.subtract(TRANSACTION_AMOUNT).compareTo(buyerAccountUpdated.getBalance()));
//		assertEquals(0, sellerBalanceBefore.add(TRANSACTION_AMOUNT).compareTo(sellerAccountUpdated.getBalance()));
//		
//		assertTrue(KeyHandler.verifyObject(txResponse, Constants.PUBLICKEY));
//		
//		Transaction tx = KeyHandler.retrieveTransaction(txResponse);
//		
//		assertEquals(buyerTransaction.getAmount(), tx.getAmount());
//		assertEquals(buyerTransaction.getBuyerUsername(), tx.getBuyerUsername());
//		assertEquals(buyerTransaction.getSellerUsername(), tx.getSellerUsername());
//		assertEquals(buyerTransaction.getTransactionNrBuyer(), tx.getTransactionNrBuyer());
//		assertEquals(buyerTransaction.getTransactionNrSeller(), tx.getTransactionNrSeller());
//		
//		assertEquals(sellerTransaction.getAmount(), tx.getAmount());
//		assertEquals(sellerTransaction.getBuyerUsername(), tx.getBuyerUsername());
//		assertEquals(sellerTransaction.getSellerUsername(), tx.getSellerUsername());
//		assertEquals(sellerTransaction.getTransactionNrBuyer(), tx.getTransactionNrBuyer());
//		assertEquals(sellerTransaction.getTransactionNrSeller(), tx.getTransactionNrSeller());
	}
	
//	private byte[] serialize(Transaction sellerTransaction) throws IOException {
//		ByteArrayOutputStream baos = new ByteArrayOutputStream();
//	    ObjectOutputStream oos = new ObjectOutputStream(baos);
//	    oos.writeObject(sellerTransaction);
//	    return baos.toByteArray();
//	}
//	
//	private Transaction deserialize(byte[] bytes) throws IOException, ClassNotFoundException {
//		ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
//		ObjectInputStream ois = new ObjectInputStream(bais);
//		return (Transaction) ois.readObject();
//	}
	
	@Test
	public void testCreateTransaction_FailNegativeBalance() throws Exception {
		UserAccount buyerAccount = createAccountAndVerifyAndReload("TDAOT_3", "TDAOT_3@bitcoin.csg.uzh.ch", "my-password", TRANSACTION_AMOUNT.subtract(new BigDecimal(1)));
		UserAccount sellerAccount = createAccountAndVerifyAndReload("TDAOT_4", "TDAOT_4@bitcoin.csg.uzh.ch", "my-password", BigDecimal.ZERO);
		
		//TODO jeton: adopt to new stuff!
//		Transaction buyerTransaction = new Transaction(buyerAccount.getTransactionNumber(), sellerAccount.getTransactionNumber(), buyerAccount.getUsername(), sellerAccount.getUsername(), TRANSACTION_AMOUNT, "", BigDecimal.ZERO);
//		Transaction sellerTransaction = new Transaction(buyerAccount.getTransactionNumber(), sellerAccount.getTransactionNumber(), buyerAccount.getUsername(), sellerAccount.getUsername(), TRANSACTION_AMOUNT, "", BigDecimal.ZERO);
//		
//		SignedObject signedTransactionBuyer = KeyHandler.signTransaction(buyerTransaction, buyerAccount.getPrivateKey());
//		SignedObject signedTransactionSeller = KeyHandler.signTransaction(sellerTransaction, sellerAccount.getPrivateKey());
//
//		int nofTransactions = getAllTransactions().size();
//		
//		long buyerTxNrBefore = buyerAccount.getTransactionNumber();
//		long sellerTxNrBefore = sellerAccount.getTransactionNumber();
//		BigDecimal buyerBalanceBefore = buyerAccount.getBalance();
//		BigDecimal sellerBalanceBefore = sellerAccount.getBalance();
//		
//		try {
//			TransactionService.getInstance().createTransaction(new Pair<SignedObject>(signedTransactionBuyer, signedTransactionSeller));
//		} catch (TransactionException e) {
//			assertEquals(TransactionService.BALANCE, e.getMessage());
//		}
//		
//		assertEquals(nofTransactions, getAllTransactions().size());
//		
//		UserAccount buyerAccountUpdated = UserAccountService.getInstance().getById(buyerAccount.getId());
//		UserAccount sellerAccountUpdated = UserAccountService.getInstance().getById(sellerAccount.getId());
//		
//		assertEquals(buyerTxNrBefore, buyerAccountUpdated.getTransactionNumber());
//		assertEquals(sellerTxNrBefore, sellerAccountUpdated.getTransactionNumber());
//		assertEquals(0, buyerBalanceBefore.compareTo(buyerAccountUpdated.getBalance()));
//		assertEquals(0, sellerBalanceBefore.compareTo(sellerAccountUpdated.getBalance()));
	}
	
	@Test
	public void testCreateTransaction_FailNotIdenticRequests() throws Exception {
		UserAccount buyerAccount = createAccountAndVerifyAndReload("TDAOT_5", "TDAOT_5@bitcoin.csg.uzh.ch", "my-password", TRANSACTION_AMOUNT);
		UserAccount sellerAccount = createAccountAndVerifyAndReload("TDAOT_6", "TDAOT_6@bitcoin.csg.uzh.ch", "my-password", BigDecimal.ZERO);
		
		//TODO jeton: adopt to new stuff!
//		Transaction buyerTransaction = new Transaction(buyerAccount.getTransactionNumber(), sellerAccount.getTransactionNumber(), buyerAccount.getUsername(), sellerAccount.getUsername(), TRANSACTION_AMOUNT, "", BigDecimal.ZERO);
//		//seller enters more than the buyer agrees to pay
//		Transaction sellerTransaction = new Transaction(buyerAccount.getTransactionNumber(), sellerAccount.getTransactionNumber(), buyerAccount.getUsername(), sellerAccount.getUsername(), TRANSACTION_AMOUNT.add(new BigDecimal(20)), "", BigDecimal.ZERO);
//		
//		SignedObject signedTransactionBuyer = KeyHandler.signTransaction(buyerTransaction, buyerAccount.getPrivateKey());
//		SignedObject signedTransactionSeller = KeyHandler.signTransaction(sellerTransaction, sellerAccount.getPrivateKey());
//
//		int nofTransactions = getAllTransactions().size();
//		
//		long buyerTxNrBefore = buyerAccount.getTransactionNumber();
//		long sellerTxNrBefore = sellerAccount.getTransactionNumber();
//		BigDecimal buyerBalanceBefore = buyerAccount.getBalance();
//		BigDecimal sellerBalanceBefore = sellerAccount.getBalance();
//		
//		try {
//			TransactionService.getInstance().createTransaction(new Pair<SignedObject>(signedTransactionBuyer, signedTransactionSeller));
//		} catch (TransactionException e) {
//			assertEquals(TransactionService.PAYMENT_REFUSE, e.getMessage());
//		}
//		
//		assertEquals(nofTransactions, getAllTransactions().size());
//		
//		UserAccount buyerAccountUpdated = UserAccountService.getInstance().getById(buyerAccount.getId());
//		UserAccount sellerAccountUpdated = UserAccountService.getInstance().getById(sellerAccount.getId());
//		
//		assertEquals(buyerTxNrBefore, buyerAccountUpdated.getTransactionNumber());
//		assertEquals(sellerTxNrBefore, sellerAccountUpdated.getTransactionNumber());
//		assertEquals(0,buyerBalanceBefore.compareTo(buyerAccountUpdated.getBalance()));
//		assertEquals(0,sellerBalanceBefore.compareTo(sellerAccountUpdated.getBalance()));
	}
	
	@Test
	public void testCreateTransaction_FailBadSignature() throws Exception {
		UserAccount buyerAccount = createAccountAndVerifyAndReload("TDAOT_7", "TDAOT_7@bitcoin.csg.uzh.ch", "my-password", TRANSACTION_AMOUNT);
		UserAccount sellerAccount = createAccountAndVerifyAndReload("TDAOT_8", "TDAOT_8@bitcoin.csg.uzh.ch", "my-password", BigDecimal.ZERO);
		
		//TODO jeton: adopt to new stuff!
//		Transaction sellerTransaction = new Transaction(buyerAccount.getTransactionNumber(), sellerAccount.getTransactionNumber(), buyerAccount.getUsername(), sellerAccount.getUsername(), TRANSACTION_AMOUNT.add(new BigDecimal(20)), "", BigDecimal.ZERO);
//		
//		SignedObject signedTransactionSeller = KeyHandler.signTransaction(sellerTransaction, sellerAccount.getPrivateKey());
//		
//		int nofTransactions = getAllTransactions().size();
//		
//		long buyerTxNrBefore = buyerAccount.getTransactionNumber();
//		long sellerTxNrBefore = sellerAccount.getTransactionNumber();
//		BigDecimal buyerBalanceBefore = buyerAccount.getBalance();
//		BigDecimal sellerBalanceBefore = sellerAccount.getBalance();
//		
//		try {
//			// Seller drops the signed transaction request by the buyer and
//			// sends only his transaction request, which has an higher amount
//			// (the seller would steal money from the buyer)
//			// or signature is simply not valid
//			TransactionService.getInstance().createTransaction(new Pair<SignedObject>(signedTransactionSeller, signedTransactionSeller));
//		} catch (TransactionException e) {
//			assertEquals(TransactionService.PAYMENT_REFUSE, e.getMessage());
//		}
//		
//		assertEquals(nofTransactions, getAllTransactions().size());
//		
//		UserAccount buyerAccountUpdated = UserAccountService.getInstance().getById(buyerAccount.getId());
//		UserAccount sellerAccountUpdated = UserAccountService.getInstance().getById(sellerAccount.getId());
//		
//		assertEquals(buyerTxNrBefore, buyerAccountUpdated.getTransactionNumber());
//		assertEquals(sellerTxNrBefore, sellerAccountUpdated.getTransactionNumber());
//		assertEquals(0,buyerBalanceBefore.compareTo(buyerAccountUpdated.getBalance()));
//		assertEquals(0,sellerBalanceBefore.compareTo(sellerAccountUpdated.getBalance()));
	}
	
	@Test
	public void testCreateTransaction_FailTransactionNumberInconsistency() throws Exception {
		UserAccount buyerAccount = createAccountAndVerifyAndReload("TDAOT_9", "TDAOT_9@bitcoin.csg.uzh.ch", "my-password", TRANSACTION_AMOUNT);
		UserAccount sellerAccount = createAccountAndVerifyAndReload("TDAOT_10", "TDAOT_10@bitcoin.csg.uzh.ch", "my-password", BigDecimal.ZERO);
		
		//TODO jeton: adopt to new stuff!
//		Transaction buyerTransaction = new Transaction(buyerAccount.getTransactionNumber(), sellerAccount.getTransactionNumber(), buyerAccount.getUsername(), sellerAccount.getUsername(), TRANSACTION_AMOUNT, "", BigDecimal.ZERO);
//		Transaction sellerTransaction = new Transaction(buyerAccount.getTransactionNumber(), sellerAccount.getTransactionNumber(), buyerAccount.getUsername(), sellerAccount.getUsername(), TRANSACTION_AMOUNT, "", BigDecimal.ZERO);
//		
//		SignedObject signedTransactionBuyer = KeyHandler.signTransaction(buyerTransaction, buyerAccount.getPrivateKey());
//		SignedObject signedTransactionSeller = KeyHandler.signTransaction(sellerTransaction, sellerAccount.getPrivateKey());
//
//		int nofTransactions = getAllTransactions().size();
//		
//		long buyerTxNrBefore = buyerAccount.getTransactionNumber();
//		long sellerTxNrBefore = sellerAccount.getTransactionNumber();
//		BigDecimal buyerBalanceBefore = buyerAccount.getBalance();
//		BigDecimal sellerBalanceBefore = sellerAccount.getBalance();
//		
//		TransactionService.getInstance().createTransaction(new Pair<SignedObject>(signedTransactionBuyer, signedTransactionSeller));
//		
//		//seller does not receive the conformation and resends the request
//		try {
//			TransactionService.getInstance().createTransaction(new Pair<SignedObject>(signedTransactionBuyer, signedTransactionSeller));
//		} catch (TransactionException e) {
//			assertEquals(TransactionService.PAYMENT_REFUSE, e.getMessage());
//		}
//		
//		assertEquals(nofTransactions+1, getAllTransactions().size());
//		
//		UserAccount buyerAccountUpdated = UserAccountService.getInstance().getById(buyerAccount.getId());
//		UserAccount sellerAccountUpdated = UserAccountService.getInstance().getById(sellerAccount.getId());
//		
//		assertEquals(buyerTxNrBefore+1, buyerAccountUpdated.getTransactionNumber());
//		assertEquals(sellerTxNrBefore+1, sellerAccountUpdated.getTransactionNumber());
//		assertEquals(0,buyerBalanceBefore.subtract(TRANSACTION_AMOUNT).compareTo(buyerAccountUpdated.getBalance()));
//		assertEquals(0,sellerBalanceBefore.add(TRANSACTION_AMOUNT).compareTo(sellerAccountUpdated.getBalance()));
	}

	@Test
	public void testGetHistory() throws Exception {
		UserAccount buyerAccount = createAccountAndVerifyAndReload("TDAOT_11", "TDAOT_11@bitcoin.csg.uzh.ch", "my-password", TRANSACTION_AMOUNT.multiply(new BigDecimal(4)));
		UserAccount sellerAccount = createAccountAndVerifyAndReload("TDAOT_12", "TDAOT_12@bitcoin.csg.uzh.ch", "my-password", BigDecimal.ZERO);
		
		//TODO jeton: adopt to new stuff!
//		//transaction #1 - TDAOT_11 buys from TDAOT_12
//		Transaction buyerTransaction = new Transaction(buyerAccount.getTransactionNumber(), sellerAccount.getTransactionNumber(), buyerAccount.getUsername(), sellerAccount.getUsername(), TRANSACTION_AMOUNT, "", BigDecimal.ZERO);
//		Transaction sellerTransaction = new Transaction(buyerAccount.getTransactionNumber(), sellerAccount.getTransactionNumber(), buyerAccount.getUsername(), sellerAccount.getUsername(), TRANSACTION_AMOUNT, "", BigDecimal.ZERO);
//		SignedObject signedTransactionBuyer = KeyHandler.signTransaction(buyerTransaction, buyerAccount.getPrivateKey());
//		SignedObject signedTransactionSeller = KeyHandler.signTransaction(sellerTransaction, sellerAccount.getPrivateKey());
//		TransactionService.getInstance().createTransaction(new Pair<SignedObject>(signedTransactionBuyer, signedTransactionSeller));
//		
//		//reload accounts
//		buyerAccount = UserAccountService.getInstance().getByUsername("TDAOT_12");
//		sellerAccount = UserAccountService.getInstance().getByUsername("TDAOT_11");
//		
//		//transaction #2 - TDAOT_12 buys from TDAOT_11
//		Transaction buyerTransaction2 = new Transaction(buyerAccount.getTransactionNumber(), sellerAccount.getTransactionNumber(), buyerAccount.getUsername(), sellerAccount.getUsername(), TRANSACTION_AMOUNT, "", BigDecimal.ZERO);
//		Transaction sellerTransaction2 = new Transaction(buyerAccount.getTransactionNumber(), sellerAccount.getTransactionNumber(), buyerAccount.getUsername(), sellerAccount.getUsername(), TRANSACTION_AMOUNT, "", BigDecimal.ZERO);
//		SignedObject signedTransactionBuyer2 = KeyHandler.signTransaction(buyerTransaction2, buyerAccount.getPrivateKey());
//		SignedObject signedTransactionSeller2 = KeyHandler.signTransaction(sellerTransaction2, sellerAccount.getPrivateKey());
//		TransactionService.getInstance().createTransaction(new Pair<SignedObject>(signedTransactionBuyer2, signedTransactionSeller2));
//		
//		//reload accounts
//		buyerAccount = UserAccountService.getInstance().getByUsername("TDAOT_12");
//		sellerAccount = UserAccountService.getInstance().getByUsername("TDAOT_11");
//		
//		int nofTransactionsBuyer = TransactionService.getInstance().getHistory(buyerAccount.getUsername(), 0).size();
//		assertEquals(2, nofTransactionsBuyer);
//		
//		int nofTransactionsSeller = TransactionService.getInstance().getHistory(sellerAccount.getUsername(), 0).size();
//		assertEquals(2, nofTransactionsSeller);
//		
//		UserAccount account3 = new UserAccount("TDAOT_13", "TDAOT_13@bitcoin.csg.uzh.ch", "my-password");
//		UserAccountService.getInstance().createAccount(account3);
//		account3 = UserAccountService.getInstance().getByUsername("TDAOT_13");
//		
//		//reload accounts, and change roles, buyer becomes seller and vice versa
//		buyerAccount = UserAccountService.getInstance().getByUsername("TDAOT_11");
//		sellerAccount = UserAccountService.getInstance().getByUsername("TDAOT_13");
//		
//		//transaction #3 - TDAOT_11 buys from TDAOT_13
//		Transaction buyerTransaction3 = new Transaction(buyerAccount.getTransactionNumber(), sellerAccount.getTransactionNumber(), buyerAccount.getUsername(), sellerAccount.getUsername(), TRANSACTION_AMOUNT, "", BigDecimal.ZERO);
//		Transaction sellerTransaction3 = new Transaction(buyerAccount.getTransactionNumber(), sellerAccount.getTransactionNumber(), buyerAccount.getUsername(), sellerAccount.getUsername(), TRANSACTION_AMOUNT, "", BigDecimal.ZERO);
//		SignedObject signedTransactionBuyer3 = KeyHandler.signTransaction(buyerTransaction3, buyerAccount.getPrivateKey());
//		SignedObject signedTransactionSeller3 = KeyHandler.signTransaction(sellerTransaction3, sellerAccount.getPrivateKey());
//		TransactionService.getInstance().createTransaction(new Pair<SignedObject>(signedTransactionBuyer3, signedTransactionSeller3));
//
//		//reload accounts, and change roles, buyer becomes seller and vice versa
//		buyerAccount = UserAccountService.getInstance().getByUsername("TDAOT_11");
//		sellerAccount = UserAccountService.getInstance().getByUsername("TDAOT_13");
//
//		int nofTransactions11 = TransactionService.getInstance().getHistory("TDAOT_11", 0).size();
//		assertEquals(3, nofTransactions11);
//		
//		int nofTransactions12 = TransactionService.getInstance().getHistory("TDAOT_12", 0).size();
//		assertEquals(2, nofTransactions12);
//		
//		int nofTransactions13 = TransactionService.getInstance().getHistory("TDAOT_13", 0).size();
//		assertEquals(1, nofTransactions13);
	}
	
	@Test
	public void testGetHistory_testMaximumResult() throws Exception {
		UserAccount fromDB = createAccountAndVerifyAndReload("TDAOT_30", "TDAOT_30@bitcoin.csg.uzh.ch", "my-password", new BigDecimal("0.0"));
		UserAccount fromDB2 = createAccountAndVerifyAndReload("TDAOT_31", "TDAOT_31@bitcoin.csg.uzh.ch", "my-password", new BigDecimal("0.0"));
		
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
