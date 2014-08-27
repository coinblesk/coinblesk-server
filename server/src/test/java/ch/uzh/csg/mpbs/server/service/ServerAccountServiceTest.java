package ch.uzh.csg.mpbs.server.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.math.BigDecimal;
import java.util.Date;

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
import ch.uzh.csg.mbps.server.domain.ServerAccount;
import ch.uzh.csg.mbps.server.service.ServerAccountService;
import ch.uzh.csg.mbps.server.util.exceptions.EmailAlreadyExistsException;
import ch.uzh.csg.mbps.server.util.exceptions.InvalidEmailException;
import ch.uzh.csg.mbps.server.util.exceptions.InvalidPublicKeyException;
import ch.uzh.csg.mbps.server.util.exceptions.InvalidUrlException;
import ch.uzh.csg.mbps.server.util.exceptions.InvalidUsernameException;
import ch.uzh.csg.mbps.server.util.exceptions.ServerAccountNotFoundException;
import ch.uzh.csg.mbps.server.util.exceptions.UrlAlreadyExistsException;

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
		"classpath:test-database.xml",
		"classpath:test-context.xml"})

@DbUnitConfiguration(databaseConnection="dataSource")
@TestExecutionListeners({ 
	DependencyInjectionTestExecutionListener.class,
	DbUnitTestExecutionListener.class })
public class ServerAccountServiceTest {
	private static boolean initialized = false;
	
	@Autowired
	private IServerAccount serverAccountService;
	
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
	@DatabaseSetup(value="classpath:DbUnitFiles/Services/serverAccountData.xml",type=DatabaseOperation.CLEAN_INSERT)
	@ExpectedDatabase(value="classpath:DbUnitFiles/Services/serverAccountExpectedCreateData.xml", table="server_account")
	public void testCreateAccount() throws UrlAlreadyExistsException, BitcoinException, InvalidUrlException, InvalidEmailException, InvalidPublicKeyException, ServerAccountNotFoundException {
		int numberOfServerAccount = serverAccountService.getAll().size();
		ServerAccount newServer = new ServerAccount("https://www.test-test.ch", "test6@mail.com", "publicKey");
		assertTrue(serverAccountService.createAccount(newServer));
		
		newServer = serverAccountService.getByUrl(newServer.getUrl());
		assertNotNull(newServer);
		assertEquals(numberOfServerAccount+1, serverAccountService.getAll().size());
	}
	
	@Test(expected=UrlAlreadyExistsException.class)
	@DatabaseSetup(value="classpath:DbUnitFiles/Services/serverAccountData.xml",type=DatabaseOperation.CLEAN_INSERT)
	public void testCreateAccount_FailUrlAlreadyExists() throws UrlAlreadyExistsException, BitcoinException, InvalidUrlException, InvalidEmailException, InvalidPublicKeyException{
		ServerAccount newServer = new ServerAccount("https://www.my_url.ch", "test@mail.ch", "my public key");
		serverAccountService.createAccount(newServer);
	}
	
	@Test(expected=InvalidUrlException.class)
	public void testCreateAccount_FailInvalidUrl() throws UrlAlreadyExistsException, BitcoinException, InvalidUrlException, InvalidEmailException, InvalidPublicKeyException {
		ServerAccount serverAccount = new ServerAccount("abcd", "test@mail.ch", "blabla");
		serverAccountService.createAccount(serverAccount);
	}
	
	@Test(expected=InvalidEmailException.class)
	public void testCreateAccount_FailInvalidEmail() throws UrlAlreadyExistsException, BitcoinException, InvalidUrlException, InvalidEmailException, InvalidPublicKeyException {
		ServerAccount serverAccount = new ServerAccount("http://www.url.ch", "mail.ch", "blabla");
		serverAccountService.createAccount(serverAccount);
	}
	
	@Test(expected=ServerAccountNotFoundException.class)
	@DatabaseSetup(value="classpath:DbUnitFiles/Services/serverAccountData.xml",type=DatabaseOperation.CLEAN_INSERT)
	public void testReadAccount_FailUserAccountNotFound() throws ServerAccountNotFoundException {
		serverAccountService.getByUrl("http://www.notexisting.ch");
	}
	
	@Test
	@DatabaseSetup(value="classpath:DbUnitFiles/Services/serverAccountData.xml",type=DatabaseOperation.CLEAN_INSERT)
	@ExpectedDatabase(value="classpath:DbUnitFiles/Services/serverAccountExpectedManuallyData.xml", table="server_account", assertionMode = DatabaseAssertionMode.NON_STRICT)
	public void testCreateAccount_EnterFieldsManually() throws BitcoinException, InvalidUsernameException, InvalidEmailException, EmailAlreadyExistsException, UrlAlreadyExistsException, InvalidUrlException, InvalidPublicKeyException, ServerAccountNotFoundException {
		ServerAccount newAccount = new ServerAccount("https://www.insert.com", "insert@mail.ch", "fake-insert");
		newAccount.setBalanceLimit(BigDecimal.ZERO);
		Date date = new Date();
		
		newAccount.setId(25);
		newAccount.setTrustLevel(1);
		newAccount.setCreationDate(date);
		newAccount.setDeleted(false);
		newAccount.setBalanceLimit(new BigDecimal(0.55));
		
		
		assertTrue(serverAccountService.createAccount(newAccount));
		ServerAccount fromDB = serverAccountService.getByUrl("https://www.insert.com");
		
		assertEquals(newAccount.getUrl(), fromDB.getUrl());
		assertEquals(newAccount.getEmail(), fromDB.getEmail());
		assertEquals(newAccount.getTrustLevel(), fromDB.getTrustLevel());
		
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
		
		beforeUpdateAccount.setActiveBalance(new BigDecimal(-100.0));
		beforeUpdateAccount.setEmail("newemail@mail.com");
		beforeUpdateAccount.setId(id + 100);
		beforeUpdateAccount.setTrustLevel(1);
		beforeUpdateAccount.setUrl("https://www.update.com");
		beforeUpdateAccount.setBalanceLimit(new BigDecimal(100.0));
		
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
	}
	
	@Test
	@DatabaseSetup(value="classpath:DbUnitFiles/Services/serverAccountData.xml",type=DatabaseOperation.CLEAN_INSERT)
//	@ExpectedDatabase(value="classpath:DbUnitFiles/serverAccountExpectedUpdatedData.xml", table="server_account")
	public void deleteAccount(){
		//TODO: mehmet write test
	}
	
}