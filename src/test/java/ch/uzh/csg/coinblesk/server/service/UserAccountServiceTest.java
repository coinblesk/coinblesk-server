package ch.uzh.csg.coinblesk.server.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.math.BigDecimal;
import java.security.KeyPair;
import java.util.Date;
import java.util.List;
import java.util.Random;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;

import ch.uzh.csg.coinblesk.customserialization.PKIAlgorithm;
import ch.uzh.csg.coinblesk.server.clientinterface.IUserAccount;
import ch.uzh.csg.coinblesk.server.domain.UserAccount;
import ch.uzh.csg.coinblesk.server.domain.UserPublicKey;
import ch.uzh.csg.coinblesk.server.security.KeyHandler;
import ch.uzh.csg.coinblesk.server.util.Config;
import ch.uzh.csg.coinblesk.server.util.CustomPasswordEncoder;
import ch.uzh.csg.coinblesk.server.util.UserRoles.Role;
import ch.uzh.csg.coinblesk.server.util.exceptions.BalanceNotZeroException;
import ch.uzh.csg.coinblesk.server.util.exceptions.EmailAlreadyExistsException;
import ch.uzh.csg.coinblesk.server.util.exceptions.InvalidEmailException;
import ch.uzh.csg.coinblesk.server.util.exceptions.InvalidUrlException;
import ch.uzh.csg.coinblesk.server.util.exceptions.InvalidUsernameException;
import ch.uzh.csg.coinblesk.server.util.exceptions.UserAccountNotFoundException;
import ch.uzh.csg.coinblesk.server.util.exceptions.UsernameAlreadyExistsException;
import ch.uzh.csg.coinblesk.server.utilTest.ReplacementDataSetLoader;

import com.github.springtestdbunit.DbUnitTestExecutionListener;
import com.github.springtestdbunit.annotation.DatabaseOperation;
import com.github.springtestdbunit.annotation.DatabaseSetup;
import com.github.springtestdbunit.annotation.DbUnitConfiguration;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = { 
        "classpath:context.xml", 
        "classpath:test-context.xml", 
        "classpath:test-database.xml" })
@DbUnitConfiguration(databaseConnection = "dataSource", dataSetLoader = ReplacementDataSetLoader.class)
@TestExecutionListeners({ DependencyInjectionTestExecutionListener.class, DbUnitTestExecutionListener.class })
public class UserAccountServiceTest {
    
    private static final Random RND = new Random(42L);
    private static UserAccount testAccount;

    @Autowired
    private IUserAccount userAccountService;

    @Before
    public void setUp() throws InvalidEmailException, EmailAlreadyExistsException {
        testAccount = new UserAccount("testuser", "imaginaryperson@email.com", "password");
    }

    @Test
    @DatabaseSetup(value = "classpath:DbUnitFiles/Services/userAccountServiceTestData.xml", type = DatabaseOperation.CLEAN_INSERT)
    public void testCreateAccount() throws UsernameAlreadyExistsException, UserAccountNotFoundException, InvalidUsernameException, InvalidEmailException,
            EmailAlreadyExistsException, InvalidUrlException {
        int nofUsers = userAccountService.getAllUserAccounts().size();
        UserAccount newUser = new UserAccount("hans80", "hans80@bitcoin.csg.uzh.ch", "my-password");
        assertTrue(userAccountService.createAccount(newUser));

        newUser = userAccountService.getByUsername(newUser.getUsername());
        assertNotNull(newUser);
        assertEquals(nofUsers + 1, userAccountService.getAllUserAccounts().size());

        assertFalse(newUser.isEmailVerified());
    }

    @Test
    @DatabaseSetup(value = "classpath:DbUnitFiles/Services/userAccountServiceTestData.xml", type = DatabaseOperation.CLEAN_INSERT)
    public void testVerifyEmail() throws Exception {
        assertTrue(userAccountService.createAccount(testAccount));
        UserAccount fromDB = userAccountService.getByUsername(testAccount.getUsername());
        assertNotNull(fromDB);

        assertFalse(fromDB.isEmailVerified());

        String token = userAccountService.getVerificationTokenByUserId(fromDB.getId());
        assertTrue(userAccountService.verifyEmailAddress(token));

        fromDB = userAccountService.getByUsername(testAccount.getUsername());
        assertTrue(fromDB.isEmailVerified());
    }

