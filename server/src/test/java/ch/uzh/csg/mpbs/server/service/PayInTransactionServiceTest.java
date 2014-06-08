package ch.uzh.csg.mpbs.server.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.math.BigDecimal;
import java.security.KeyPair;
import java.util.ArrayList;
import java.util.Date;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import ch.uzh.csg.mbps.model.HistoryPayInTransaction;
import ch.uzh.csg.mbps.server.dao.PayInTransactionDAO;
import ch.uzh.csg.mbps.server.dao.UserAccountDAO;
import ch.uzh.csg.mbps.server.domain.PayInTransaction;
import ch.uzh.csg.mbps.server.domain.UserAccount;
import ch.uzh.csg.mbps.server.security.KeyHandler;
import ch.uzh.csg.mbps.server.service.PayInTransactionService;
import ch.uzh.csg.mbps.server.service.UserAccountService;
import ch.uzh.csg.mbps.server.util.Config;
import ch.uzh.csg.mbps.server.util.Constants;
import ch.uzh.csg.mbps.server.util.exceptions.EmailAlreadyExistsException;
import ch.uzh.csg.mbps.server.util.exceptions.InvalidEmailException;
import ch.uzh.csg.mbps.server.util.exceptions.InvalidUsernameException;
import ch.uzh.csg.mbps.server.util.exceptions.UserAccountNotFoundException;
import ch.uzh.csg.mbps.server.util.exceptions.UsernameAlreadyExistsException;

import com.azazar.bitcoin.jsonrpcclient.Bitcoin.RawTransaction;
import com.azazar.bitcoin.jsonrpcclient.Bitcoin.Transaction;
import com.azazar.bitcoin.jsonrpcclient.BitcoinException;

public class PayInTransactionServiceTest {

	private static boolean initialized = false;
	private static UserAccount test61;
	private static UserAccount test62;

	@Before
	public void setUp() throws Exception {
		UserAccountService.enableTestingMode();

		if (!initialized) {
			test61 = new UserAccount("test61", "test61@bitcoin.csg.uzh.chs", "asdf");
			test62 = new UserAccount("test62", "test62@bitcoin.csg.uzh.ch", "asdf");

			KeyPair keypair = KeyHandler.generateKeyPair();

			Constants.PRIVATEKEY = keypair.getPrivate();
			Constants.PUBLICKEY = keypair.getPublic();

			initialized = true;
		}
	}
	
	@After
	public void tearDown(){
		UserAccountService.disableTestingMode();
	}

	private void createAccountAndVerifyAndReload(UserAccount userAccount, BigDecimal balance) throws UsernameAlreadyExistsException, UserAccountNotFoundException, BitcoinException, InvalidUsernameException, InvalidEmailException, EmailAlreadyExistsException {
		assertTrue(UserAccountService.getInstance().createAccount(userAccount));
		userAccount = UserAccountService.getInstance().getByUsername(userAccount.getUsername());
		userAccount.setEmailVerified(true);
		userAccount.setBalance(balance);
		UserAccountDAO.updateAccount(userAccount);
	}

	@Test
	public void testCheck() throws Exception{
		createAccountAndVerifyAndReload(test61,BigDecimal.ZERO);
		UserAccount userAccount = UserAccountService.getInstance().getByUsername(test61.getUsername());
		userAccount.setPaymentAddress("asdfjklqwertuiopyxcvb");
		UserAccountDAO.updateAccount(userAccount);
		UserAccount fromDB = UserAccountService.getInstance().getByUsername(test61.getUsername());
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

		PayInTransactionService.create(transaction);
		
		fromDB = UserAccountService.getInstance().getByUsername(test61.getUsername());
		assertTrue(BigDecimal.ONE.compareTo(fromDB.getBalance())==0);
	}
	
	@Test
	public void testGetHistory() throws Exception {
		createAccountAndVerifyAndReload(test62, BigDecimal.ZERO); 
		
		UserAccount fromDB = UserAccountService.getInstance().getByUsername(test62.getUsername());
		
		assertEquals(0, PayInTransactionService.getInstance().getHistory(fromDB.getUsername(), 0).size());
		
		PayInTransaction tx;
		
		ArrayList<Date> dates = new ArrayList<Date>();
		int nofTransactions = 0;
		int additionalTx = 2;
		
		while (nofTransactions < Config.PAY_INS_MAX_RESULTS+additionalTx) {
			tx = new PayInTransaction();
			tx.setId(nofTransactions);
			Date d = new Date();
			dates.add(d);
			tx.setTimestamp(d);
			tx.setAmount(new BigDecimal("0.00001"));
			tx.setUserID(fromDB.getId());
			tx.setTransactionID(Integer.toString(nofTransactions));
			
			PayInTransactionDAO.createPayInTransaction(tx);
			
			nofTransactions++;
		}
		
		assertTrue(nofTransactions > Config.PAY_INS_MAX_RESULTS);
		ArrayList<HistoryPayInTransaction> history = PayInTransactionService.getInstance().getHistory(fromDB.getUsername(), 0);
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
		ArrayList<HistoryPayInTransaction> history2 = PayInTransactionService.getInstance().getHistory(fromDB.getUsername(), 1);
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
		long historyCount = PayInTransactionService.getInstance().getHistoryCount(fromDB.getUsername());
		assertEquals(Config.PAY_INS_MAX_RESULTS+additionalTx, historyCount);
	}

}