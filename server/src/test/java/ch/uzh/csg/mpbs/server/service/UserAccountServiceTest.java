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

import ch.uzh.csg.mbps.server.dao.UserAccountDAO;
import ch.uzh.csg.mbps.server.domain.UserAccount;
import ch.uzh.csg.mbps.server.service.UserAccountService;
import ch.uzh.csg.mbps.server.util.CustomPasswordEncoder;
import ch.uzh.csg.mbps.server.util.HibernateUtil;
import ch.uzh.csg.mbps.server.util.exceptions.BalanceNotZeroException;
import ch.uzh.csg.mbps.server.util.exceptions.EmailAlreadyExistsException;
import ch.uzh.csg.mbps.server.util.exceptions.InvalidEmailException;
import ch.uzh.csg.mbps.server.util.exceptions.InvalidUsernameException;
import ch.uzh.csg.mbps.server.util.exceptions.UserAccountNotFoundException;
import ch.uzh.csg.mbps.server.util.exceptions.UsernameAlreadyExistsException;

import com.azazar.bitcoin.jsonrpcclient.BitcoinException;

public class UserAccountServiceTest {
	private static boolean initialized = false;
	
	private static String accountOnePassword;
	private static UserAccount accountOne;
	private static UserAccount accountTwo;
	private static UserAccount accountToDelete;
	private static UserAccount accountToUpdate;
	private static UserAccount accountToUpdate2;
	private static UserAccount accountFour;
	private static UserAccount accountFive;

	@Before
	public void setUp() throws InvalidEmailException, EmailAlreadyExistsException {
		UserAccountService.enableTestingMode();
		
		if (! initialized) {
			accountOnePassword = "my-password";
			accountOne = new UserAccount("hans", "hans@bitcoin.csg.uzh.ch", accountOnePassword);
			accountTwo = new UserAccount("hans1", "hans1@bitcoin.csg.uzh.ch", "my-password-wurst");
			accountToDelete = new UserAccount("hans2", "hans2@bitcoin.csg.uzh.ch", "my-password-wurst");
			accountToUpdate = new UserAccount("hans3", "hans3@bitcoin.csg.uzh.ch", "my-password-wurst");
			accountToUpdate2 = new UserAccount("hans3-2", "hans3-2@bitcoin.csg.uzh.ch", "my-password-wurst");
			accountFour = new UserAccount("hans4", "hans4@bitcoin.csg.uzh.ch", "my-password-wurst");
			accountFive = new UserAccount("hans5", "hans5@bitcoin.csg.uzh.ch", "my-password-wurst");
			
			try {
				UserAccountService.getInstance().createAccount(accountOne);
				UserAccountService.getInstance().createAccount(accountTwo);
				UserAccountService.getInstance().createAccount(accountToDelete);
				UserAccountService.getInstance().createAccount(accountToUpdate);
				UserAccountService.getInstance().createAccount(accountToUpdate2);
			} catch (UsernameAlreadyExistsException | BitcoinException e) {
				//do nothing
			} catch (InvalidUsernameException e) {
				//do nothing
			}
			
			initialized = true;
		}
	}
	
	@After
	public void tearDown() {
		UserAccountService.disableTestingMode();
	}
	
	@Test
	public void testCreateAccount() throws UsernameAlreadyExistsException, UserAccountNotFoundException, BitcoinException, InvalidUsernameException, InvalidEmailException, EmailAlreadyExistsException {
		int nofUsers = getAllUserAccounts().size();
		UserAccount newUser = new UserAccount("hans80", "hans80@bitcoin.csg.uzh.ch", "my-password");
		assertTrue(UserAccountService.getInstance().createAccount(newUser));
		
		newUser = UserAccountService.getInstance().getByUsername(newUser.getUsername());
		assertNotNull(newUser);
		assertEquals(nofUsers+1, getAllUserAccounts().size());
		
		assertFalse(newUser.isEmailVerified());
	}
	
	@Test
	public void testVerifyEmail() throws Exception {
		assertTrue(UserAccountService.getInstance().createAccount(accountFour));
		UserAccount fromDB = UserAccountService.getInstance().getByUsername(accountFour.getUsername());
		assertNotNull(fromDB);
		
		assertFalse(fromDB.isEmailVerified());
		
		String token = UserAccountDAO.getVerificationTokenByUserId(fromDB.getId());
		assertTrue(UserAccountService.getInstance().verifyEmailAddress(token));
		
		fromDB = UserAccountService.getInstance().getByUsername(accountFour.getUsername());
		assertTrue(fromDB.isEmailVerified());
	}
	
	@Test
	public void testVerifyEmail_failWrongToken() throws Exception {
		assertTrue(UserAccountService.getInstance().createAccount(accountFive));
		UserAccount fromDB = UserAccountService.getInstance().getByUsername(accountFive.getUsername());
		assertNotNull(fromDB);
		
		assertFalse(fromDB.isEmailVerified());
		
		assertFalse(UserAccountService.getInstance().verifyEmailAddress("12345"));
		
		fromDB = UserAccountService.getInstance().getByUsername(accountFive.getUsername());
		assertFalse(fromDB.isEmailVerified());
	}
	
