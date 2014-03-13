package ch.uzh.csg.mbps.server.service;

import java.math.BigDecimal;
import java.security.KeyPair;
import java.util.Date;
import java.util.List;

import org.hibernate.HibernateException;

import ch.uzh.csg.mbps.server.clientinterface.IUserAccount;
import ch.uzh.csg.mbps.server.dao.UserAccountDAO;
import ch.uzh.csg.mbps.server.domain.ResetPassword;
import ch.uzh.csg.mbps.server.domain.UserAccount;
import ch.uzh.csg.mbps.server.util.BitcoindController;
import ch.uzh.csg.mbps.server.util.Config;
import ch.uzh.csg.mbps.server.util.CustomPasswordEncoder;
import ch.uzh.csg.mbps.server.util.Emailer;
import ch.uzh.csg.mbps.server.util.PasswordMatcher;
import ch.uzh.csg.mbps.server.util.exceptions.BalanceNotZeroException;
import ch.uzh.csg.mbps.server.util.exceptions.EmailAlreadyExistsException;
import ch.uzh.csg.mbps.server.util.exceptions.InvalidEmailException;
import ch.uzh.csg.mbps.server.util.exceptions.InvalidUsernameException;
import ch.uzh.csg.mbps.server.util.exceptions.UserAccountNotFoundException;
import ch.uzh.csg.mbps.server.util.exceptions.UsernameAlreadyExistsException;
import ch.uzh.csg.mbps.server.util.exceptions.VerificationTokenNotFoundException;
import ch.uzh.csg.mbps.util.KeyHandler;

import com.azazar.bitcoin.jsonrpcclient.BitcoinException;

/**
 * Service class for {@link UserAccount}.
 *
 */
public class UserAccountService implements IUserAccount {
	private static UserAccountService userAccountService;
	private static boolean TESTING_MODE = false;

	private UserAccountService() {
	}

	/**
	 * Returns new or existing instance of {@link UserAccountService}.
	 * 
	 * @return instance of UserAccountService
	 */
	public static UserAccountService getInstance() {
		if (userAccountService == null) {
			userAccountService = new UserAccountService();
		}
			
		return userAccountService;
	}
	
	/**
	 * Enables testing mode for JUnit Tests.
	 */
	public static void enableTestingMode() {
		TESTING_MODE = true;
	}
	
	public static boolean isTestingMode(){
		return TESTING_MODE;
	}

	/**
	 * Disables testing mode for JUnit Tests.
	 */
	public static void disableTestingMode() {
		TESTING_MODE = false;
	}

	@Override
	public boolean createAccount(UserAccount userAccount) throws UsernameAlreadyExistsException, BitcoinException, InvalidUsernameException, InvalidEmailException, EmailAlreadyExistsException {
		if (TESTING_MODE)
			return createAccount(userAccount, "fake-address");
		else
			return createAccount(userAccount, getNewPaymentAddress());
	}
	
