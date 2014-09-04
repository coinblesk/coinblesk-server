package ch.uzh.csg.mpbs.server.service;

import static org.hamcrest.Matchers.is;
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
import ch.uzh.csg.mbps.server.clientinterface.IActivities;
import ch.uzh.csg.mbps.server.clientinterface.IServerAccount;
import ch.uzh.csg.mbps.server.clientinterface.IServerPayOutTransaction;
import ch.uzh.csg.mbps.server.clientinterface.IUserAccount;
import ch.uzh.csg.mbps.server.security.KeyHandler;
import ch.uzh.csg.mbps.server.service.ServerAccountService;
import ch.uzh.csg.mbps.server.service.ServerPayOutTransactionService;
import ch.uzh.csg.mbps.server.util.Config;
import ch.uzh.csg.mbps.server.util.Constants;
import ch.uzh.csg.mbps.server.util.exceptions.ServerAccountNotFoundException;
import ch.uzh.csg.mbps.server.util.test.ReplacementDataSetLoader;
import ch.uzh.csg.mbps.server.util.web.model.HistoryServerAccountTransaction;
import ch.uzh.csg.mbps.server.util.web.model.HistoryServerPayOutTransaction;

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
public class ServerPayOutTransactionServiceTest {

	@Autowired 
	private IServerPayOutTransaction serverPayOutTransactionService;
	@Autowired
	private IServerAccount serverAccountService;
	@Autowired
	private IActivities activitiesService;
	@Autowired
	private IUserAccount userAccountService;

	private static boolean initialized = false;
	
	@Before
	public void setUp() throws Exception {
		ServerPayOutTransactionService.testingMode = true;
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
		ServerPayOutTransactionService.testingMode = true;
	}
	
	@Test
	@DatabaseSetup(value="classpath:DbUnitFiles/Services/serverPayOutTransactionData.xml", type = DatabaseOperation.CLEAN_INSERT)
	public void testGetHistory(){
		List <HistoryServerPayOutTransaction> history = serverPayOutTransactionService.getHistory(0);
		assertTrue(history.size() == 19);
		assertFalse(history.size() == Config.PAY_OUTS_MAX_RESULTS);
		
		//assert that the list is in descending order
		for (int i=0; i<history.size()-1; i++) {
			assertTrue(history.get(i).getTimestamp().compareTo(history.get(i+1).getTimestamp()) >= 0);
		}
	}
	
	@Test
	@DatabaseSetup(value="classpath:DbUnitFiles/Services/serverPayOutTransactionData.xml",type=DatabaseOperation.CLEAN_INSERT)
	public void testGetLast5Transactions(){
		List<HistoryServerPayOutTransaction> transaction = serverPayOutTransactionService.getLast5Transactions();
		assertNotNull(transaction);
		
		assertThat(transaction.size(), is(5));
		
		//assert that all transactions are verified
		for(int i= 0; i < transaction.size(); i++){
			assertTrue(transaction.get(i).isVerified()==true);
		}
		
		//assert that the list is in descending order page 1
		for(int i= 0; i < transaction.size()-1; i++){
			assertTrue(transaction.get(i).getTimestamp().compareTo(transaction.get(i+1).getTimestamp()) >= 0);
		}
	}
	
	@Test
	@DatabaseSetup(value="classpath:DbUnitFiles/Services/serverPayOutTransactionData.xml",type=DatabaseOperation.CLEAN_INSERT)
	public void testGetLast5ServerAccountTransaction() throws ServerAccountNotFoundException{
		List<HistoryServerPayOutTransaction> transaction = serverPayOutTransactionService.getLast5ServerAccountTransactions("https://www.my_url.ch");
		assertNotNull(transaction);
		
		assertThat(transaction.size(), is(5));
		
		//assert that all transactions are verified
		for(int i= 0; i < transaction.size(); i++){
			assertTrue(transaction.get(i).isVerified()==true);
		}
		
		//assert that the list is in descending order page 1
		for(int i= 0; i < transaction.size()-1; i++){
			assertTrue(transaction.get(i).getTimestamp().compareTo(transaction.get(i+1).getTimestamp()) >= 0);
		}
	}
}
