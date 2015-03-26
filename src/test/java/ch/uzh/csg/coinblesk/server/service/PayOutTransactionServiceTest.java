package ch.uzh.csg.coinblesk.server.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import ch.uzh.csg.coinblesk.model.HistoryPayOutTransaction;
import ch.uzh.csg.coinblesk.server.clientinterface.IPayOutTransaction;
import ch.uzh.csg.coinblesk.server.clientinterface.IUserAccount;
import ch.uzh.csg.coinblesk.server.domain.PayOutTransaction;
import ch.uzh.csg.coinblesk.server.domain.UserAccount;
import ch.uzh.csg.coinblesk.server.service.UserAccountService;
import ch.uzh.csg.coinblesk.server.util.Config;
import ch.uzh.csg.coinblesk.server.util.exceptions.EmailAlreadyExistsException;
import ch.uzh.csg.coinblesk.server.util.exceptions.InvalidEmailException;
import ch.uzh.csg.coinblesk.server.util.exceptions.InvalidUrlException;
import ch.uzh.csg.coinblesk.server.util.exceptions.InvalidUsernameException;
import ch.uzh.csg.coinblesk.server.util.exceptions.UserAccountNotFoundException;
import ch.uzh.csg.coinblesk.server.util.exceptions.UsernameAlreadyExistsException;
import ch.uzh.csg.coinblesk.server.utilTest.TestUtil;

import com.azazar.bitcoin.jsonrpcclient.BitcoinException;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {
		"classpath:context.xml",
		"classpath:test-database.xml"})

public class PayOutTransactionServiceTest {
	
	@Autowired
	private IUserAccount userAccountService;
	
	@Autowired
	private IPayOutTransaction payOutTransactionService;
	
	private static UserAccount test71;

	@Before
	public void setUp() throws Exception {
		UserAccountService.enableTestingMode();
		test71 = new UserAccount("test71@https://mbps.csg.uzh.ch", "test71@bitcoin.csg.uzh.ch", "asdf");
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
	public void testGetHistory() throws Exception {
		createAccountAndVerifyAndReload(test71, BigDecimal.ZERO); 
		
		UserAccount fromDB = userAccountService.getByUsername(test71.getUsername());
		
		assertEquals(0, payOutTransactionService.getHistory(fromDB.getUsername(), 0).size());
		
		PayOutTransaction tx;
		
		ArrayList<Date> dates = new ArrayList<Date>();
		int nofTransactions = 0;
		int additionalTx = 2;
		
		while (nofTransactions < Config.PAY_OUTS_MAX_RESULTS+additionalTx) {
			tx = new PayOutTransaction();
			Date d = new Date();
			dates.add(d);
			tx.setTimestamp(d);
			tx.setAmount(new BigDecimal("0.00001"));
			tx.setBtcAddress(Integer.toString(nofTransactions));
			tx.setUserID(fromDB.getId());
			tx.setTransactionID(Integer.toString(nofTransactions));
			tx.setVerified(false);
			
			payOutTransactionService.createPayOutTransaction(tx);
			
			nofTransactions++;
		}
		
		assertTrue(nofTransactions > Config.PAY_OUTS_MAX_RESULTS);
		List<HistoryPayOutTransaction> history = payOutTransactionService.getHistory(fromDB.getUsername(), 0);
		assertEquals(Config.PAY_OUTS_MAX_RESULTS, history.size());
		
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
		List<HistoryPayOutTransaction> history2 = payOutTransactionService.getHistory(fromDB.getUsername(), 1);
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
		long historyCount = payOutTransactionService.getHistoryCount(fromDB.getUsername());
		assertEquals(Config.PAY_OUTS_MAX_RESULTS+additionalTx, historyCount);
	}

}
