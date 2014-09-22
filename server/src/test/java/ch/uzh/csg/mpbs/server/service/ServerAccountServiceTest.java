package ch.uzh.csg.mpbs.server.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.math.BigDecimal;
import java.util.Date;
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

import ch.uzh.csg.mbps.server.clientinterface.IServerAccount;
import ch.uzh.csg.mbps.server.clientinterface.IUserAccount;
import ch.uzh.csg.mbps.server.domain.ServerAccount;
import ch.uzh.csg.mbps.server.domain.UserAccount;
import ch.uzh.csg.mbps.server.service.ServerAccountService;
import ch.uzh.csg.mbps.server.util.SecurityConfig;
import ch.uzh.csg.mbps.server.util.exceptions.BalanceNotZeroException;
import ch.uzh.csg.mbps.server.util.exceptions.EmailAlreadyExistsException;
import ch.uzh.csg.mbps.server.util.exceptions.InvalidEmailException;
import ch.uzh.csg.mbps.server.util.exceptions.InvalidUrlException;
import ch.uzh.csg.mbps.server.util.exceptions.InvalidUsernameException;
import ch.uzh.csg.mbps.server.util.exceptions.ServerAccountNotFoundException;
import ch.uzh.csg.mbps.server.util.exceptions.UrlAlreadyExistsException;
import ch.uzh.csg.mbps.server.util.exceptions.UserAccountNotFoundException;
import ch.uzh.csg.mpbs.server.utilTest.ReplacementDataSetLoader;

import com.azazar.bitcoin.jsonrpcclient.BitcoinException;
import com.github.springtestdbunit.DbUnitTestExecutionListener;
import com.github.springtestdbunit.annotation.DatabaseOperation;
import com.github.springtestdbunit.annotation.DatabaseSetup;
import com.github.springtestdbunit.annotation.DbUnitConfiguration;
import com.github.springtestdbunit.annotation.ExpectedDatabase;
import com.github.springtestdbunit.assertion.DatabaseAssertionMode;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {
		"classpath:context.xml",
		"classpath:test-database.xml"})

@DbUnitConfiguration(databaseConnection="dataSource", dataSetLoader = ReplacementDataSetLoader.class)
@TestExecutionListeners({ 
	DependencyInjectionTestExecutionListener.class,
	DbUnitTestExecutionListener.class })
public class ServerAccountServiceTest {
	private static boolean initialized = false;
	
	@Autowired
	private IServerAccount serverAccountService;
	
	@Autowired
	private IUserAccount userAccountService;
	
	@Before
	public void setUp() throws Exception {
		ServerAccountService.enableTestingMode();
		if (!initialized){		
			initialized = true;
		}
	}
	
	@After
	public void teardown(){
		ServerAccountService.disableTestingMode();
	}

	@Test
	@DatabaseSetup(value="classpath:DbUnitFiles/Services/userAccountServerAccountData.xml",type=DatabaseOperation.CLEAN_INSERT)
	public void testPrepareGetAccount() throws UserAccountNotFoundException, InvalidUrlException, InvalidEmailException{
		UserAccount  account = userAccountService.getByUsername("hans");
		assertNotNull(account);
		
		ServerAccount server = new ServerAccount("http://www.neu.ch", "neu@neu.ch");
		ServerAccount sendAccount = serverAccountService.prepareAccount(account, server);
		assertNotNull(sendAccount);
		assertEquals(SecurityConfig.URL, sendAccount.getUrl());
		assertEquals(0, sendAccount.getNOfKeys());
	}

	@Test(expected=InvalidUrlException.class)
	@DatabaseSetup(value="classpath:DbUnitFiles/Services/userAccountServerAccountData.xml",type=DatabaseOperation.CLEAN_INSERT)
	public void testPrepareGetAccount_FailInvalidUrlexception() throws UserAccountNotFoundException, InvalidUrlException, InvalidEmailException{
		UserAccount  account = userAccountService.getByUsername("hans@http://server.own.org");
		assertNotNull(account);
		
		ServerAccount server = new ServerAccount("www.neu.ch", "neu@neu.ch");
		serverAccountService.prepareAccount(account, server);
	}
	
