package ch.uzh.csg.mpbs.server.service;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.security.KeyPair;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;

import ch.uzh.csg.mbps.customserialization.PKIAlgorithm;
import ch.uzh.csg.mbps.keys.CustomKeyPair;
import ch.uzh.csg.mbps.server.clientinterface.IServerTransaction;
import ch.uzh.csg.mbps.server.security.KeyHandler;
import ch.uzh.csg.mbps.server.service.ServerAccountService;
import ch.uzh.csg.mbps.server.util.Config;
import ch.uzh.csg.mbps.server.util.Constants;
import ch.uzh.csg.mbps.server.util.exceptions.ServerAccountNotFoundException;
import ch.uzh.csg.mbps.server.util.web.model.HistoryServerAccountTransaction;
import ch.uzh.csg.mpbs.server.util.ReplacementDataSetLoader;

import com.github.springtestdbunit.DbUnitTestExecutionListener;
import com.github.springtestdbunit.annotation.DatabaseOperation;
import com.github.springtestdbunit.annotation.DatabaseSetup;
import com.github.springtestdbunit.annotation.DbUnitConfiguration;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {
		"classpath:context.xml",
		"classpath:test-database.xml"})
@DbUnitConfiguration(databaseConnection="dataSource", dataSetLoader = ReplacementDataSetLoader.class)
@TestExecutionListeners({ 
	DependencyInjectionTestExecutionListener.class,
	DbUnitTestExecutionListener.class })
public class ServerTransactionServiceTest {

	private static boolean initialized = false;
	
	@Autowired
	private IServerTransaction serverTransactionService;
	
	@Before
	public void setUp() throws Exception {
		ServerAccountService.enableTestingMode();
		if (!initialized){		
			KeyPair keypair = KeyHandler.generateKeyPair();
			
			Constants.SERVER_KEY_PAIR = new CustomKeyPair(PKIAlgorithm.DEFAULT.getCode(), (byte) 1, KeyHandler.encodePublicKey(keypair.getPublic()), KeyHandler.encodePrivateKey(keypair.getPrivate()));
			initialized = true;
		}
	}
	
	@After
	public void teardown(){
		ServerAccountService.disableTestingMode();
	}
	
	@Test
	@DatabaseSetup(value="classpath:DbUnitFiles/Services/serverTransactionDataHistory.xml",type=DatabaseOperation.CLEAN_INSERT)
	public void testCreateRule() {
		//TODO: mehmet needed when communicating with android
		
		
	}
	
	@Test
	@DatabaseSetup(value="classpath:DbUnitFiles/Services/serverTransactionDataHistory.xml",type=DatabaseOperation.CLEAN_INSERT)
	public void testGetHistory() {

		//TODO: mehmet verified??
		
		//first page
		List<HistoryServerAccountTransaction> history = serverTransactionService.getHistory(0);
		assertNotNull(history);
		long nofAllTransaction = serverTransactionService.getHistoryCount();
		//Not the same size
		assertFalse(history.size() == nofAllTransaction);
		//the history has to have the same size TODO: 50 has to be it
		assertEquals(history.size(), Config.TRANSACTIONS_MAX_RESULTS);
		
		//second page
		List<HistoryServerAccountTransaction> history2 = serverTransactionService.getHistory(1);
		assertNotNull(history2);
		//has less the max result
		assertTrue(history2.size() < Config.TRANSACTIONS_MAX_RESULTS);
		
		long nofTransaction = history.size() + history2.size();
		
		assertEquals(nofAllTransaction,nofTransaction);
		
		//assert that the list is in descending order page 1
		for(int i= 0; i < history.size()-1; i++){
			assertTrue(history.get(i).getTimestamp().compareTo(history.get(i+1).getTimestamp()) >= 0);
		}
		
		//assert that the list is in descending order page 2
		for(int i= 0; i < history2.size()-1; i++){
			assertTrue(history.get(i).getTimestamp().compareTo(history.get(i+1).getTimestamp()) >= 0);
		}

		//assert that the first of the page 1 is descended to the last of the page 2
		assertTrue(history.get(0).getTimestamp().compareTo(history2.get(history2.size()-1).getTimestamp()) == 1);
	}
	
