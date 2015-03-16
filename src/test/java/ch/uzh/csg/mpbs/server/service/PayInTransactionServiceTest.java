package ch.uzh.csg.mpbs.server.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.math.BigDecimal;
import java.security.KeyPair;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import ch.uzh.csg.coinblesk.customserialization.PKIAlgorithm;
import ch.uzh.csg.coinblesk.keys.CustomKeyPair;
import ch.uzh.csg.coinblesk.model.HistoryPayInTransaction;
import ch.uzh.csg.coinblesk.server.clientinterface.IPayInTransaction;
import ch.uzh.csg.coinblesk.server.clientinterface.IUserAccount;
import ch.uzh.csg.coinblesk.server.domain.PayInTransaction;
import ch.uzh.csg.coinblesk.server.domain.UserAccount;
import ch.uzh.csg.coinblesk.server.security.KeyHandler;
import ch.uzh.csg.coinblesk.server.service.UserAccountService;
import ch.uzh.csg.coinblesk.server.util.BitcoindController;
import ch.uzh.csg.coinblesk.server.util.Config;
import ch.uzh.csg.coinblesk.server.util.Constants;
import ch.uzh.csg.coinblesk.server.util.exceptions.EmailAlreadyExistsException;
import ch.uzh.csg.coinblesk.server.util.exceptions.InvalidEmailException;
import ch.uzh.csg.coinblesk.server.util.exceptions.InvalidUrlException;
import ch.uzh.csg.coinblesk.server.util.exceptions.InvalidUsernameException;
import ch.uzh.csg.coinblesk.server.util.exceptions.UserAccountNotFoundException;
import ch.uzh.csg.coinblesk.server.util.exceptions.UsernameAlreadyExistsException;

import com.azazar.bitcoin.jsonrpcclient.Bitcoin.RawTransaction;
import com.azazar.bitcoin.jsonrpcclient.Bitcoin.Transaction;
import com.azazar.bitcoin.jsonrpcclient.BitcoinException;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {
		"classpath:context.xml",
		"classpath:test-database.xml"})
public class PayInTransactionServiceTest {

	private static boolean initialized = false;
	private static UserAccount test61;
	private static UserAccount test62;
	
	@Autowired
	private IUserAccount userAccountService;
	
	@Autowired
	private IPayInTransaction payInTransactionService;

	@Before
	public void setUp() throws Exception {
		BitcoindController.TESTING = true;
		UserAccountService.enableTestingMode();

		if (!initialized) {
			test61 = new UserAccount("test61@https://mbps.csg.uzh.ch", "test61@bitcoin.csg.uzh.chs", "asdf");
			test62 = new UserAccount("test62@https://mbps.csg.uzh.ch", "test62@bitcoin.csg.uzh.ch", "asdf");

			KeyPair keypair = KeyHandler.generateKeyPair();

			Constants.SERVER_KEY_PAIR = new CustomKeyPair(PKIAlgorithm.DEFAULT.getCode(), (byte) 1, KeyHandler.encodePublicKey(keypair.getPublic()), KeyHandler.encodePrivateKey(keypair.getPrivate()));

			initialized = true;
		}
	}
	
	@After
	public void tearDown(){
		UserAccountService.disableTestingMode();
	}

	private void createAccountAndVerifyAndReload(UserAccount userAccount, BigDecimal balance) throws UsernameAlreadyExistsException, UserAccountNotFoundException, BitcoinException, InvalidUsernameException, InvalidEmailException, EmailAlreadyExistsException, InvalidUrlException {
		assertTrue(userAccountService.createAccount(userAccount));
		userAccount = userAccountService.getByUsername(userAccount.getUsername());
		userAccount.setEmailVerified(true);
		userAccount.setBalance(balance);
		userAccountService.updateAccount(userAccount);
	}

	@Test
	public void testCheck() throws Exception{
		createAccountAndVerifyAndReload(test61,BigDecimal.ZERO);
		UserAccount userAccount = userAccountService.getByUsername(test61.getUsername());
		userAccount.setPaymentAddress("asdfjklqwertuiopyxcvb");
		userAccountService.updateAccount(userAccount);
		UserAccount fromDB = userAccountService.getByUsername(test61.getUsername());
		final String paymentAddress = fromDB.getPaymentAddress();
		Transaction transaction = new Transaction() {

			@Override
			public String txId() {
				return "1";
			}

			@Override
			public Date timeReceived() {
				return new Date();
			}

			@Override
			public Date time() {
				return new Date();
			}

			@Override
			public RawTransaction raw() {
				return null;
			}

			@Override
			public double fee() {
				return 0;
			}

			@Override
			public int confirmations() {
				return 15;
			}

			@Override
			public String commentTo() {
				return null;
			}

			@Override
			public String comment() {
				return null;
			}

			@Override
			public String category() {
				return "receive";
			}

			@Override
			public Date blockTime() {
				return null;
			}

			@Override
			public int blockIndex() {
				return 0;
			}

			@Override
			public String blockHash() {
				return null;
			}

			@Override
			public double amount() {
				return 1;
			}

			@Override
			public String address() {
				return paymentAddress;
			}

			@Override
			public String account() {
				return null;
			}
		};

		assertTrue(BigDecimal.ZERO.compareTo(fromDB.getBalance())==0);

		payInTransactionService.create(transaction);
		
		fromDB = userAccountService.getByUsername(test61.getUsername());
		assertTrue(BigDecimal.ONE.compareTo(fromDB.getBalance())==0);
	}
	
	@Test
	public void testGetHistory() throws Exception {
		createAccountAndVerifyAndReload(test62, BigDecimal.ZERO); 
		
		UserAccount fromDB = userAccountService.getByUsername(test62.getUsername());
		
		assertEquals(0, payInTransactionService.getHistory(fromDB.getUsername(), 0).size());
		
		
		
		ArrayList<Date> dates = new ArrayList<Date>();
		int nofTransactions = 0;
		int additionalTx = 2;
		
		while (nofTransactions < Config.PAY_INS_MAX_RESULTS+additionalTx) {
			PayInTransaction tx = new PayInTransaction();
			//tx.setId(nofTransactions);
			Date d = new Date();
			dates.add(d);
			tx.setTimestamp(d);
			tx.setAmount(new BigDecimal("0.00001"));
			tx.setUserID(fromDB.getId());
			tx.setTransactionID(Integer.toString(nofTransactions));
			
			payInTransactionService.createPayInTransaction(tx);
			
			nofTransactions++;
		}
		
		System.err.println("created "+nofTransactions);
		
		assertTrue(nofTransactions > Config.PAY_INS_MAX_RESULTS);
		List<HistoryPayInTransaction> history = payInTransactionService.getHistory(fromDB.getUsername(), 0);
		assertEquals(Config.PAY_INS_MAX_RESULTS, history.size());
		
		//assert that the list is in descending order
		for (int i=0; i<history.size()-1; i++) {
			assertTrue(history.get(i).getTimestamp().compareTo(history.get(i+1).getTimestamp()) == 1);
		}
		
		//assert that the first and the second tx are not contained in the list (due to the order by clause)
		for (int i=0; i<history.size(); i++) {
			for (int j=0; j<additionalTx; j++) {
				assertFalse(dates.get(j).compareTo(history.get(i).getTimestamp()) == 0);
			}
		}
		
		//get the secod page
		List<HistoryPayInTransaction> history2 = payInTransactionService.getHistory(fromDB.getUsername(), 1);
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
		long historyCount = payInTransactionService.getHistoryCount(fromDB.getUsername());
		assertEquals(Config.PAY_INS_MAX_RESULTS+additionalTx, historyCount);
	}

}