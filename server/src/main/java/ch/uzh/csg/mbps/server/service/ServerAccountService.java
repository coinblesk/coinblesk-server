package ch.uzh.csg.mbps.server.service;

import java.math.BigDecimal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import org.hibernate.HibernateException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ch.uzh.csg.mbps.customserialization.PKIAlgorithm;
import ch.uzh.csg.mbps.server.clientinterface.IActivities;
import ch.uzh.csg.mbps.server.clientinterface.IServerAccount;
import ch.uzh.csg.mbps.server.dao.ServerAccountDAO;
import ch.uzh.csg.mbps.server.dao.ServerPublicKeyDAO;
import ch.uzh.csg.mbps.server.dao.UserAccountDAO;
import ch.uzh.csg.mbps.server.domain.DbTransaction;
import ch.uzh.csg.mbps.server.domain.ServerAccount;
import ch.uzh.csg.mbps.server.domain.UserAccount;
import ch.uzh.csg.mbps.server.util.AuthenticationInfo;
import ch.uzh.csg.mbps.server.util.BitcoindController;
import ch.uzh.csg.mbps.server.util.Config;
import ch.uzh.csg.mbps.server.util.SecurityConfig;
import ch.uzh.csg.mbps.server.util.Subjects;
import ch.uzh.csg.mbps.server.util.exceptions.BalanceNotZeroException;
import ch.uzh.csg.mbps.server.util.exceptions.InvalidEmailException;
import ch.uzh.csg.mbps.server.util.exceptions.InvalidPublicKeyException;
import ch.uzh.csg.mbps.server.util.exceptions.InvalidUrlException;
import ch.uzh.csg.mbps.server.util.exceptions.ServerAccountNotFoundException;
import ch.uzh.csg.mbps.server.util.exceptions.UrlAlreadyExistsException;
import ch.uzh.csg.mbps.server.util.exceptions.UserAccountNotFoundException;

import com.azazar.bitcoin.jsonrpcclient.BitcoinException;

/**
 * Service class for {@link ServerAccount}.
 *
 */
@Service
public class ServerAccountService implements IServerAccount {
	
	private static boolean TESTING_MODE = false;
	
	@Autowired
	private ServerAccountDAO serverAccountDAO;
	@Autowired
	private UserAccountDAO userAccountDAO;
	@Autowired
	private ServerPublicKeyDAO serverPublicKeyDAO;
	@Autowired
	private IActivities activitiesService;
	
	
	
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
	@Transactional(readOnly = true)
	public boolean checkIfExistsByUrl(String url) {
		try {
			//see for matches in db ignoring cases and deletion status
			getByUrlIgnoreCaseAndDeletedFlag(url);
			return true;
		} catch (ServerAccountNotFoundException e) {
			return false;
		}
	}

	@Override
	@Transactional
	public ServerAccount prepareAccount(UserAccount user, ServerAccount otherAccount) throws UserAccountNotFoundException, InvalidUrlException, InvalidEmailException {
		String otherUrl = otherAccount.getUrl();
		String otherEmail = otherAccount.getEmail();
		
		ServerAccount serverAccount = new ServerAccount(SecurityConfig.URL, user.getEmail());

		if (!otherUrl.matches(Config.URL_NAME_REGEX))
			throw new InvalidUrlException();
		
		if (!otherEmail.matches(Config.EMAIL_REGEX))
			throw new InvalidEmailException();
		
		if(!TESTING_MODE)
			activitiesService.activityLog(user.getUsername(), Subjects.CREATE_SERVER_ACCOUNT,"Create a new relation with the server " + otherUrl + " and email " + otherEmail);
		
		return serverAccount;
	}


	@Override
	@Transactional
	public boolean persistAccount(ServerAccount serverAccount) throws UrlAlreadyExistsException, BitcoinException, InvalidUrlException, InvalidEmailException {
		Date date = new Date();
		if (TESTING_MODE){
			String strDate = "2014-06-19 14:35:54.0";
			try {
				Date date2 = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(strDate);
				return createAccount(serverAccount, "fake-address", date2);
			} catch (ParseException e) {
				e.printStackTrace();
				return createAccount(serverAccount, "fake-address", date);
			}
		} else {
			return createAccount(serverAccount, getNewPayinAddress(), date);
		}
	}

