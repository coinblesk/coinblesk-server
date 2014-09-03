package ch.uzh.csg.mpbs.server.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.math.BigDecimal;
import java.security.KeyPair;
import java.util.Date;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import ch.uzh.csg.mbps.customserialization.PKIAlgorithm;
import ch.uzh.csg.mbps.server.clientinterface.IUserAccount;
import ch.uzh.csg.mbps.server.domain.UserAccount;
import ch.uzh.csg.mbps.server.domain.UserPublicKey;
import ch.uzh.csg.mbps.server.security.KeyHandler;
import ch.uzh.csg.mbps.server.service.UserAccountService;
import ch.uzh.csg.mbps.server.util.CustomPasswordEncoder;
import ch.uzh.csg.mbps.server.util.UserRoles.Role;
import ch.uzh.csg.mbps.server.util.exceptions.BalanceNotZeroException;
import ch.uzh.csg.mbps.server.util.exceptions.EmailAlreadyExistsException;
import ch.uzh.csg.mbps.server.util.exceptions.InvalidEmailException;
import ch.uzh.csg.mbps.server.util.exceptions.InvalidUrlException;
import ch.uzh.csg.mbps.server.util.exceptions.InvalidUsernameException;
import ch.uzh.csg.mbps.server.util.exceptions.UserAccountNotFoundException;
import ch.uzh.csg.mbps.server.util.exceptions.UsernameAlreadyExistsException;