    @Test
    @DatabaseSetup(value = "classpath:DbUnitFiles/Services/userAccountServiceTestData.xml", type = DatabaseOperation.CLEAN_INSERT)
    public void testVerifyEmail_failWrongToken() throws Exception {
        assertTrue(userAccountService.createAccount(testAccount));
        UserAccount fromDB = userAccountService.getByUsername(testAccount.getUsername());
        assertNotNull(fromDB);

        assertFalse(fromDB.isEmailVerified());

        assertFalse(userAccountService.verifyEmailAddress("12345"));

        fromDB = userAccountService.getByUsername(testAccount.getUsername());
        assertFalse(fromDB.isEmailVerified());
    }

    @Test(expected = UsernameAlreadyExistsException.class)
    @DatabaseSetup(value = "classpath:DbUnitFiles/Services/userAccountServiceTestData.xml", type = DatabaseOperation.CLEAN_INSERT)
    public void testCreateAccount_FailUsernameAlreadyExists() throws UsernameAlreadyExistsException, InvalidUsernameException, InvalidEmailException,
            EmailAlreadyExistsException, InvalidUrlException {
        UserAccount existingUser = getRandomExistingUser();
        UserAccount newAccount = new UserAccount(existingUser.getUsername(), "test@email.com", "password");
        userAccountService.createAccount(newAccount);
    }