	@Test(expected=UsernameAlreadyExistsException.class)
	public void testCreateAccount_FailUsernameAlreadyExists() throws UsernameAlreadyExistsException, BitcoinException, InvalidUsernameException, InvalidEmailException, EmailAlreadyExistsException {
		UserAccountService.getInstance().createAccount(accountOne);
	}
	
	@Test
	public void testCreateAccount_EnterFieldsManually() throws UsernameAlreadyExistsException, UserAccountNotFoundException, BitcoinException, InvalidUsernameException, InvalidEmailException, EmailAlreadyExistsException {
		String pw = "my-password";
		UserAccount newAccount = new UserAccount("hans81", "hans81@bitcoin.csg.uzh.ch", pw);
		newAccount.setBalance(new BigDecimal(1000));
		Date date = new Date();
		newAccount.setCreationDate(date);
		newAccount.setDeleted(true);
		newAccount.setEmailVerified(true);
		newAccount.setId(256);
		newAccount.setRoles((byte) 2);
		
		assertTrue(UserAccountService.getInstance().createAccount(newAccount));
		
		UserAccount fromDB = UserAccountService.getInstance().getByUsername("hans81");
		
		assertEquals(newAccount.getUsername(), fromDB.getUsername());
		assertEquals(newAccount.getEmail(), fromDB.getEmail());
		assertFalse(newAccount.getPassword().equals(fromDB.getPassword()));
		assertTrue(CustomPasswordEncoder.matches(newAccount.getPassword(), fromDB.getPassword()));
		
		assertEquals(0, fromDB.getBalance().compareTo(BigDecimal.ZERO));
		assertFalse(fromDB.isDeleted());
		assertFalse(fromDB.isEmailVerified());
		assertFalse(newAccount.getId() == fromDB.getId());
		assertEquals(newAccount.getRoles(), fromDB.getRoles());
	}
	

	@Test
	public void testReadAccount() throws UserAccountNotFoundException, UsernameAlreadyExistsException {
		UserAccount byUsername = UserAccountService.getInstance().getByUsername(accountOne.getUsername());
		assertNotNull(byUsername);
		assertTrue(byUsername.getUsername().equals(accountOne.getUsername()));
		assertTrue(byUsername.getEmail().equals(accountOne.getEmail()));
		assertEquals(false, byUsername.isDeleted());
		
		assertTrue(CustomPasswordEncoder.matches(accountOnePassword, byUsername.getPassword()));
	}
	
	@Test(expected=UserAccountNotFoundException.class)
	public void testReadAccount_FailUserAccountNotFound() throws UserAccountNotFoundException {
		UserAccountService.getInstance().getByUsername("not-existing");
	}
	
	@Test
	public void testUpdateAccount() throws UserAccountNotFoundException, UsernameAlreadyExistsException {
		UserAccount beforeUpdate = UserAccountService.getInstance().getByUsername(accountToUpdate.getUsername());
		assertNotNull(beforeUpdate);
		BigDecimal balance = beforeUpdate.getBalance();
		Date creationDate = beforeUpdate.getCreationDate();
		String email = beforeUpdate.getEmail();
		long id = beforeUpdate.getId();
		String password = beforeUpdate.getPassword();
		String username = beforeUpdate.getUsername();
		byte roles = beforeUpdate.getRoles();
		
		beforeUpdate.setBalance(new BigDecimal(1000));
		beforeUpdate.setCreationDate(new Date());
		beforeUpdate.setEmail("new email");
		beforeUpdate.setId(id+100);
		beforeUpdate.setPassword("new password haha");
		beforeUpdate.setUsername("useruser");
		beforeUpdate.setRoles((byte) 3);
		
		UserAccountService.getInstance().updateAccount(username, beforeUpdate);
		UserAccount afterUpdate = UserAccountService.getInstance().getByUsername(username);
		
		assertNotNull(afterUpdate);
		
		//password SHOULD change
		assertFalse(password.equals(afterUpdate.getPassword()));
		assertTrue(CustomPasswordEncoder.matches("new password haha", afterUpdate.getPassword()));
		//email SHOULD change
		assertFalse(email.equals(afterUpdate.getEmail()));
		assertEquals("new email", afterUpdate.getEmail());
		
		//balance should not change
		assertTrue(balance.equals(afterUpdate.getBalance()));
		//creation date should not change
		assertEquals(creationDate, afterUpdate.getCreationDate());
		//id should not change
		assertEquals(id, afterUpdate.getId());
		//username should not change
		assertEquals(username, afterUpdate.getUsername());
		//roles should not change
		assertEquals(roles, afterUpdate.getRoles());
	}
	
	@Test(expected=UserAccountNotFoundException.class)
	public void testDeleteAccount() throws UserAccountNotFoundException, UsernameAlreadyExistsException, BalanceNotZeroException {
		int nofUsers = getAllUserAccounts().size();
		UserAccountService.getInstance().delete(accountToDelete.getUsername());
		assertEquals(nofUsers, getAllUserAccounts().size());
		UserAccountService.getInstance().getByUsername(accountToDelete.getUsername());
	}
	
	@SuppressWarnings("unchecked")
	private List<UserAccount> getAllUserAccounts() {
		SessionFactory sessionFactory = HibernateUtil.getSessionFactory();
		Session session = sessionFactory.openSession();
		session.beginTransaction();
		List<UserAccount> list = session.createQuery("from USER_ACCOUNT").list();
		
		session.close();
		return list;
	}

}