import com.azazar.bitcoin.jsonrpcclient.BitcoinException;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {
		"classpath:context.xml",
		"classpath:test-database.xml"})
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
	private static UserAccount accountSix;
	private static UserAccount accountSeven;
	
	@Autowired
	private IUserAccount userAccountService;

	@Before
	public void setUp() throws InvalidEmailException, EmailAlreadyExistsException {
		UserAccountService.enableTestingMode();
		
		if (! initialized) {
			accountOnePassword = "my-password";
			accountOne = new UserAccount("hans@https://mbps.csg.uzh.ch", "hans@bitcoin.csg.uzh.ch", accountOnePassword);
			accountOne.setBalance(new BigDecimal(2.5));
			accountTwo = new UserAccount("hans1@https://mbps.csg.uzh.ch", "hans1@bitcoin.csg.uzh.ch", "my-password-wurst");
			accountTwo.setBalance(new BigDecimal(2.5));
			accountToDelete = new UserAccount("hans2@https://mbps.csg.uzh.ch", "hans2@bitcoin.csg.uzh.ch", "my-password-wurst");
			accountToDelete.setBalance(new BigDecimal(1.0));
			accountToUpdate = new UserAccount("hans3@https://mbps.csg.uzh.ch", "hans3@bitcoin.csg.uzh.ch", "my-password-wurst");
			accountToUpdate.setBalance(new BigDecimal(1.5));
			accountToUpdate2 = new UserAccount("hans3-2@https://mbps.csg.uzh.ch", "hans3-2@bitcoin.csg.uzh.ch", "my-password-wurst");
			accountToUpdate2.setBalance(new BigDecimal(0.8));
			accountFour = new UserAccount("hans4@https://mbps.csg.uzh.ch", "hans4@bitcoin.csg.uzh.ch", "my-password-wurst");
			accountFour.setBalance(new BigDecimal(2.0));
			accountFive = new UserAccount("hans5@https://mbps.csg.uzh.ch", "hans5@bitcoin.csg.uzh.ch", "my-password-wurst");
			accountFive.setBalance(new BigDecimal(1.3));
			accountSix = new UserAccount("hans6@https://mbps.csg.uzh.ch", "hans6@bitcoin.csg.uzh.ch", "my-password-wurst");
			accountSix.setBalance(new BigDecimal(0.4));
			accountSeven = new UserAccount("hans7@https://mbps.csg.uzh.ch", "hans7@bitcoin.csg.uzh.ch", "my-password-wurst");
			accountSeven.setBalance(new BigDecimal(0.4));
			try {
				userAccountService.createAccount(accountOne);
				userAccountService.createAccount(accountTwo);
				userAccountService.createAccount(accountToDelete);
				userAccountService.createAccount(accountToUpdate);
				userAccountService.createAccount(accountToUpdate2);
				userAccountService.createAccount(accountSix);
				userAccountService.createAccount(accountSeven);
			} catch (UsernameAlreadyExistsException | BitcoinException e) {
				//do nothing
			} catch (InvalidUsernameException | InvalidUrlException e) {
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
	public void testCreateAccount() throws UsernameAlreadyExistsException, UserAccountNotFoundException, BitcoinException, InvalidUsernameException, InvalidEmailException, EmailAlreadyExistsException, InvalidUrlException {
		int nofUsers = userAccountService.getAllUserAccounts().size();
		UserAccount newUser = new UserAccount("hans80@https://mbps.csg.uzh.ch", "hans80@bitcoin.csg.uzh.ch", "my-password");
		assertTrue(userAccountService.createAccount(newUser));
		
		newUser = userAccountService.getByUsername(newUser.getUsername());
		assertNotNull(newUser);
		assertEquals(nofUsers+1, userAccountService.getAllUserAccounts().size());
		
		assertFalse(newUser.isEmailVerified());
	}
	
	@Test
	public void testVerifyEmail() throws Exception {
		assertTrue(userAccountService.createAccount(accountFour));
		UserAccount fromDB = userAccountService.getByUsername(accountFour.getUsername());
		assertNotNull(fromDB);
		
		assertFalse(fromDB.isEmailVerified());
		
		String token = userAccountService.getVerificationTokenByUserId(fromDB.getId());
		assertTrue(userAccountService.verifyEmailAddress(token));
		
		fromDB = userAccountService.getByUsername(accountFour.getUsername());
		assertTrue(fromDB.isEmailVerified());
	}
	
	@Test
	public void testVerifyEmail_failWrongToken() throws Exception {
		assertTrue(userAccountService.createAccount(accountFive));
		UserAccount fromDB = userAccountService.getByUsername(accountFive.getUsername());
		assertNotNull(fromDB);
		
		assertFalse(fromDB.isEmailVerified());
		
		assertFalse(userAccountService.verifyEmailAddress("12345"));
		
		fromDB = userAccountService.getByUsername(accountFive.getUsername());
		assertFalse(fromDB.isEmailVerified());
	}
	
	@Test(expected=UsernameAlreadyExistsException.class)
	public void testCreateAccount_FailUsernameAlreadyExists() throws UsernameAlreadyExistsException, BitcoinException, InvalidUsernameException, InvalidEmailException, EmailAlreadyExistsException, InvalidUrlException {
		userAccountService.createAccount(accountOne);
	}
	
	@Test
	public void testCreateAccount_EnterFieldsManually() throws UsernameAlreadyExistsException, UserAccountNotFoundException, BitcoinException, InvalidUsernameException, InvalidEmailException, EmailAlreadyExistsException, InvalidUrlException {
		String pw = "my-password";
		UserAccount newAccount = new UserAccount("hans81@https://mbps.csg.uzh.ch", "hans81@bitcoin.csg.uzh.ch", pw);
		newAccount.setBalance(new BigDecimal(1000));
		Date date = new Date();
		newAccount.setCreationDate(date);
		newAccount.setDeleted(true);
		newAccount.setEmailVerified(true);
		newAccount.setId(256);
		newAccount.setRoles((byte) 2);
		
		assertTrue(userAccountService.createAccount(newAccount));
		
		UserAccount fromDB = userAccountService.getByUsername("hans81@https://mbps.csg.uzh.ch");
		
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
		UserAccount byUsername = userAccountService.getByUsername(accountOne.getUsername());
		assertNotNull(byUsername);
		assertTrue(byUsername.getUsername().equals(accountOne.getUsername()));
		assertTrue(byUsername.getEmail().equals(accountOne.getEmail()));
		assertEquals(false, byUsername.isDeleted());
		
		assertTrue(CustomPasswordEncoder.matches(accountOnePassword, byUsername.getPassword()));
	}
	
	@Test(expected=UserAccountNotFoundException.class)
	public void testReadAccount_FailUserAccountNotFound() throws UserAccountNotFoundException {
		userAccountService.getByUsername("not-existing");
	}
	
	@Test
	public void testUpdateAccount() throws UserAccountNotFoundException, UsernameAlreadyExistsException {
		UserAccount beforeUpdate = userAccountService.getByUsername(accountToUpdate.getUsername());
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
		beforeUpdate.setRoles(Role.BOTH.getCode());
		
		userAccountService.updateAccount(username, beforeUpdate);
		UserAccount afterUpdate = userAccountService.getByUsername(username);
		
		assertNotNull(afterUpdate);
		
		//password SHOULD change
		assertFalse(password.equals(afterUpdate.getPassword()));
		assertTrue(CustomPasswordEncoder.matches("new password haha", afterUpdate.getPassword()));
		//email SHOULD change
		assertFalse(email.equals(afterUpdate.getEmail()));
		assertEquals("new email", afterUpdate.getEmail());
		
		//balance should not change
		assertTrue(balance.equals(afterUpdate.getBalance()));
		//roles should change
		assertEquals(Role.BOTH.getCode(), afterUpdate.getRoles());
		assertFalse(roles == afterUpdate.getRoles());
		//creation date should not change
		assertEquals(creationDate, afterUpdate.getCreationDate());
		//id should not change
		assertEquals(id, afterUpdate.getId());
		//username should not change
		assertEquals(username, afterUpdate.getUsername());
	}
	
	@Test(expected=UserAccountNotFoundException.class)
	public void testDeleteAccount() throws UserAccountNotFoundException, UsernameAlreadyExistsException, BalanceNotZeroException {
		int nofUsers = userAccountService.getAllUserAccounts().size();
		userAccountService.delete(accountToDelete.getUsername());
		assertEquals(nofUsers, userAccountService.getAllUserAccounts().size());
		userAccountService.getByUsername(accountToDelete.getUsername());
	}
	
	@Test
	public void testSavePublicKeys() throws Exception {
		UserAccount loadedSix = userAccountService.getByUsername(accountSix.getUsername());
		
		List<UserPublicKey> userPublicKeys = userAccountService.getUserPublicKeys(loadedSix.getId());
		assertEquals(0, userPublicKeys.size());
		
		KeyPair keyPair = KeyHandler.generateKeyPair();
		String encodedPublicKey = KeyHandler.encodePublicKey(keyPair.getPublic());
		
		byte keyNumber = userAccountService.saveUserPublicKey(loadedSix.getId(), PKIAlgorithm.DEFAULT, encodedPublicKey);
		assertEquals(1, keyNumber);
		
		
		UserAccount loadedSeven = userAccountService.getByUsername(accountSeven.getUsername());
		userPublicKeys = userAccountService.getUserPublicKeys(loadedSeven.getId());
		assertEquals(0, userPublicKeys.size());
		
		keyPair = KeyHandler.generateKeyPair();
		encodedPublicKey = KeyHandler.encodePublicKey(keyPair.getPublic());
		
		keyNumber = userAccountService.saveUserPublicKey(loadedSix.getId(), PKIAlgorithm.DEFAULT, encodedPublicKey);
		assertEquals(2, keyNumber);
		
		userPublicKeys = userAccountService.getUserPublicKeys(loadedSix.getId());
		assertEquals(2, userPublicKeys.size());
		userPublicKeys = userAccountService.getUserPublicKeys(loadedSeven.getId());
		assertEquals(0, userPublicKeys.size());
		
		loadedSix = userAccountService.getByUsername(accountSix.getUsername());
		assertEquals(2, loadedSix.getNofKeys());
	}
	
	@Test
	public void testGetUserAccounts(){
		BigDecimal balance = userAccountService.getSumOfUserAccountBalances();
		
		assertNotNull(balance);
		
		List<UserAccount> userList = userAccountService.getAllUserAccounts();
		BigDecimal sum = BigDecimal.ZERO;
		for(UserAccount user: userList){
			sum = sum.add(user.getBalance());
		}
		
		assertEquals(balance, sum);
	}
	
	@Test
	public void testGetAllEmailAccounts(){
		List<String> allEmails = userAccountService.getEmailOfAllUsers();
		List<UserAccount> users = userAccountService.getUsers();
		
		//has to be same
		assertEquals(allEmails.size(), users.size());
	}

	@Test
	public void testGetAllUserAccounts(){		
		List<UserAccount> allUsers = userAccountService.getAllUserAccounts();
		List<UserAccount> users = userAccountService.getUsers();
		
		//only users with role users exists
		assertTrue(allUsers.size() == users.size());
		
		UserAccount account = new UserAccount("marcus@https://mbps.csg.uzh.ch", "marcus@bitcoin.csg.uzh.ch", accountOnePassword);
		account.setRoles(Role.ADMIN.getCode());
		try {
			userAccountService.createAccount(account);
		} catch (UsernameAlreadyExistsException | BitcoinException
				| InvalidUsernameException | InvalidEmailException
				| EmailAlreadyExistsException | InvalidUrlException e) {
			// do nothing
		}
		
		List<UserAccount> allUsers2 = userAccountService.getAllUserAccounts();
		List<UserAccount> users2 = userAccountService.getUsers();
		
		//only users with role users exists
		assertFalse(allUsers2.size() == users2.size());
	}
}
