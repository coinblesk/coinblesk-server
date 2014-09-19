package ch.uzh.csg.mbps.server.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ch.uzh.csg.mbps.customserialization.PKIAlgorithm;
import ch.uzh.csg.mbps.server.clientinterface.IUserAccount;
import ch.uzh.csg.mbps.server.dao.UserAccountDAO;
import ch.uzh.csg.mbps.server.dao.UserPublicKeyDAO;
import ch.uzh.csg.mbps.server.domain.AdminRole;
import ch.uzh.csg.mbps.server.domain.ResetPassword;
import ch.uzh.csg.mbps.server.domain.UserAccount;
import ch.uzh.csg.mbps.server.domain.UserPublicKey;
import ch.uzh.csg.mbps.server.util.AdminObject;
import ch.uzh.csg.mbps.server.util.BitcoindController;
import ch.uzh.csg.mbps.server.util.Config;
import ch.uzh.csg.mbps.server.util.CustomPasswordEncoder;
import ch.uzh.csg.mbps.server.util.Emailer;
import ch.uzh.csg.mbps.server.util.PasswordMatcher;
import ch.uzh.csg.mbps.server.util.SecurityConfig;
import ch.uzh.csg.mbps.server.util.SplitNameHandler;
import ch.uzh.csg.mbps.server.util.UserRoles.Role;
import ch.uzh.csg.mbps.server.util.exceptions.BalanceNotZeroException;
import ch.uzh.csg.mbps.server.util.exceptions.EmailAlreadyExistsException;
import ch.uzh.csg.mbps.server.util.exceptions.InvalidEmailException;
import ch.uzh.csg.mbps.server.util.exceptions.InvalidUrlException;
import ch.uzh.csg.mbps.server.util.exceptions.InvalidUsernameException;
import ch.uzh.csg.mbps.server.util.exceptions.UserAccountNotFoundException;
import ch.uzh.csg.mbps.server.util.exceptions.UsernameAlreadyExistsException;
import ch.uzh.csg.mbps.server.util.exceptions.VerificationTokenNotFoundException;

import com.azazar.bitcoin.jsonrpcclient.BitcoinException;

/**
 * Service class for {@link UserAccount}.
 */
@Service
public class UserAccountService implements IUserAccount {
	private static boolean TESTING_MODE = false;

	@Autowired
	private UserAccountDAO userAccountDAO;
	
	@Autowired
	private UserPublicKeyDAO userPublicKeyDAO;
	
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
	@Transactional
	public boolean createAccount(UserAccount userAccount) throws UsernameAlreadyExistsException, BitcoinException, InvalidUsernameException, InvalidEmailException, EmailAlreadyExistsException, InvalidUrlException {
		if (TESTING_MODE)
			return createAccount(userAccount, "fake-address");
		else
			return createAccount(userAccount, getNewPaymentAddress());
	}
	
	private boolean createAccount(UserAccount userAccount, String paymentAddress) throws UsernameAlreadyExistsException, BitcoinException, InvalidUsernameException, InvalidEmailException, EmailAlreadyExistsException, InvalidUrlException {
		UserAccount fromDB = null;
		
		userAccount.setUsername(userAccount.getUsername().trim());
		String username = userAccount.getUsername();
		String email = userAccount.getEmail();
		byte roles = userAccount.getRoles();
		
		if (username == null)
			throw new InvalidUsernameException();

		if (email == null)
			throw new InvalidEmailException();

		String userName = SplitNameHandler.getInstance().getUsername(username);
		String userUrl = SplitNameHandler.getInstance().getServerUrl(username);
		
		if(!userName.matches(Config.USERNAME_REGEX))
			throw new InvalidUsernameException();
		
		if (!userUrl.matches(Config.URL_NAME_REGEX))
			throw new InvalidUrlException();
		
//		if (!username.matches(Config.USERNAME_REGEX))
//			throw new InvalidUsernameException();
		
		if (!email.matches(Config.EMAIL_REGEX))
			throw new InvalidEmailException();
		
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
		
		String passwordHash = CustomPasswordEncoder.getEncodedPassword(userAccount.getPassword());
		userAccount.setPassword(passwordHash);
		userAccount.setPaymentAddress(paymentAddress);
		
		if (roles < 1 || roles > 3)
			roles = Role.USER.getCode();
		
		userAccount.setRoles(roles);
		
		String token = java.util.UUID.randomUUID().toString();
		
		userAccountDAO.createAccount(userAccount, token);
		sendEmailVerificationLink(token, userAccount.getEmail());
		return true;
	}
	
