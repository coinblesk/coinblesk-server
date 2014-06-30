package ch.uzh.csg.mpbs.server.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;
import org.springframework.test.context.support.DirtiesContextTestExecutionListener;
import org.springframework.test.context.transaction.TransactionalTestExecutionListener;

import ch.uzh.csg.mbps.server.domain.ServerAccount;
import ch.uzh.csg.mbps.server.service.ServerAccountService;
import ch.uzh.csg.mbps.server.util.HibernateUtil;
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
import com.github.springtestdbunit.annotation.DatabaseTearDown;
import com.github.springtestdbunit.annotation.DbUnitConfiguration;
import com.github.springtestdbunit.annotation.ExpectedDatabase;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations={"classpath:test-applicationContext.xml"})
@DbUnitConfiguration(databaseConnection="dataSource")
@TestExecutionListeners({ 
	DependencyInjectionTestExecutionListener.class,
	DirtiesContextTestExecutionListener.class,
	TransactionalTestExecutionListener.class,
	DbUnitTestExecutionListener.class })
public class ServerAccountServiceTest {
	private static boolean initialized = false;
	
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
	@DatabaseSetup(value="classpath:DbUnitFiles/serverAccountData.xml",type=DatabaseOperation.CLEAN_INSERT)
	@DatabaseTearDown(value="classpath:DbUnitFiles/serverAccountData.xml", type=DatabaseOperation.DELETE_ALL)
	@ExpectedDatabase(value="classpath:DbUnitFiles/serverAccountExpectedCreateData.xml", table="server_account")
	public void testCreateAccount() throws UrlAlreadyExistsException, BitcoinException, InvalidUrlException, InvalidEmailException, InvalidPublicKeyException, ServerAccountNotFoundException {
		int numberOfServerAccount = getAllServerAccounts().size();
		ServerAccount newServer = new ServerAccount("www.test-test.ch", "test6@mail.com", "publicKey");
		assertTrue(ServerAccountService.getInstance().createAccount(newServer));
		
		newServer = ServerAccountService.getInstance().getByUrl(newServer.getUrl());
		assertNotNull(newServer);
		assertEquals(numberOfServerAccount+1, getAllServerAccounts().size());
	}
	
	@Test(expected=UrlAlreadyExistsException.class)
	@DatabaseSetup(value="classpath:DbUnitFiles/serverAccountData.xml",type=DatabaseOperation.CLEAN_INSERT)
	@DatabaseTearDown(value="classpath:DbUnitFiles/serverAccountData.xml", type=DatabaseOperation.DELETE_ALL)
	public void testCreateAccount_FailUrlAlreadyExists() throws UrlAlreadyExistsException, BitcoinException, InvalidUrlException, InvalidEmailException, InvalidPublicKeyException{
		ServerAccount newServer = new ServerAccount("https://www.my_url.ch", "test@mail.ch", "my public key");
		ServerAccountService.getInstance().createAccount(newServer);
	}
	
	@Test(expected=InvalidUrlException.class)
	public void testCreateAccount_FailInvalidUrl() throws UrlAlreadyExistsException, BitcoinException, InvalidUrlException, InvalidEmailException, InvalidPublicKeyException {
		ServerAccount serverAccount = new ServerAccount("abcd", "test@mail.ch", "blabla");
		ServerAccountService.getInstance().createAccount(serverAccount);
	}
	
	@Test(expected=InvalidEmailException.class)
	public void testCreateAccount_FailInvalidEmail() throws UrlAlreadyExistsException, BitcoinException, InvalidUrlException, InvalidEmailException, InvalidPublicKeyException {
		ServerAccount serverAccount = new ServerAccount("www.url.ch", "mail.ch", "blabla");
		ServerAccountService.getInstance().createAccount(serverAccount);
	}
	
	@Test(expected=ServerAccountNotFoundException.class)
	public void testReadAccount_FailUserAccountNotFound() throws ServerAccountNotFoundException {
		ServerAccountService.getInstance().getByUrl("www.notexisting.ch");
	}
	