	private boolean createAccount(UserAccount userAccount, String paymentAddress) throws UsernameAlreadyExistsException, BitcoinException, InvalidUsernameException, InvalidEmailException, EmailAlreadyExistsException {
		UserAccount fromDB = null;
		
		userAccount.setUsername(userAccount.getUsername().trim());
		String username = userAccount.getUsername();
		String email = userAccount.getEmail();
		
		if(username==null){
			throw new InvalidUsernameException();
		}
		
		if(email==null){
			throw new InvalidEmailException();
		}
		
		
		if(!username.matches(Config.USERNAME_REGEX)){
			throw new InvalidUsernameException();			
		}
		if(!email.matches(Config.EMAIL_REGEX)){
			throw new InvalidEmailException();			
		}
		
		try {
			//see for matches in db ignoring cases and deletion status
			fromDB = getByUsernameIgnoreCaseAndDeletedFlag(userAccount.getUsername());
			if (!fromDB.isEmailVerified()) {
				resendVerificationEmail(fromDB);
			}
			throw new UsernameAlreadyExistsException(userAccount.getUsername());
		} catch (UserAccountNotFoundException e) {
			//do nothing, since this happens when a new account is created with a unique username
		}
		
		try {
			//see for emailaddress matches in db ignoring cases and deletion status
			getByEmailIgnoreCaseAndDeletedFlag(email);
			throw new EmailAlreadyExistsException(email);
		} catch (UserAccountNotFoundException e) {
			//do nothing, since this happens when a new account is created with a unique email address
		}
		
		userAccount = new UserAccount(userAccount.getUsername(), userAccount.getEmail(), userAccount.getPassword());
		userAccount.setBalance(new BigDecimal(0.0));
		userAccount.setCreationDate(new Date());
		userAccount.setDeleted(false);
		userAccount.setEmailVerified(false);
		
		String passwordHash = CustomPasswordEncoder.getEncodedPassword(userAccount.getPassword());
		userAccount.setPassword(passwordHash);

		
		KeyPair keyPair = null;
		try {
			keyPair = KeyHandler.generateKeys();
		} catch (Exception e) {
			return false;
		}
		
		String privateKeyEncoded = KeyHandler.encodePrivateKey(keyPair.getPrivate());
		userAccount.setPrivateKey(privateKeyEncoded);
		String publicKeyEncoded = KeyHandler.encodePublicKey(keyPair.getPublic());
		userAccount.setPublicKey(publicKeyEncoded);
		
		userAccount.setTransactionNumber(0);
		userAccount.setPaymentAddress(paymentAddress);
		
		String token = java.util.UUID.randomUUID().toString();
		try {
			UserAccountDAO.createAccount(userAccount, token);
			sendEmailVerificationLink(token, userAccount.getEmail());
		} catch (HibernateException e) {
			return false;
		}
		return true;
	}
	
	private String getNewPaymentAddress() throws BitcoinException {
		return BitcoindController.getNewAddress();
	}
	
	private void sendEmailVerificationLink(String token, String email){
		Emailer.sendEmailConfirmationLink(token, email);
	}
	
	public void resendVerificationEmail(UserAccount userAccount) {
		String token;
		try {
			token = UserAccountDAO.getVerificationTokenByUserId(userAccount.getId());
			Emailer.sendEmailConfirmationLink(token, userAccount.getEmail());
		} catch (VerificationTokenNotFoundException e) {
			token = java.util.UUID.randomUUID().toString();
			try {
				UserAccountDAO.createEmailVerificationToken(userAccount.getId(), token);
				Emailer.sendEmailConfirmationLink(token, userAccount.getEmail());
			} catch (HibernateException e1) { 
			}
		}
	}

	@Override
	public UserAccount getByUsername(String username) throws UserAccountNotFoundException {
		return UserAccountDAO.getByUsername(username);
	}
	
	private UserAccount getByUsernameIgnoreCaseAndDeletedFlag(String username) throws UserAccountNotFoundException {
		return UserAccountDAO.getByUsernameIgnoreCaseAndDeletedFlag(username);
	}
	
	private UserAccount getByEmailIgnoreCaseAndDeletedFlag(String email) throws UserAccountNotFoundException{
		return UserAccountDAO.getByEmailIgnoreCaseAndDeletedFlag(email);
	}

	@Override
	public UserAccount getById(long id) throws UserAccountNotFoundException {
		return UserAccountDAO.getById(id);
	}

	@Override
	public boolean updateAccount(String username, UserAccount updatedAccount) throws UserAccountNotFoundException{
		UserAccount userAccount = getByUsername(username);

		if (updatedAccount.getEmail() != null && !updatedAccount.getEmail().isEmpty())
			userAccount.setEmail(updatedAccount.getEmail());

		if (updatedAccount.getPassword() != null && !updatedAccount.getPassword().isEmpty())
			userAccount.setPassword(CustomPasswordEncoder.getEncodedPassword(updatedAccount.getPassword()));

		try {
			UserAccountDAO.updateAccount(userAccount);
			return true;
		} catch (HibernateException e) {
			return false;
		}
	}