	private void sendEmailVerificationLink(String token, String email){
		Emailer.sendEmailConfirmationLink(token, email);
	}
	
	@Transactional
	public void resendVerificationEmail(UserAccount userAccount) {
		String token;
		try {
			token = userAccountDAO.getVerificationTokenByUserId(userAccount.getId());
			Emailer.sendEmailConfirmationLink(token, userAccount.getEmail());
		} catch (VerificationTokenNotFoundException e) {
			token = java.util.UUID.randomUUID().toString();
			
			userAccountDAO.createEmailVerificationToken(userAccount.getId(), token);
			Emailer.sendEmailConfirmationLink(token, userAccount.getEmail());
		}
	}

	private String getNewPaymentAddress() throws BitcoinException {
		return BitcoindController.getNewAddress();
	}

	@Override
	@Transactional(readOnly = true)
	public UserAccount getByUsername(String username) throws UserAccountNotFoundException {
		return userAccountDAO.getByUsername(username);
	}
	
	private UserAccount getByUsernameIgnoreCaseAndDeletedFlag(String username) throws UserAccountNotFoundException {
		return userAccountDAO.getByUsernameIgnoreCaseAndDeletedFlag(username);
	}
	
	private UserAccount getByEmailIgnoreCaseAndDeletedFlag(String email) throws UserAccountNotFoundException{
		return userAccountDAO.getByEmailIgnoreCaseAndDeletedFlag(email);
	}

	@Override
	@Transactional(readOnly = true)
	public UserAccount getById(long id) throws UserAccountNotFoundException {
		return userAccountDAO.getById(id);
	}

	@Override
	@Transactional(readOnly = true)
	public UserAccount getByEmail(String email) throws UserAccountNotFoundException {
		return userAccountDAO.getByEmail(email);
	}

	@Override
	@Transactional
	public boolean updateAccount(String username, UserAccount updatedAccount) throws UserAccountNotFoundException{
		UserAccount userAccount = getByUsername(username);

		if (updatedAccount.getEmail() != null && !updatedAccount.getEmail().isEmpty())
			userAccount.setEmail(updatedAccount.getEmail());

		if (updatedAccount.getPassword() != null && !updatedAccount.getPassword().isEmpty())
			userAccount.setPassword(CustomPasswordEncoder.getEncodedPassword(updatedAccount.getPassword()));
		
		//TODO: no logic behind....changes the role always to user 
//		if (UserRoles.isValidRole(updatedAccount.getRoles()))
//			userAccount.setRoles(updatedAccount.getRoles());

		
		userAccountDAO.updateAccount(userAccount);
		return true;
	}

	@Override
	@Transactional
	public boolean delete(String username) throws UserAccountNotFoundException, BalanceNotZeroException {
		userAccountDAO.delete(username);
		return true;
	}

	@Override
	@Transactional
	public boolean verifyEmailAddress(String verificationToken) {
		try {
			userAccountDAO.verifyEmail(verificationToken);
			return true;
		} catch (UserAccountNotFoundException | VerificationTokenNotFoundException e) {
			return false;
		} 
	}

	@Override
	@Transactional
	public void resetPasswordRequest(String emailAddress) throws UserAccountNotFoundException {
		UserAccount user = userAccountDAO.getByEmail(emailAddress);
		String token = java.util.UUID.randomUUID().toString();
		userAccountDAO.createPasswordResetToken(user, token);
		
		Emailer.sendResetPasswordLink(user, token);
	}


