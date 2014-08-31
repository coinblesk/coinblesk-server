package ch.uzh.csg.mbps.server.clientinterface;

import java.math.BigDecimal;
import java.util.List;

import com.azazar.bitcoin.jsonrpcclient.BitcoinException;

import ch.uzh.csg.mbps.customserialization.PKIAlgorithm;
import ch.uzh.csg.mbps.server.domain.ResetPassword;
import ch.uzh.csg.mbps.server.domain.UserAccount;
import ch.uzh.csg.mbps.server.domain.UserPublicKey;
import ch.uzh.csg.mbps.server.util.PasswordMatcher;
import ch.uzh.csg.mbps.server.util.UserModel;
import ch.uzh.csg.mbps.server.util.exceptions.BalanceNotZeroException;
import ch.uzh.csg.mbps.server.util.exceptions.EmailAlreadyExistsException;
import ch.uzh.csg.mbps.server.util.exceptions.InvalidEmailException;
import ch.uzh.csg.mbps.server.util.exceptions.InvalidUsernameException;
import ch.uzh.csg.mbps.server.util.exceptions.UserAccountNotFoundException;
import ch.uzh.csg.mbps.server.util.exceptions.UsernameAlreadyExistsException;
import ch.uzh.csg.mbps.server.util.exceptions.VerificationTokenNotFoundException;

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
	 */
	public boolean createAccount(UserAccount userAccount) throws UsernameAlreadyExistsException, BitcoinException, InvalidUsernameException, InvalidEmailException, EmailAlreadyExistsException;

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

	public List<UserAccount> getUsers();

	public void resetPasswordRequest(String emailAddress) throws UserAccountNotFoundException;

	public boolean resetPassword(PasswordMatcher matcher);

	public boolean isValidResetPasswordLink(String resetPasswordToken);

	public byte saveUserPublicKey(long id, PKIAlgorithm pkiAlgorithm, String publicKey) throws UserAccountNotFoundException;

	public BigDecimal getSumOfAllAccounts();

	public List<UserAccount> getAdmins();

	public UserModel getLoggedAdmin(String username);

	public UserAccount getByEmail(String email) throws UserAccountNotFoundException;

	public void changeRoleBoth(String email) throws UserAccountNotFoundException;

	public void changeRoleAdmin(String email) throws UserAccountNotFoundException;

	public void resendVerificationEmail(UserAccount userAccount);
	
	public List<UserPublicKey> getUserPublicKey(long userId) throws UserAccountNotFoundException;

	public void updateAccount(UserAccount userAccount) throws UserAccountNotFoundException;

	public List<ResetPassword> getAllResetPassword();

	public String getTokenForUser(long id) throws VerificationTokenNotFoundException;

	public List<UserAccount> getAllUserAccounts();

	public String getVerificationTokenByUserId(long id)  throws VerificationTokenNotFoundException;

	public List<UserPublicKey> getUserPublicKeys(long id);

	public BigDecimal getSumOfUserAccountBalances();

}