	@Test
	@DatabaseSetup(value="classpath:DbUnitFiles/serverAccountData.xml",type=DatabaseOperation.CLEAN_INSERT)
	@DatabaseTearDown(value="classpath:DbUnitFiles/serverAccountData.xml", type=DatabaseOperation.DELETE_ALL)
	@ExpectedDatabase(value="classpath:DbUnitFiles/serverAccountExpectedManuallyData.xml", table="server_account")
	public void testCreateAccount_EnterFieldsManually() throws BitcoinException, InvalidUsernameException, InvalidEmailException, EmailAlreadyExistsException, UrlAlreadyExistsException, InvalidUrlException, InvalidPublicKeyException, ServerAccountNotFoundException {
		ServerAccount newAccount = new ServerAccount("www.insert.com", "insert@mail.ch", "fake-insert");
		newAccount.setBalanceLimit(BigDecimal.ZERO);
		Date date = new Date();
		newAccount.setTrustLevel(0);
		newAccount.setCreationDate(date);
		newAccount.setDeleted(false);
		newAccount.setId(0);
		
		assertTrue(ServerAccountService.getInstance().createAccount(newAccount));
		ServerAccount fromDB = ServerAccountService.getInstance().getByUrl("www.insert.com");
		
		assertEquals(newAccount.getUrl(), fromDB.getUrl());
		assertEquals(newAccount.getEmail(), fromDB.getEmail());
		assertEquals(newAccount.getTrustLevel(), fromDB.getTrustLevel());
		
		assertEquals(0, fromDB.getActiveBalance().compareTo(BigDecimal.ZERO));
		assertFalse(fromDB.isDeleted());
		System.out.println(newAccount.getId());
		System.out.println(fromDB.getId());
		//TODO: mehmet check how to fix
//		assertFalse(newAccount.getId() == fromDB.getId());
	}
	
	@Test
	@DatabaseSetup(value="classpath:DbUnitFiles/serverAccountData.xml",type=DatabaseOperation.CLEAN_INSERT)
	@DatabaseTearDown(value="classpath:DbUnitFiles/serverAccountData.xml", type=DatabaseOperation.DELETE_ALL)
	public void testReadAccount() throws ServerAccountNotFoundException, UrlAlreadyExistsException {
		ServerAccount byUrl = ServerAccountService.getInstance().getByUrl("https://www.my_url.ch");
		assertNotNull(byUrl);
		assertTrue(byUrl.getUrl().equals("https://www.my_url.ch"));
		assertTrue(byUrl.getEmail().equals("test@mail.ch"));
		assertEquals(false, byUrl.isDeleted());
	}
	
	@Test
	@DatabaseSetup(value="classpath:DbUnitFiles/serverAccountData.xml",type=DatabaseOperation.CLEAN_INSERT)
	@DatabaseTearDown(value="classpath:DbUnitFiles/serverAccountData.xml", type=DatabaseOperation.DELETE_ALL)
	public void testTrustLevel() throws ServerAccountNotFoundException {
		ServerAccount serverUrl1 = ServerAccountService.getInstance().getByUrl("https://www.my_url.ch");
		ServerAccount serverUrl2 = ServerAccountService.getInstance().getByUrl("www.haus.ch");
		
		serverUrl1.setTrustLevel(1);
		
		assertEquals(1, serverUrl1.getTrustLevel());
		assertEquals(0, serverUrl2.getTrustLevel());
	}

	@Test
	@DatabaseSetup(value="classpath:DbUnitFiles/serverAccountData.xml",type=DatabaseOperation.CLEAN_INSERT)
	@DatabaseTearDown(value="classpath:DbUnitFiles/serverAccountData.xml", type=DatabaseOperation.DELETE_ALL)
	@ExpectedDatabase(value="classpath:DbUnitFiles/serverAccountExpectedUpdatedData.xml", table="server_account")
	public void testUpdatedAccount() throws ServerAccountNotFoundException {
		ServerAccount beforeUpdateAccount = ServerAccountService.getInstance().getByUrl("www.mbps.com");
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
		beforeUpdateAccount.setUrl("www.update.com");
		beforeUpdateAccount.setBalanceLimit(new BigDecimal(1000.0));
		
		ServerAccountService.getInstance().updateAccount(url, beforeUpdateAccount);
		ServerAccount afterUpdateAccount = ServerAccountService.getInstance().getByUrl(beforeUpdateAccount.getUrl());
		
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
		//limit balance should not change
		assertTrue(limitBalance.equals(afterUpdateAccount.getBalanceLimit()));
	}
	
	@Test
//	@DatabaseSetup(value="classpath:DbUnitFiles/serverAccountData.xml",type=DatabaseOperation.CLEAN_INSERT)
//	@DatabaseTearDown(value="classpath:DbUnitFiles/serverAccountData.xml", type=DatabaseOperation.DELETE_ALL)
//	@ExpectedDatabase(value="classpath:DbUnitFiles/serverAccountExpectedUpdatedData.xml", table="server_account")
	public void deleteAccount(){
		//TODO: mehmet write test
	}
	
	@SuppressWarnings("unchecked")
	private List<ServerAccount> getAllServerAccounts() {
		SessionFactory sessionFactory = HibernateUtil.getSessionFactory();
		Session session = sessionFactory.openSession();
		session.beginTransaction();
		List<ServerAccount> list = session.createQuery("from SERVER_ACCOUNT").list();
		
		session.close();
		return list;
	}
	
}