	@Override
	@Transactional(readOnly = true)
	public boolean isValidResetPasswordLink(String resetPasswordToken) {
		try {
			ResetPassword resetPassword = userAccountDAO.getResetPassword(resetPasswordToken);
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
	@Transactional
	public void deleteOldResetPasswords(){
		Date currentDate = new Date();
		List<ResetPassword> list = userAccountDAO.getAllResetPassword();
		for(int i=0; i< list.size();i++){
			if(list.get(i).getCreationDate().getTime() < (currentDate.getTime() - Config.DELETE_TOKEN_LIMIT)){
				try {
					userAccountDAO.deleteResetPassword(list.get(i).getToken());
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
	@Transactional(readOnly = true)
	public UserAccount getByResetPasswordToken(String token) throws VerificationTokenNotFoundException, UserAccountNotFoundException{
		return userAccountDAO.getByResetPasswordToken(token);
	}
	
	@Override
	@Transactional
	public boolean resetPassword(PasswordMatcher matcher){
		if (!isValidResetPasswordLink(matcher.getToken())) {
			return false;
		}
		
		//clean table and delete all old tokens
		deleteOldResetPasswords();
		
		UserAccount user;
		if (matcher.compare()){
			try {
				user = getByResetPasswordToken(matcher.getToken());
				user.setPassword(matcher.getPw1());
				updateAccount(user.getUsername(), user);
				userAccountDAO.deleteResetPassword(matcher.getToken());		
				return true;
			} catch (VerificationTokenNotFoundException | UserAccountNotFoundException e) {
				return false;
			}
		} else {
			return false;
		}
	}
	
	@Override
	@Transactional
	public byte saveUserPublicKey(long userId, PKIAlgorithm algorithm, String publicKey) throws UserAccountNotFoundException {
		return userPublicKeyDAO.saveUserPublicKey(userId, algorithm, publicKey);
	}
	
	@Override
	@Transactional(readOnly = true)
	public List<UserPublicKey> getUserPublicKey(long userId) throws UserAccountNotFoundException {
		return userPublicKeyDAO.getUserPublicKeys(userId);
	}

	@Override
	@Transactional(readOnly = true)
	public List<UserPublicKey> getUserPublicKeys(long id) {
		return userPublicKeyDAO.getUserPublicKeys(id);
	}
	
	@Override
	@Transactional(readOnly = true)
	public String getVerificationTokenByUserId(long id) throws VerificationTokenNotFoundException {
		return userAccountDAO.getVerificationTokenByUserId(id);
	}
	
	@Override
	@Transactional(readOnly = true)
	public List<ResetPassword> getAllResetPassword() {
		return userAccountDAO.getAllResetPassword();
	}
	
	@Override
	@Transactional(readOnly = true)
	public String getTokenForUser(long userID) throws VerificationTokenNotFoundException {
		return userAccountDAO.getVerificationTokenByUserId(userID);
	}

	@Override
	@Transactional
	public void updateAccount(UserAccount userAccount) throws UserAccountNotFoundException {
		userAccountDAO.updateAccount(userAccount);
	}

	@Override
	@Transactional(readOnly = true)
	public List<UserAccount> getAdmins(){
		List<UserAccount> users = new ArrayList<UserAccount>(); 
		users = userAccountDAO.getAllUsersByRoles(Role.ADMIN);
		users.addAll(userAccountDAO.getAllUsersByRoles(Role.BOTH));
		return users;
	}
	
	@Override
	@Transactional(readOnly = true)
	public List<UserAccount> getAllUsers(){
		List<UserAccount> users; 
		users = userAccountDAO.getAllUserAccounts();
		return users;
	}
	
	@Override
	@Transactional(readOnly=true)
	public List<UserAccount> getUsers(){
		List<UserAccount> users; 
		users = userAccountDAO.getAllUsersByRoles(Role.USER);
		users.addAll(userAccountDAO.getAllUsersByRoles(Role.BOTH));
		return users;
	}
	
	@Override
	@Transactional(readOnly = true)
	public UserAccount getLoggedAdmin(String username) {
		try {
			return userAccountDAO.getByUsername(username);
		} catch (UserAccountNotFoundException e) {
			return null;
		}
	}

	@Override
	@Transactional
	public void changeRoleBoth(UserAccount admin) throws UserAccountNotFoundException {
		admin.setRoles(Role.BOTH.getCode());
		userAccountDAO.updateAccount(admin);
		Emailer.sendUpdateRoleBothLink(admin);
	}
	
	/**
	 * Deletes every time when an admin user is created the old entries ({@link AdminRole}s) (older than 24h) from the table.
	 */
	@Transactional
	public void deleteOldCreateAdmin(){
		Date currentDate = new Date();
		List<AdminRole> list = userAccountDAO.getAllAdminRole();
		for(int i=0; i< list.size();i++){
			if(list.get(i).getCreationDate().getTime() < (currentDate.getTime() - Config.DELETE_TOKEN_LIMIT)){
				try {
					userAccountDAO.deleteAdminRole(list.get(i).getToken());
				} catch (VerificationTokenNotFoundException e) {
				}
			}
		}
	}
	
	@Override
	@Transactional
	public boolean createRole(AdminObject admin) {
		if (!isValidAdminRoleLink(admin.getToken())) {
			return false;
		}
		
		//clean table and delete all old tokens
		deleteOldResetPasswords();

		if (admin.compare()){
			try {
				String username = admin.getUsername() + Config.SPLIT_USERNAME + SecurityConfig.URL;
				String passwordHash = CustomPasswordEncoder.getEncodedPassword(admin.getPw1());
				UserAccount userAccount = new UserAccount(username,admin.getEmail(),passwordHash);
				userAccount.setRoles(Role.ADMIN.getCode());
				String token = java.util.UUID.randomUUID().toString();
				userAccountDAO.createAccount(userAccount,token);
				try {
					UserAccount fromDB = getByUsername(username);
					if (!fromDB.isEmailVerified()) {
						resendVerificationEmail(fromDB);
					}
				} catch (UserAccountNotFoundException e) {
					//
				}
				userAccountDAO.deleteAdminRole(admin.getToken());	
				return true;
			} catch (VerificationTokenNotFoundException e) {
				return false;
			}
		} else {
			return false;
		}
	}

	@Override
	@Transactional
	public void changeRoleAdmin(String emailAddress) throws UserAccountNotFoundException {
		String token = java.util.UUID.randomUUID().toString();
		userAccountDAO.createAdminToken(emailAddress, token);
		
		Emailer.sendCreateRoleAdminLink(emailAddress, token);
	}

	@Override
	@Transactional
	public boolean isValidAdminRoleLink(String adminToken) {
		try {
			AdminRole adminRole = userAccountDAO.getCreateAdmin(adminToken);
			if (adminRole == null) {
				return false;
			} else {
				// checks if token has been created during the last 1h
				if (adminRole.getCreationDate().getTime() >= (new Date().getTime() - Config.VALID_TOKEN_LIMIT)) {
					return true;
				} else {
					return false;
				}
			}
		} catch (VerificationTokenNotFoundException e) {
			return false;
		}
	}

	@Override
	@Transactional(readOnly = true)
    public List<UserAccount> getAllUserAccounts() {
	    return userAccountDAO.getAllUserAccounts();
    }
	
	@Override
	@Transactional(readOnly = true)
    public BigDecimal getSumOfUserAccountBalances() {
	    return userAccountDAO.getSumOfAccountBalance();
    }

	@Override
	@Transactional
	public void sendMailToAll(String subject, String text){
		List<String> emails = getEmailOfAllUsers();
		String emailToSend = "";
		for (String email : emails) {
			emailToSend += email + ",";
		}
		Emailer.sendMessageToAllUsers(emailToSend, subject, text);
	}

	@Override
	@Transactional
	public List<String> getEmailOfAllUsers() {
		List<String> emails = new ArrayList<String>();
		emails = userAccountDAO.getEmailOfAllUsersByRoles(Role.USER);
		emails.addAll(userAccountDAO.getEmailOfAllUsersByRoles(Role.ADMIN));
		emails.addAll(userAccountDAO.getEmailOfAllUsersByRoles(Role.BOTH));
		return emails;
	}

	@Override
	@Transactional(readOnly=true)
	public UserAccount getAdminEmail() {
		return userAccountDAO.getAdminEmail();
	}

}