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

import ch.uzh.csg.mbps.server.clientinterface.IActivities;
import ch.uzh.csg.mbps.server.clientinterface.IServerAccount;
import ch.uzh.csg.mbps.server.dao.ServerAccountDAO;
import ch.uzh.csg.mbps.server.dao.UserAccountDAO;
import ch.uzh.csg.mbps.server.domain.ServerAccount;
import ch.uzh.csg.mbps.server.domain.UserAccount;
import ch.uzh.csg.mbps.server.util.ActivitiesTitle;
import ch.uzh.csg.mbps.server.util.AuthenticationInfo;
import ch.uzh.csg.mbps.server.util.BitcoindController;
import ch.uzh.csg.mbps.server.util.Config;
import ch.uzh.csg.mbps.server.util.Constants;
import ch.uzh.csg.mbps.server.util.SecurityConfig;
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
	private IActivities activitiesService;

	//TODO: mehmet: javadoc
	
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
	public boolean checkIfExistsByUrl(String url) throws UrlAlreadyExistsException{
		try {
			//see for matches in db ignoring cases and deletion status
			getByUrlIgnoreCaseAndDeletedFlag(url);
			throw new UrlAlreadyExistsException(url);
		} catch (ServerAccountNotFoundException e) {
			return true;
		}
	}

	@Override
	@Transactional
	public ServerAccount prepareAccount(UserAccount user, ServerAccount otherAccount) throws UserAccountNotFoundException, InvalidPublicKeyException, InvalidUrlException, InvalidEmailException {
		if (TESTING_MODE){
			return prepareAccount(user, otherAccount, "fake-keys");
		} else {
			String publicKey = Constants.SERVER_KEY_PAIR.getPublicKey();
			//CustomPublicKey cpk = new CustomPublicKey(Constants.SERVER_KEY_PAIR.getKeyNumber(), Constants.SERVER_KEY_PAIR.getPkiAlgorithm(), Constants.SERVER_KEY_PAIR.getPublicKey());
			return prepareAccount(user, otherAccount, publicKey);
		}
	}
	
	/**
	 * Prepares {@link ServerAccount} with the data which will be send to the other server. 
	 * 
	 * @param serverAccount
	 * @param newPayinAddress
	 * @param date
	 * @return ServerAccount
	 * @throws InvalidUrlException
	 * @throws InvalidEmailException 
	 * @throws InvalidPublicKeyException
	 * @throws UrlAlreadyExistsException 
	 * @throws UserAccountNotFoundException
	 */
	private ServerAccount prepareAccount(UserAccount user, ServerAccount otherAccount, String publicKey) throws UserAccountNotFoundException, InvalidPublicKeyException, InvalidUrlException, InvalidEmailException {
		String otherUrl = otherAccount.getUrl();
		String otherEmail = otherAccount.getEmail();
		
		ServerAccount serverAccount = new ServerAccount(SecurityConfig.BASE_URL, user.getEmail(), publicKey);

		if (serverAccount.getPublicKey() == null)
			throw new InvalidPublicKeyException();

		if (!otherUrl.matches(Config.URL_NAME_REGEX))
			throw new InvalidUrlException();
		
		if (!otherEmail.matches(Config.EMAIL_REGEX))
			throw new InvalidEmailException();
		
		if(!TESTING_MODE)
			activitiesService.activityLog(user.getUsername(), ActivitiesTitle.CREATE_SERVER_ACCOUNT,"Create a new relation with the server " + otherUrl + " and email " + otherEmail);
		
		return serverAccount;
	}

	@Override
	@Transactional
	public boolean persistAccount(ServerAccount serverAccount) throws UrlAlreadyExistsException, BitcoinException, InvalidUrlException, InvalidEmailException, InvalidPublicKeyException {
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
	private boolean createAccount(ServerAccount serverAccount, String payinAddress, Date date) throws UrlAlreadyExistsException, BitcoinException, InvalidUrlException, InvalidEmailException, InvalidPublicKeyException {
		serverAccount.setUrl(serverAccount.getUrl().trim());
		String url = serverAccount.getUrl();
		String email = serverAccount.getEmail();
		String publicKey = serverAccount.getPublicKey();
		
		if (url == null)
			throw new InvalidUrlException();

		if (email == null)
			throw new InvalidEmailException();

		if (publicKey == null)
			throw new InvalidPublicKeyException();
		
		if (!url.matches(Config.URL_REGEX))
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
		
		serverAccount = new ServerAccount(serverAccount.getUrl(), serverAccount.getEmail(), serverAccount.getPublicKey());
		
		serverAccount.setPayoutAddress(payinAddress);
		serverAccount.setCreationDate(date);

		try {
			serverAccountDAO.createAccount(serverAccount);
		} catch (HibernateException e) {
			return false;
		}
		if(!TESTING_MODE)
			activitiesService.activityLog("n.V.", ActivitiesTitle.CREATE_SERVER_ACCOUNT,"Create a new relation with the server " + url + " and email " + email);
	
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
		
		if (updatedAccount.getEmail() != null && !updatedAccount.getEmail().isEmpty())
			serverAccount.setEmail(updatedAccount.getEmail());
		
		if (updatedAccount.getUrl() != null && !updatedAccount.getUrl().isEmpty())
			serverAccount.setUrl(updatedAccount.getUrl());
		
		if (updatedAccount.getTrustLevel() != serverAccount.getTrustLevel())
			serverAccount.setTrustLevel(updatedAccount.getTrustLevel());

		if (updatedAccount.getBalanceLimit() != serverAccount.getBalanceLimit())
			serverAccount.setBalanceLimit(updatedAccount.getBalanceLimit());
		
		serverAccountDAO.updatedAccount(serverAccount);

		if(!TESTING_MODE){
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
				title = ActivitiesTitle.UPDATE_EMAIL;
				message = "Email is updated to " + updatedAccount.getEmail();
			}
				
			if (updatedAccount.getUrl() != null && !updatedAccount.getUrl().isEmpty()){
				title = ActivitiesTitle.UPDATE_URL;
				message = "URL is updated to " + updatedAccount.getUrl();
			}
				
			if (updatedAccount.getTrustLevel() != serverAccount.getTrustLevel()){
				title = ActivitiesTitle.UPGRADE_TRUST_LEVEL;
				message = "Trust level is updated to " + updatedAccount.getTrustLevel();
			}
					
			if (updatedAccount.getBalanceLimit() != serverAccount.getBalanceLimit()){
				title = ActivitiesTitle.UPDATE_BALANCE_LIMIT;
				message = "Balance limit is updated to " + updatedAccount.getBalanceLimit();
			}
			
			activitiesService.activityLog(username, title, message);
		}
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
			activitiesService.activityLog(username, ActivitiesTitle.DELETE_ACCOUNT, "The server account "+ url +" is deleted.");
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

	//TODO: mehmet
	
	@Override
	@Transactional
	public void updateTrustLevel(String url, int oldLevel, int newLevel) throws ServerAccountNotFoundException {
		//TODO: check if upgrade or downgrade
	}
	
	@Override
	@Transactional
	public void updateBalanceLimit(String url, BigDecimal oldLimit, BigDecimal newLimit) {
		//TODO: communicate balance limit asymmetric balance limits
	}
}