	@Test
	@DatabaseSetup(value="classpath:DbUnitFiles/Services/serverAccountData.xml",type=DatabaseOperation.CLEAN_INSERT)
	@ExpectedDatabase(value="classpath:DbUnitFiles/Services/serverAccountExpectedCreateData.xml", table="server_account")
	public void testPersistAccount() throws UrlAlreadyExistsException, BitcoinException, InvalidUrlException, InvalidEmailException, ServerAccountNotFoundException {
		int numberOfServerAccount = serverAccountService.getAll().size();
		ServerAccount newServer = new ServerAccount("https://www.test-test.ch", "test6@mail.com");
		assertTrue(serverAccountService.persistAccount(newServer));
		
		newServer = serverAccountService.getByUrl(newServer.getUrl());
		assertNotNull(newServer);
		assertEquals(numberOfServerAccount+1, serverAccountService.getAll().size());
	}
	
	@Test(expected=UrlAlreadyExistsException.class)
	@DatabaseSetup(value="classpath:DbUnitFiles/Services/serverAccountData.xml",type=DatabaseOperation.CLEAN_INSERT)
	public void testPersistsAccount_FailUrlAlreadyExists() throws UrlAlreadyExistsException, BitcoinException, InvalidUrlException, InvalidEmailException{
		ServerAccount newServer = new ServerAccount("https://www.my_url.ch", "test@mail.ch");
		serverAccountService.persistAccount(newServer);
	}
	
	@Test(expected=InvalidUrlException.class)
	public void testPersistsAccount_FailInvalidUrl() throws UrlAlreadyExistsException, BitcoinException, InvalidUrlException, InvalidEmailException {
		ServerAccount serverAccount = new ServerAccount("abcd", "test@mail.ch");
		serverAccountService.persistAccount(serverAccount);
	}
	
	@Test(expected=InvalidEmailException.class)
	public void testPersistsAccount_FailInvalidEmail() throws UrlAlreadyExistsException, BitcoinException, InvalidUrlException, InvalidEmailException {
		ServerAccount serverAccount = new ServerAccount("http://www.url.ch", "mail.ch");
		serverAccountService.persistAccount(serverAccount);
	}
	
	@Test(expected=ServerAccountNotFoundException.class)
	@DatabaseSetup(value="classpath:DbUnitFiles/Services/serverAccountData.xml",type=DatabaseOperation.CLEAN_INSERT)
	public void testReadAccount_FailUserAccountNotFound() throws ServerAccountNotFoundException {
		serverAccountService.getByUrl("http://www.notexisting.ch");
	}
	
	@Test
	@DatabaseSetup(value="classpath:DbUnitFiles/Services/serverAccountData.xml",type=DatabaseOperation.CLEAN_INSERT)
	@ExpectedDatabase(value="classpath:DbUnitFiles/Services/serverAccountExpectedManuallyData.xml", table="server_account", assertionMode = DatabaseAssertionMode.NON_STRICT)
	public void testCreateAccount_EnterFieldsManually() throws BitcoinException, InvalidUsernameException, InvalidEmailException, EmailAlreadyExistsException, UrlAlreadyExistsException, InvalidUrlException, ServerAccountNotFoundException {
		ServerAccount newAccount = new ServerAccount("https://www.insert.com", "insert@mail.ch");
		newAccount.setBalanceLimit(BigDecimal.ZERO);
		Date date = new Date();
		
		newAccount.setId(25);
		newAccount.setTrustLevel(1);
		newAccount.setCreationDate(date);
		newAccount.setDeleted(false);
		newAccount.setBalanceLimit(new BigDecimal(0.55));
		
		
		assertTrue(serverAccountService.persistAccount(newAccount));
		ServerAccount fromDB = serverAccountService.getByUrl("https://www.insert.com");
		
		assertEquals(newAccount.getUrl(), fromDB.getUrl());
		assertEquals(newAccount.getEmail(), fromDB.getEmail());
		assertFalse(newAccount.getTrustLevel()==fromDB.getTrustLevel());
		assertFalse(newAccount.getId()==fromDB.getId());
		
		assertEquals(0, fromDB.getActiveBalance().compareTo(BigDecimal.ZERO));
		assertFalse(fromDB.isDeleted());
	}
	