    @Test
    @DatabaseSetup(value = "classpath:DbUnitFiles/Services/userAccountServiceTestData.xml", type = DatabaseOperation.CLEAN_INSERT)
    public void testCreateAccount_EnterFieldsManually() throws UsernameAlreadyExistsException, UserAccountNotFoundException, InvalidUsernameException,
            InvalidEmailException, EmailAlreadyExistsException, InvalidUrlException {
        String pw = "my-password";
        UserAccount newAccount = new UserAccount("hans81", "hans81@bitcoin.csg.uzh.ch", pw);
        newAccount.setBalance(new BigDecimal(1000));
        Date date = new Date();
        newAccount.setCreationDate(date);
        newAccount.setDeleted(true);
        newAccount.setEmailVerified(true);
        newAccount.setId(256);
        newAccount.setRoles((byte) 2);

        assertTrue(userAccountService.createAccount(newAccount));

        UserAccount fromDB = userAccountService.getByUsername("hans81");

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
    @DatabaseSetup(value = "classpath:DbUnitFiles/Services/userAccountServiceTestData.xml", type = DatabaseOperation.CLEAN_INSERT)
    public void testReadAccount() throws Exception {
        
        boolean success = userAccountService.createAccount(testAccount);
        assertTrue(success);
        
        UserAccount reloadedUserAccount = userAccountService.getByUsername(testAccount.getUsername());
        assertNotNull(reloadedUserAccount);
        assertTrue(reloadedUserAccount.getUsername().equals(testAccount.getUsername()));
        assertTrue(reloadedUserAccount.getEmail().equals(testAccount.getEmail()));
        assertEquals(false, reloadedUserAccount.isDeleted());
        
        System.out.println("lolrofl");
        System.out.println(testAccount.getPassword());
        System.out.println(reloadedUserAccount.getPassword());

        assertTrue(CustomPasswordEncoder.matches(testAccount.getPassword(), reloadedUserAccount.getPassword()));
    }

    @Test(expected = UserAccountNotFoundException.class)
    @DatabaseSetup(value = "classpath:DbUnitFiles/Services/userAccountServiceTestData.xml", type = DatabaseOperation.CLEAN_INSERT)
    public void testReadAccount_FailUserAccountNotFound() throws UserAccountNotFoundException {
        userAccountService.getByUsername("not-existing");
    }

    @Test
    @DatabaseSetup(value = "classpath:DbUnitFiles/Services/userAccountServiceTestData.xml", type = DatabaseOperation.CLEAN_INSERT)
    public void testUpdateAccount() throws UserAccountNotFoundException, UsernameAlreadyExistsException {
        UserAccount existingUser = getRandomExistingUser();
        UserAccount beforeUpdate = userAccountService.getByUsername(existingUser.getUsername());
        assertNotNull(beforeUpdate);
        BigDecimal balance = beforeUpdate.getBalance();
        Date creationDate = beforeUpdate.getCreationDate();
        String email = beforeUpdate.getEmail();
        long id = beforeUpdate.getId();
        String password = beforeUpdate.getPassword();
        String username = beforeUpdate.getUsername();

        beforeUpdate.setBalance(new BigDecimal(1000));
        beforeUpdate.setCreationDate(new Date());
        beforeUpdate.setEmail("new email");
        beforeUpdate.setId(id + 100);
        beforeUpdate.setPassword("new password");
        beforeUpdate.setUsername("useruser");

        boolean success = userAccountService.updateAccount(username, beforeUpdate);
        assertTrue(success);
        
        UserAccount afterUpdate = userAccountService.getByUsername(username);
        assertNotNull(afterUpdate);

        // password SHOULD change
        assertFalse(password.equals(afterUpdate.getPassword()));
        assertTrue(CustomPasswordEncoder.matches("new password", afterUpdate.getPassword()));
        // email SHOULD change
        assertFalse(email.equals(afterUpdate.getEmail()));
        assertEquals("new email", afterUpdate.getEmail());

        // balance should not change
        assertTrue(balance.equals(afterUpdate.getBalance()));
        // creation date should not change
        assertEquals(creationDate, afterUpdate.getCreationDate());
        // id should not change
        assertEquals(id, afterUpdate.getId());
        // username should not change
        assertEquals(username, afterUpdate.getUsername());
    }

    @Test(expected = UserAccountNotFoundException.class)
    @DatabaseSetup(value = "classpath:DbUnitFiles/Services/userAccountServiceTestData.xml", type = DatabaseOperation.CLEAN_INSERT)
    public void testDeleteAccount() throws UserAccountNotFoundException, UsernameAlreadyExistsException, BalanceNotZeroException {
        
        UserAccount existingUser = userAccountService.getById(1001L);
        int nofUsers = userAccountService.getAllUserAccounts().size();
        userAccountService.delete(existingUser.getUsername());
        assertEquals(nofUsers, userAccountService.getAllUserAccounts().size());
        userAccountService.getByUsername(existingUser.getUsername());
    }
    
    @Test(expected = BalanceNotZeroException.class)
    @DatabaseSetup(value = "classpath:DbUnitFiles/Services/userAccountServiceTestData.xml", type = DatabaseOperation.CLEAN_INSERT)
    public void testDeleteAccount2() throws UserAccountNotFoundException, UsernameAlreadyExistsException, BalanceNotZeroException {
        UserAccount existingUser = userAccountService.getById(1007L);
        int nofUsers = userAccountService.getAllUserAccounts().size();
        userAccountService.delete(existingUser.getUsername());
        assertEquals(nofUsers, userAccountService.getAllUserAccounts().size());
        userAccountService.getByUsername(existingUser.getUsername());
    }

    @Test
    @DatabaseSetup(value = "classpath:DbUnitFiles/Services/userAccountServiceTestData.xml", type = DatabaseOperation.CLEAN_INSERT)
    public void testSavePublicKeys() throws Exception {
        
        UserAccount user1 = getRandomExistingUser();
        UserAccount user2 = getRandomExistingUser();
        
        // make sure we load a different user
        while(user1.equals(user2)) {
            user2 = getRandomExistingUser();
        }

        List<UserPublicKey> userPublicKeys = userAccountService.getUserPublicKeys(user2.getId());
        assertEquals(0, userPublicKeys.size());

        KeyPair keyPair = KeyHandler.generateKeyPair();
        String encodedPublicKey = KeyHandler.encodePublicKey(keyPair.getPublic());

        byte keyNumber = userAccountService.saveUserPublicKey(user2.getId(), PKIAlgorithm.DEFAULT, encodedPublicKey);
        assertEquals(1, keyNumber);

        UserAccount user1reloaded = userAccountService.getByUsername(user1.getUsername());
        userPublicKeys = userAccountService.getUserPublicKeys(user1reloaded.getId());
        assertEquals(0, userPublicKeys.size());

        keyPair = KeyHandler.generateKeyPair();
        encodedPublicKey = KeyHandler.encodePublicKey(keyPair.getPublic());

        keyNumber = userAccountService.saveUserPublicKey(user2.getId(), PKIAlgorithm.DEFAULT, encodedPublicKey);
        assertEquals(2, keyNumber);

        userPublicKeys = userAccountService.getUserPublicKeys(user2.getId());
        assertEquals(2, userPublicKeys.size());
        userPublicKeys = userAccountService.getUserPublicKeys(user1reloaded.getId());
        assertEquals(0, userPublicKeys.size());

        UserAccount user2reloaded = userAccountService.getByUsername(user2.getUsername());
        assertEquals(2, user2reloaded.getNofKeys());
    }

    @Test
    @DatabaseSetup(value = "classpath:DbUnitFiles/Services/userAccountServiceTestData.xml", type = DatabaseOperation.CLEAN_INSERT)
    public void testGetUserAccounts() {
        BigDecimal balance = userAccountService.getSumOfUserAccountBalances();

        assertNotNull(balance);

        List<UserAccount> userList = userAccountService.getAllUserAccounts();
        BigDecimal sum = BigDecimal.ZERO;
        for (UserAccount user : userList) {
            sum = sum.add(user.getBalance());
        }

        assertEquals(balance, sum);
    }

    @Test
    @DatabaseSetup(value = "classpath:DbUnitFiles/Services/userAccountServiceTestData.xml", type = DatabaseOperation.CLEAN_INSERT)
    public void testGetAllEmailAccounts() {
        List<String> allEmails = userAccountService.getEmailOfAllUsers();
        List<UserAccount> users = userAccountService.getUsers();

        // has to be same
        assertEquals(allEmails.size(), users.size());
    }

    @Test
    @DatabaseSetup(value = "classpath:DbUnitFiles/Services/userAccountServiceTestData.xml", type = DatabaseOperation.CLEAN_INSERT)
    public void testGetAllUserAccounts() {
        List<UserAccount> allUsers = userAccountService.getAllUserAccounts();
        List<UserAccount> users = userAccountService.getUsers();

        // only users with role users exists
        assertTrue(allUsers.size() == users.size());

        UserAccount account = new UserAccount("marcus", "marcus@bitcoin.csg.uzh.ch", "some password");
        account.setRoles(Role.ADMIN.getCode());
        try {
            userAccountService.createAccount(account);
        } catch (UsernameAlreadyExistsException | InvalidUsernameException | InvalidEmailException | EmailAlreadyExistsException | InvalidUrlException e) {
            // do nothing
        }

        List<UserAccount> allUsers2 = userAccountService.getAllUserAccounts();
        List<UserAccount> users2 = userAccountService.getUsers();

        // only users with role users exists
        assertFalse(allUsers2.size() == users2.size());
    }

    @Test
    @DatabaseSetup(value = "classpath:DbUnitFiles/Services/userAccountServiceTestData.xml", type = DatabaseOperation.CLEAN_INSERT)
    public void testGetAdminEmail() throws Exception {
        UserAccount account = new UserAccount("felix", "felix@bitcoin.csg.uzh.ch", "some password");
        account.setRoles(Role.ADMIN.getCode());
        try {
            userAccountService.createAccount(account);
        } catch (UsernameAlreadyExistsException | InvalidUsernameException | InvalidEmailException | EmailAlreadyExistsException | InvalidUrlException e) {
            // do nothing
        }

        UserAccount admin = userAccountService.getAdminEmail();
        assertNotNull(admin);
        assertTrue(admin.getEmail().matches(Config.EMAIL_REGEX));
    }
    
    private UserAccount getRandomExistingUser() {
        List<UserAccount> accounts = userAccountService.getAllUsers();
        return accounts.get(RND.nextInt(accounts.size()));
    }
}