	/**
	 * Creates {@link ServerAccount} with a payinAddress.
	 * 
	 * @param serverAccount
	 * @param payinAddress
	 * @return boolean
	 * @throws UrlAlreadyExistsException
	 * @throws BitcoinException
	 * @throws InvalidUrlException
	 * @throws InvalidEmailException
	 * @throws InvalidPublicKeyException 
	 */
	private boolean createAccount(ServerAccount serverAccount, String payinAddress, Date date) throws UrlAlreadyExistsException, InvalidUrlException, InvalidEmailException {
		if(serverAccount.getUrl() == null)
			throw new InvalidUrlException();

		serverAccount.setUrl(serverAccount.getUrl().trim());
		String url = serverAccount.getUrl();
		String email = serverAccount.getEmail();
		
		if (url == null)
			throw new InvalidUrlException();

		if (email == null)
			throw new InvalidEmailException();
		
		if (!url.matches(Config.URL_NAME_REGEX))
			throw new InvalidUrlException();
		
		if (!email.matches(Config.EMAIL_REGEX))
			throw new InvalidEmailException();
		
		try {
			//see for matches in db ignoring cases and deletion status
			getByUrlIgnoreCaseAndDeletedFlag(serverAccount.getUrl());
			throw new UrlAlreadyExistsException(serverAccount.getUrl());
		} catch (ServerAccountNotFoundException e) {
			//do nothing, since this happens when a new account is created with a unique url
		}
		
		serverAccount = new ServerAccount(serverAccount.getUrl(), serverAccount.getEmail());
		
		serverAccount.setPayinAddress(payinAddress);
		serverAccount.setCreationDate(date);

		try {
			serverAccountDAO.createAccount(serverAccount);
		} catch (HibernateException e) {
			return false;
		}
		if(!TESTING_MODE)
			activitiesService.activityLog("n.V.", Subjects.CREATE_SERVER_ACCOUNT,"Create a new relation with the server " + url + " and email " + email);
	
		return true;
	}

	private String getNewPayinAddress() throws BitcoinException {
		return BitcoindController.getNewAddress();
	}
	
	@Override
	@Transactional(readOnly = true)
	public ServerAccount getByUrl(String url) throws ServerAccountNotFoundException {
		return serverAccountDAO.getByUrl(url);
	}

	@Override
	@Transactional(readOnly = true)
	public ServerAccount getById(long id) throws ServerAccountNotFoundException {
		return serverAccountDAO.getById(id);
	}
	
	@Transactional(readOnly = true)
	public ServerAccount getByUrlIgnoreCaseAndDeletedFlag(String url) throws ServerAccountNotFoundException{
		return serverAccountDAO.getByUrlIgnoreCaseAndDeletedFlag(url);
	}