	@Test
	@DatabaseSetup(value="classpath:DbUnitFiles/Services/serverAccountData.xml",type=DatabaseOperation.CLEAN_INSERT)
	public void testReadAccount() throws ServerAccountNotFoundException, UrlAlreadyExistsException {
		ServerAccount byUrl = serverAccountService.getByUrl("https://www.my_url.ch");
		assertNotNull(byUrl);
		assertTrue(byUrl.getUrl().equals("https://www.my_url.ch"));
		assertTrue(byUrl.getEmail().equals("test@mail.ch"));
		assertEquals(false, byUrl.isDeleted());
	}
	
	@Test
	@DatabaseSetup(value="classpath:DbUnitFiles/Services/serverAccountData.xml",type=DatabaseOperation.CLEAN_INSERT)
	public void testTrustLevel() throws ServerAccountNotFoundException {
		ServerAccount serverUrl1 = serverAccountService.getByUrl("https://www.haus.ch");
		ServerAccount serverUrl2 = serverAccountService.getByUrl("https://www.my_url.ch");
		
		serverUrl1.setTrustLevel(1);
		
		assertEquals(1, serverUrl1.getTrustLevel());
		assertEquals(0, serverUrl2.getTrustLevel());
	}

	@Test
	@DatabaseSetup(value="classpath:DbUnitFiles/Services/serverAccountData.xml",type=DatabaseOperation.CLEAN_INSERT)
	@ExpectedDatabase(value="classpath:DbUnitFiles/Services/serverAccountExpectedUpdatedData.xml", table="server_account")
	public void testUpdatedAccount() throws ServerAccountNotFoundException {
		ServerAccount beforeUpdateAccount = serverAccountService.getByUrl("https://www.mbps.com");
		assertNotNull(beforeUpdateAccount);
		
		BigDecimal activeBalance = beforeUpdateAccount.getActiveBalance();
		Date creationDate = beforeUpdateAccount.getCreationDate();
		String email = beforeUpdateAccount.getEmail();
		long id = beforeUpdateAccount.getId();
		int trustlevel = beforeUpdateAccount.getTrustLevel();
		String url = beforeUpdateAccount.getUrl();
		BigDecimal limitBalance = beforeUpdateAccount.getBalanceLimit();
		BigDecimal userBalanceLimit = beforeUpdateAccount.getUserBalanceLimit();
		int nofKeys = beforeUpdateAccount.getNOfKeys();
		
		beforeUpdateAccount.setActiveBalance(new BigDecimal(-100.0));
		beforeUpdateAccount.setEmail("newemail@mail.com");
		beforeUpdateAccount.setId(id + 100);
		beforeUpdateAccount.setTrustLevel(1);
		beforeUpdateAccount.setUrl("https://www.update.com");
		beforeUpdateAccount.setBalanceLimit(new BigDecimal(100.0));
		beforeUpdateAccount.setUserBalanceLimit(new BigDecimal(20.0));
		beforeUpdateAccount.setNOfKeys(2);
		
		serverAccountService.updateAccount(url, beforeUpdateAccount);
		ServerAccount afterUpdateAccount = serverAccountService.getByUrl(beforeUpdateAccount.getUrl());
		
		assertNotNull(afterUpdateAccount);
		
		//TODO: check update in dao
		//trust level SHOULD change
		boolean trust = (trustlevel==afterUpdateAccount.getTrustLevel());
		assertFalse(trust);
		//email SHOULD change
		assertFalse(email.equals(afterUpdateAccount.getEmail()));
		assertEquals("newemail@mail.com", afterUpdateAccount.getEmail());
		
		//active balance should not change
		assertTrue(activeBalance.equals(afterUpdateAccount.getActiveBalance()));
		//creation date should not change
		assertEquals(creationDate, afterUpdateAccount.getCreationDate());
		//id should not change
		assertEquals(id, afterUpdateAccount.getId());
		//url should change
		assertFalse(url.equals(afterUpdateAccount.getUrl()));
		//limit balance should change
		assertFalse(limitBalance.equals(afterUpdateAccount.getBalanceLimit()));
		//user balance limit should change
		assertFalse(userBalanceLimit.equals(afterUpdateAccount.getUserBalanceLimit()));
		//nof keys should not change
		assertEquals(nofKeys, afterUpdateAccount.getNOfKeys());
	}
	
