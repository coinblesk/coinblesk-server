package ch.uzh.csg.mbps.server.clientinterface;

import java.math.BigDecimal;
import java.util.List;

import com.azazar.bitcoin.jsonrpcclient.BitcoinException;

import ch.uzh.csg.mbps.customserialization.PKIAlgorithm;
import ch.uzh.csg.mbps.server.domain.ResetPassword;
import ch.uzh.csg.mbps.server.domain.UserAccount;
import ch.uzh.csg.mbps.server.domain.UserPublicKey;
import ch.uzh.csg.mbps.server.util.PasswordMatcher;
import ch.uzh.csg.mbps.server.util.exceptions.BalanceNotZeroException;
import ch.uzh.csg.mbps.server.util.exceptions.EmailAlreadyExistsException;
import ch.uzh.csg.mbps.server.util.exceptions.InvalidEmailException;
import ch.uzh.csg.mbps.server.util.exceptions.InvalidUrlException;
import ch.uzh.csg.mbps.server.util.exceptions.InvalidUsernameException;
import ch.uzh.csg.mbps.server.util.exceptions.UserAccountNotFoundException;
import ch.uzh.csg.mbps.server.util.exceptions.UsernameAlreadyExistsException;
import ch.uzh.csg.mbps.server.util.exceptions.VerificationTokenNotFoundException;
import ch.uzh.csg.mbps.server.web.model.UserModelObject;

public interface IUserAccount {
	
	/**
	 * Creates new UserAccount. Trims any whitespaces in front/after username and checks username for validity.
	 * 
	 * @param userAccount
	 * @return boolean if UserAccount has ben successfully created.
	 * @throws UsernameAlreadyExistsException
	 * @throws BitcoinException
	 * @throws InvalidUsernameException
	 * @throws InvalidEmailException 
	 * @throws EmailAlreadyExistsException 
	 * @throws InvalidUrlException 
	 */
	public boolean createAccount(UserAccount userAccount) throws UsernameAlreadyExistsException, BitcoinException, InvalidUsernameException, InvalidEmailException, EmailAlreadyExistsException, InvalidUrlException;

	/**
	 * Returns the UserAccount belonging to the given username.
	 * 
	 * @param username
	 * @return UserAccount with given username
	 * @throws UserAccountNotFoundException
	 */
	public UserAccount getByUsername(String username) throws UserAccountNotFoundException;

	/**
	 * Returns the UserAccount belonging to the given id.
	 * 
	 * @param id 
	 * @return UserAccount with given id
	 * @throws UserAccountNotFoundException
	 */
	public UserAccount getById(long id) throws UserAccountNotFoundException;

	/**
	 * Updates UserAccount in DB with updated values from updatedAccount. Only email address and password can be updated.
	 * 
	 * @param username
	 * @param updatedAccount
	 * @return boolean if operation was successful or not
	 * @throws UserAccountNotFoundException
	 */
	public boolean updateAccount(String username, UserAccount updatedAccount) throws UserAccountNotFoundException;

	/**
	 * Deletes UserAccount with username. UserAccount is not deleted from DB,
	 * but account's flag "isDeleted" is set to true.
	 * 
	 * @param username
	 * @return boolean if operation was successful or not
	 * @throws UserAccountNotFoundException
	 * @throws BalanceNotZeroException
	 */
	public boolean delete(String username) throws UserAccountNotFoundException, BalanceNotZeroException;

	/**
	 * Verifies email address of UserAccount to which verificationToken belongs to.
	 * 
	 * @param verificationToken
	 * @return boolean if UserAccount has successfully been verified.
	 */
	public boolean verifyEmailAddress(String verificationToken);

	/**
	 * Generates new {@link ResetPassword} Entry and sends Email to user with token
	 * @param emailAddress
	 * @throws UserAccountNotFoundException
	 */
	public void resetPasswordRequest(String emailAddress) throws UserAccountNotFoundException;

	/**
	 * Deletes all old tokens first, afterwards resets the userpassword and deletes the used token.
	 * @param matcher
	 * @return true if password has successfully been reseted
	 */
	public boolean resetPassword(PasswordMatcher matcher);

	/**
	 * Checks if token is saved in table and still valid (younger than 1h)
	 * @param resetPasswordToken
	 * @return
	 */
	public boolean isValidResetPasswordLink(String resetPasswordToken);
	
	/**
	 * Stores a public key on the database and maps this public key to a user
	 * account.
	 * 
	 * @param userId
	 *            the id of the user account
	 * @param algorithm
	 *            the {@link PKIAlgorithm} used to generate the key
	 * @param publicKey
	 *            the base64 encoded public key
	 * @return the key number, indicating the (incremented) position this public
	 *         key has in a list of public keys mapped to this user account
	 * @throws UserAccountNotFoundException
	 */
	public byte saveUserPublicKey(long id, PKIAlgorithm pkiAlgorithm, String publicKey) throws UserAccountNotFoundException;

	/**
	 * 
	 * @param email
	 * @return User Account
	 * @throws UserAccountNotFoundException 
	 */
	public UserAccount getByEmail(String email) throws UserAccountNotFoundException;

	public void resendVerificationEmail(UserAccount userAccount);
	
	public List<UserPublicKey> getUserPublicKey(long userId) throws UserAccountNotFoundException;
	
	public void updateAccount(UserAccount userAccount) throws UserAccountNotFoundException;
	
	public List<ResetPassword> getAllResetPassword();
	
	public String getTokenForUser(long id) throws VerificationTokenNotFoundException;
	
	public String getVerificationTokenByUserId(long id)  throws VerificationTokenNotFoundException;
	
	public List<UserPublicKey> getUserPublicKeys(long id);
	
	/**
	 * Returns all {@link UserAccount}s with role admin.
	 * @return list of all admins
	 */
	public List<UserAccount> getAdmins();
	
	/**
	 * Returns all {@link ch.uzh.csg.mbps.model.UserAccount}s with the roles as user and both.
	 * @return list of all user accounts
	 */
	public List<UserAccount> getUsers();
	
	/**
	 * Returns a {@link UserModelObject} of a given parameter username.
	 * 
	 * @param username
	 * @return User Model
	 */
	public UserModelObject getLoggedAdmin(String username);

	/**
	 * Updates a user with the role user to both
	 * 
	 * @param admin
	 * @throws UserAccountNotFoundException
	 */
	public void changeRoleBoth(UserAccount admin) throws UserAccountNotFoundException;

	/**
	 * sets the role as admin
	 * 
	 * @param email
	 * @throws UserAccountNotFoundException
	 */
	public void changeRoleAdmin(String email) throws UserAccountNotFoundException;

	/**
	 * 
	 * @return
	 */
	public List<UserAccount> getAllUserAccounts();
	/**
	 * Returns the sum of balances from all user accounts. Represents the total
	 * amount of Bitcoins which belong to all user accounts.
	 * @return sumOfUserAccountBalances
	 */
	public BigDecimal getSumOfUserAccountBalances();

	/**
	 * Checks if token is saved in table and still valid (younger than 1h)
	 * 
	 * @param adminToken
	 * @return
	 */
	public boolean isValidAdminRoleLink(String adminToken);

	/**
	 * Sends a message as email to all not deleted users
	 * 
	 * @param subject
	 * @param text
	 */
	public void sendMailToAll(String subject, String text);

	/**
	 * 
	 * @return All email addresses of all user with the role admin and both
	 */
	public List<String> getEmailOfAllUsers();

	/**
	 * Returns the email of the first admin
	 * @return Email
	 */
	public String getAdminEmail();
}