	@Override
	@Transactional
	public boolean updateAccount(String url, ServerAccount updatedAccount) throws ServerAccountNotFoundException {
		ServerAccount serverAccount = getByUrl(url);
		String title = "";
		String message = "";
		String username;
		try {
			UserAccount user = userAccountDAO.getByUsername(AuthenticationInfo.getPrincipalUsername());
			username = user.getUsername();
		} catch (UserAccountNotFoundException e) {
			username = "n.V.";
		}
		
		if (updatedAccount.getEmail() != null && !updatedAccount.getEmail().isEmpty()){			
			title = Subjects.UPDATE_EMAIL;
			message = "Email is updated to " + updatedAccount.getEmail();
			serverAccount.setEmail(updatedAccount.getEmail());
		}
		
		if (updatedAccount.getUrl() != null && !updatedAccount.getUrl().isEmpty()){
			title = Subjects.UPDATE_URL;
			message = "URL is updated to " + updatedAccount.getUrl();			
			serverAccount.setUrl(updatedAccount.getUrl());
		}
		
		if (updatedAccount.getTrustLevel() != serverAccount.getTrustLevel()){
			title = Subjects.UPGRADE_TRUST_LEVEL;
			message = "Trust level is updated to " + updatedAccount.getTrustLevel();			
			serverAccount.setTrustLevel(updatedAccount.getTrustLevel());
		}

		if (updatedAccount.getBalanceLimit() != null && updatedAccount.getBalanceLimit() != serverAccount.getBalanceLimit()){			
			title = Subjects.UPDATE_BALANCE_LIMIT;
			message = "Balance limit is updated to " + updatedAccount.getBalanceLimit();
			serverAccount.setBalanceLimit(updatedAccount.getBalanceLimit());
		}
		
		if (updatedAccount.getUserBalanceLimit() != null && updatedAccount.getUserBalanceLimit() != serverAccount.getUserBalanceLimit()){
			title = Subjects.UPDATE_USER_BALANCE_LIMIT;
			message = "User balance limit is updated to " + updatedAccount.getUserBalanceLimit();			
			serverAccount.setUserBalanceLimit(updatedAccount.getUserBalanceLimit());
		}
		
		serverAccountDAO.updatedAccount(serverAccount);

		if(!TESTING_MODE)
			activitiesService.activityLog(username, title, message);

		return true;
	}

	@Override
	@Transactional
	public boolean updatePayoutAddressAccount(String url, ServerAccount updatedAccount) throws ServerAccountNotFoundException {
		ServerAccount serverAccount = getByUrl(url);
		String payoutAddress = serverAccount.getPayoutAddress() != null ? serverAccount.getPayoutAddress() : "";
		String title = "";
		String message = "";
		String username;
		try {
			UserAccount user = userAccountDAO.getByUsername(AuthenticationInfo.getPrincipalUsername());
			username = user.getUsername();
		} catch (UserAccountNotFoundException e) {
			username = Config.NOT_AVAILABLE;
		}
		
		if (updatedAccount.getPayoutAddress() != null && !updatedAccount.getPayoutAddress().isEmpty() && 
				!updatedAccount.getPayoutAddress().equals(payoutAddress)){			
			title = Subjects.UPDATE_PAYOUT_ADDRESS;
			message = "Payout address is updated to " + updatedAccount.getPayoutAddress();
			serverAccount.setPayoutAddress(updatedAccount.getPayoutAddress());
		}
		
		serverAccountDAO.updatedAccount(serverAccount);
		
		if(!TESTING_MODE)
			activitiesService.activityLog(username, title, message);
		
		return true;
	}

	@Override
	@Transactional
	public boolean deleteAccount(String url) throws ServerAccountNotFoundException, BalanceNotZeroException {
		boolean success = serverAccountDAO.delete(url);
		if(success && !TESTING_MODE){
			String username;
			try {
				UserAccount user = userAccountDAO.getByUsername(AuthenticationInfo.getPrincipalUsername());
				username = user.getUsername();
			} catch (UserAccountNotFoundException e) {
				username = "n.V.";
			}
			activitiesService.activityLog(username, Subjects.DELETE_ACCOUNT, "The server account "+ url +" is deleted.");
		}
		return success;
	}

	@Override
	@Transactional(readOnly = true)
	public List<ServerAccount> getByTrustLevel(int trustlevel) {
		return serverAccountDAO.getByTrustLevel(trustlevel);
	}

	@Override
	@Transactional(readOnly = true)
	public List<ServerAccount> getAll() {
		return serverAccountDAO.getAllServerAccounts();
	}

	@Override
	@Transactional(readOnly = true)
	public List<ch.uzh.csg.mbps.model.ServerAccount> getServerAccounts(int urlPage) {
		return serverAccountDAO.getServerAccounts(urlPage);
	}

	@Override
	@Transactional(readOnly = true)
	public long getAccountsCount() {
		return serverAccountDAO.getAccountCount();
	}
	