	@Test
	@DatabaseSetup(value="classpath:DbUnitFiles/Services/serverTransactionDataReceived.xml",type=DatabaseOperation.CLEAN_INSERT)
	public void testGetPayeeHistory() {
		List<HistoryServerAccountTransaction> payeeList = serverTransactionService.getPayeeHistory(0);
		assertNotNull(payeeList);
		assertThat(payeeList.size(), is(7));
		
		//assert that all accounts are payee
		for(int i= 0; i < payeeList.size(); i++){
			assertTrue(payeeList.get(i).getReceived()==true);
		}
	}
	
	@Test
	@DatabaseSetup(value="classpath:DbUnitFiles/Services/serverTransactionDataReceived.xml",type=DatabaseOperation.CLEAN_INSERT)
	public void testGetPayerHistory() {
		List<HistoryServerAccountTransaction> payerList = serverTransactionService.getPayerHistory(0);
		assertNotNull(payerList);
		assertThat(payerList.size(), is(5));
		
		//assert that all accounts are payee
		for(int i= 0; i < payerList.size(); i++){
			assertTrue(payerList.get(i).getReceived()==false);
		}
	}
	
	@Test
	@DatabaseSetup(value="classpath:DbUnitFiles/Services/serverTransactionDataHistory.xml",type=DatabaseOperation.CLEAN_INSERT)
	public void testGetLast5Transactions(){
		List<HistoryServerAccountTransaction> transaction = serverTransactionService.getLast5Transactions();
		assertNotNull(transaction);
		
		assertThat(transaction.size(), is(5));
		
		//assert that all transactions are verified
		for(int i= 0; i < transaction.size(); i++){
			assertTrue(transaction.get(i).getVerified()==true);
		}
		
		//assert that the list is in descending order page 1
		for(int i= 0; i < transaction.size()-1; i++){
			assertTrue(transaction.get(i).getTimestamp().compareTo(transaction.get(i+1).getTimestamp()) >= 0);
		}
	}
	
	@Test
	@DatabaseSetup(value="classpath:DbUnitFiles/Services/serverTransactionDataHistory.xml",type=DatabaseOperation.CLEAN_INSERT)
	public void testGetLast5ServerAccountTransaction() throws ServerAccountNotFoundException{
		List<HistoryServerAccountTransaction> transaction = serverTransactionService.getLast5ServerAccountTransaction("https://www.haus.ch");
		assertNotNull(transaction);
		
		assertThat(transaction.size(), is(5));
		
		//assert that the list is in descending order page 1
		for(int i= 0; i < transaction.size()-1; i++){
			assertTrue(transaction.get(i).getTimestamp().compareTo(transaction.get(i+1).getTimestamp()) >= 0);
		}
	}
	
	@Test
	@DatabaseSetup(value="classpath:DbUnitFiles/Services/serverTransactionDataHistory.xml",type=DatabaseOperation.CLEAN_INSERT)
	public void testetServerAccountTransactions() throws ServerAccountNotFoundException{
		//TODO: mehmet verified??
		
		//first page
		List<HistoryServerAccountTransaction> history = serverTransactionService.getServerAccountTransactions("https://www.my_url.ch", 0);
		assertNotNull(history);
		long nofAllTransaction = serverTransactionService.getServerAccountHistoryCount("https://www.my_url.ch");

		assertTrue(history.size() < Config.TRANSACTIONS_MAX_RESULTS);
		//Has to be same
		assertTrue(history.size() == nofAllTransaction);
		
		//second page
		List<HistoryServerAccountTransaction> history2 = serverTransactionService.getServerAccountTransactions("https://www.my_url.ch", 1);
		assertNotNull(history2);
		//has less the max result
		assertTrue(history2.isEmpty());	
		
		//assert that the list is in descending order page 1
		for(int i= 0; i < history.size()-1; i++){
			assertTrue(history.get(i).getTimestamp().compareTo(history.get(i+1).getTimestamp()) >= 0);
		}
	}
}