	@Test
	@DatabaseSetup(value="classpath:DbUnitFiles/Services/serverAccountData.xml",type=DatabaseOperation.CLEAN_INSERT)
	public void testDeleteAccount() throws ServerAccountNotFoundException, BalanceNotZeroException{
		ServerAccount account = serverAccountService.getByUrl("https://www.my_url.ch");
		
		assertEquals(account.getTrustLevel(), 0);
		assertEquals(account.getActiveBalance(), new BigDecimal("0.00000000"));
		
		boolean success = serverAccountService.deleteAccount("https://www.my_url.ch");
		assertEquals(success, true);
	}
	
	@Test
	@DatabaseSetup(value="classpath:DbUnitFiles/Services/serverAccountData.xml",type=DatabaseOperation.CLEAN_INSERT)
	public void testDeleteAccount_FailedConditions() throws ServerAccountNotFoundException, BalanceNotZeroException{
		ServerAccount accountActiveBalance = serverAccountService.getByUrl("http://www.fake_address.org");
		assertFalse(accountActiveBalance.getActiveBalance()==new BigDecimal("0.00000000"));
		boolean success = serverAccountService.deleteAccount("http://www.fake_address.org");
		assertEquals(success, false);

		ServerAccount accountTrustLevel = serverAccountService.getByUrl("http://test.com");
		assertFalse(accountTrustLevel.getTrustLevel()==0);
		boolean success2 = serverAccountService.deleteAccount("http://test.com");
		assertEquals(success2, false);
		
	}
	
	@Test
	@DatabaseSetup(value="classpath:DbUnitFiles/Services/serverAccountTrustLevelData.xml",type=DatabaseOperation.CLEAN_INSERT)
	public void testGetByTrustLevel(){
		List<ServerAccount> noTrust = serverAccountService.getByTrustLevel(0);
		List<ServerAccount> hyprid = serverAccountService.getByTrustLevel(1);
		List<ServerAccount> high = serverAccountService.getByTrustLevel(2);
		
		//deleted is not count
		assertFalse(noTrust.size()==5);
		assertEquals(noTrust.size(), 4);
		
		//deleted is not count
		assertFalse(hyprid.size() == 2);
		assertEquals(hyprid.size(), 1);
	
		//deleted is not count
		assertFalse(high.size() == 5);
		assertEquals(high.size(), 3);
	}
	
	@Test
	@DatabaseSetup(value="classpath:DbUnitFiles/Services/serverAccountTrustLevelData.xml",type=DatabaseOperation.CLEAN_INSERT)
	public void testGetServerAccountsAll() {
		List<ch.uzh.csg.mbps.model.ServerAccount> notDeletedAccounts = serverAccountService.getServerAccounts(0);
		assertNotNull(notDeletedAccounts);
		
		int nofAllAccounts = notDeletedAccounts.size();
		
		//deleted accounts are not count
		assertFalse(nofAllAccounts==12);

		assertEquals(nofAllAccounts,8);
		
		for(ch.uzh.csg.mbps.model.ServerAccount account: notDeletedAccounts){
			assertFalse(account.getBalanceLimit() == null);
			assertFalse(account.getActiveBalance() == null);			
		}
	}
	
	@Test
	@DatabaseSetup(value="classpath:DbUnitFiles/Services/userAccountServerAccountData.xml",type=DatabaseOperation.CLEAN_INSERT)
	public void testGetByUrlIgnoreCase() throws UrlAlreadyExistsException{
		String url = "https://www.my_url.ch";
		boolean success = serverAccountService.checkIfExistsByUrl(url);
		
		assertTrue(success);
		
		String urlNotExisting = "http://notExisting.ch";
		boolean failed = serverAccountService.checkIfExistsByUrl(urlNotExisting);
		
		assertFalse(failed);
	}
	
	@Test
	@DatabaseSetup(value="classpath:DbUnitFiles/Services/userAccountServerAccountData.xml",type=DatabaseOperation.CLEAN_INSERT)
	public void testGetUrlIgnoreCase(){
		ServerAccount test = new ServerAccount("http://what_is.ch", "mail@mail.ch");
		
		boolean success = serverAccountService.checkIfExistsByUrl(test.getUrl());
		
		assertFalse(success==true);
	}
}