	@Override
	@Transactional(readOnly = true)
	public boolean checkPredefinedDeleteArguments(String url) throws ServerAccountNotFoundException, BalanceNotZeroException, HibernateException{
		return serverAccountDAO.checkPredefinedDeleteArguments(url);
	}
	
	@Override
	@Transactional(readOnly=true)
	public boolean isDeletedByUrl(String url) {
		return serverAccountDAO.isDeletedByUrl(url);
	}
	
	@Override
	@Transactional(readOnly=true)
	public boolean isDeletedById(long id) {
		return serverAccountDAO.isDeletedById(id);
	}

	@Override
	@Transactional
	public byte saveServerPublicKey(long serverId, PKIAlgorithm algorithm, String publicKey) throws UserAccountNotFoundException, ServerAccountNotFoundException {
		return serverPublicKeyDAO.saveUserPublicKey(serverId, algorithm, publicKey);
	}

	@Override
	public void undeleteServerAccountByUrl(String url) throws ServerAccountNotFoundException {
		ServerAccount account = serverAccountDAO.getByUrlIgnoreDelete(url);
		account.setDeleted(false);
		account.setActiveBalance(new BigDecimal("0.00000000"));
		account.setBalanceLimit(new BigDecimal("0.00000000"));
		account.setUserBalanceLimit(new BigDecimal("0.00000000"));
		account.setCreationDate(new Date());
		serverAccountDAO.persitsAccount(account);
	}
	
	@Override
	public void undeleteServerAccountById(Long id) throws ServerAccountNotFoundException {
		ServerAccount account = serverAccountDAO.getByIdIgnoreDelete(id);
		account.setDeleted(false);
		account.setActiveBalance(new BigDecimal("0.00000000"));
		account.setBalanceLimit(new BigDecimal("0.00000000"));
		account.setUserBalanceLimit(new BigDecimal("0.00000000"));
		account.setCreationDate(new Date());
		serverAccountDAO.persitsAccount(account);
	}

	@Override
	@Transactional
	public void updateTrustLevel(String url, int newLevel) throws ServerAccountNotFoundException {
		ServerAccount updatedAccount = new ServerAccount();
		updatedAccount.setTrustLevel(newLevel);
		updateAccount(url, updatedAccount);
	}


	@Override
	@Transactional
	public boolean verifyTrustAndBalance(ServerAccount account, BigDecimal amount) {
		ServerAccount serverAccount = null;
		boolean verified = false;
		
		try {
			serverAccount = getByUrl(account.getUrl());
		} catch (ServerAccountNotFoundException e) {
			return false;
		}
		
		if(serverAccount.getTrustLevel() == 1){
			verified = verifyHypridTrustBalance(serverAccount, amount);
		} else if(serverAccount.getTrustLevel() == 2){
			verified = verifyFullTrustBalance(serverAccount, amount);
		}
		
		return verified;
	}

	/**
	 * Verifies if the Balance limit is not reached by the new transaction.
	 * 
	 * @param serverAccount
	 * @param amount
	 * @return boolean
	 */
	private boolean verifyFullTrustBalance(ServerAccount serverAccount, BigDecimal amount) {
		//Amount how much active balance will be if the transaction will be verified. 
		BigDecimal changedActiveBalance = serverAccount.getActiveBalance().abs().add(amount);
		if(serverAccount.getBalanceLimit().subtract(changedActiveBalance).compareTo(BigDecimal.ZERO) > 0)
			return true;

		return false;
	}

	private boolean verifyHypridTrustBalance(ServerAccount serverAccount, BigDecimal amount) {
		//This method verifies that amount in comparison to the balance limit and bitcoins in escrow
		return false;
	}

	@Override
	@Transactional
	public void persistsTransactionAmount(ServerAccount serverAccount, DbTransaction dbTransaction, boolean received) {
		try {
			ServerAccount account = getByUrl(serverAccount.getUrl());
			serverAccountDAO.persistsTransaction(account, dbTransaction.getAmount(), received);
		} catch (ServerAccountNotFoundException e) {
			//ignore it should no happen
		}
		
	}	
}