	@Override
	public boolean delete(String username) throws UserAccountNotFoundException, BalanceNotZeroException {
		try {
			UserAccountDAO.delete(username);
			return true;
		} catch (HibernateException e) {
			return false;
		}
	}

	@Override
	public boolean verifyEmailAddress(String verificationToken) {
		try {
			UserAccountDAO.verifyEmail(verificationToken);
			return true;
		} catch (UserAccountNotFoundException | HibernateException | VerificationTokenNotFoundException e) {
			return false;
		} 
	}

	/**
	 * Generates new {@link ResetPassword} Entry and sends Email to user with token
	 * @param emailAddress
	 * @throws UserAccountNotFoundException
	 */
	public void resetPasswordRequest(String emailAddress) throws UserAccountNotFoundException {
		UserAccount user = UserAccountDAO.getByEmail(emailAddress);
		String token = java.util.UUID.randomUUID().toString();
		UserAccountDAO.createPasswordResetToken(user, token);
		
		Emailer.sendResetPasswordLink(user, token);
	}
	
	/**
	 * Checks if token is saved in table and still valid (younger than 1h)
	 * @param resetPasswordToken
	 * @return
	 */
	public boolean isValidResetPasswordLink(String resetPasswordToken) {
		try {
			ResetPassword resetPassword = UserAccountDAO.getResetPassword(resetPasswordToken);
			if (resetPassword == null) {
				return false;
			} else {
				// checks if token has been created during the last 1h
				if (resetPassword.getCreationDate().getTime() >= (new Date().getTime() - Config.VALID_TOKEN_LIMIT)) {
					return true;
				} else {
					return false;
				}
			}
		} catch (VerificationTokenNotFoundException e) {
			return false;
		} 
	}	
	
	/**
	 * Deletes every time when a password is reseted the old entries ({@link ResetPassword}s) (older than 24h) from the table.
	 */
	public void deleteOldResetPasswords(){
		Date currentDate = new Date();
		List<ResetPassword> list = UserAccountDAO.getAllResetPassword();
		for(int i=0; i< list.size();i++){
			if(list.get(i).getCreationDate().getTime() < (currentDate.getTime() - Config.DELETE_TOKEN_LIMIT)){
				try {
					UserAccountDAO.deleteResetPassword(list.get(i).getToken());
				} catch (VerificationTokenNotFoundException e) {
				}
			}
		}
	}
	
	/**
	 * Returns {@link UserAccount} which belongs to ResetPassword-Token.
	 * @param token
	 * @return UserAccount
	 * @throws VerificationTokenNotFoundException
	 * @throws UserAccountNotFoundException
	 */
	public UserAccount getByResetPasswordToken(String token) throws VerificationTokenNotFoundException, UserAccountNotFoundException{
		return UserAccountDAO.getByResetPasswordToken(token);
	}
	
	/**
	 * Deletes all old tokens first, afterwards resets the userpassword and deletes the used token.
	 * @param matcher
	 * @return true if password has successfully been reseted
	 */
	public boolean resetPassword(PasswordMatcher matcher){
		if (!UserAccountService.getInstance().isValidResetPasswordLink(matcher.getToken())) {
			return false;
		}
		
		//clean table and delete all old tokens
		deleteOldResetPasswords();
		
		UserAccount user;
		if (matcher.compare()){
			try {
				user = UserAccountService.getInstance().getByResetPasswordToken(matcher.getToken());
				user.setPassword(matcher.getPw1());
				UserAccountService.getInstance().updateAccount(user.getUsername(), user);
				UserAccountDAO.deleteResetPassword(matcher.getToken());		
				return true;
			} catch (VerificationTokenNotFoundException | UserAccountNotFoundException e) {
				return false;
			}
		} else {
			return false;
		}
	}

}
