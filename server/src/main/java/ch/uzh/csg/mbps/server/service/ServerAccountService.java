package ch.uzh.csg.mbps.server.service;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import org.hibernate.HibernateException;
import org.springframework.stereotype.Service;

import ch.uzh.csg.mbps.server.clientinterface.IServerAccount;
import ch.uzh.csg.mbps.server.dao.ServerAccountDAO;
import ch.uzh.csg.mbps.server.domain.ServerAccount;
import ch.uzh.csg.mbps.server.util.BitcoindController;
import ch.uzh.csg.mbps.server.util.Config;
import ch.uzh.csg.mbps.server.util.exceptions.BalanceNotZeroException;
import ch.uzh.csg.mbps.server.util.exceptions.InvalidEmailException;
import ch.uzh.csg.mbps.server.util.exceptions.InvalidPublicKeyException;
import ch.uzh.csg.mbps.server.util.exceptions.InvalidUrlException;
import ch.uzh.csg.mbps.server.util.exceptions.ServerAccountNotFoundException;
import ch.uzh.csg.mbps.server.util.exceptions.UrlAlreadyExistsException;

import com.azazar.bitcoin.jsonrpcclient.BitcoinException;

/**
 * Service class for {@link ServerAccount}.
 *
 */
@Service
public class ServerAccountService implements IServerAccount{
	private static ServerAccountService serverAccountService;
	private static boolean TESTING_MODE = false;

	private ServerAccountService() {
	}
	
	//TODO: mehmet: javadoc
	/**
	 * 
	 * @return
	 */
	public static ServerAccountService getInstance() {
		if (serverAccountService == null) {
			serverAccountService = new ServerAccountService();
		}
			
		return serverAccountService;
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
	public boolean createAccount(ServerAccount serverAccount) throws UrlAlreadyExistsException, BitcoinException, InvalidUrlException, InvalidEmailException, InvalidPublicKeyException {
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
	 * 
	 * @param serverAccount
	 * @param payinAddress
	 * @return
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
		
		serverAccount.setPayinAddress(payinAddress);
		serverAccount.setCreationDate(date);
		
		String token = java.util.UUID.randomUUID().toString();
		try {
			ServerAccountDAO.createAccount(serverAccount, token);
		} catch (HibernateException e) {
			return false;
		}
		return true;
	}

	private String getNewPayinAddress() throws BitcoinException {
		return BitcoindController.getNewAddress();
	}
	
	@Override
	public ServerAccount getByUrl(String url) throws ServerAccountNotFoundException {
		return ServerAccountDAO.getByUrl(url);
	}

	@Override
	public ServerAccount getById(long id) throws ServerAccountNotFoundException {
		return ServerAccountDAO.getById(id);
	}
	
	public ServerAccount getByUrlIgnoreCaseAndDeletedFlag(String url) throws ServerAccountNotFoundException{
		return ServerAccountDAO.getByUrlIgnoreCaseAndDeletedFlag(url);
	}

	@Override
	public boolean updateAccount(String url, ServerAccount updatedAccount) throws ServerAccountNotFoundException {
		ServerAccount serverAccount = getByUrl(url);
		
		if (updatedAccount.getEmail() != null && !updatedAccount.getEmail().isEmpty())
			serverAccount.setEmail(updatedAccount.getEmail());
		
		if (updatedAccount.getUrl() != null && !updatedAccount.getUrl().isEmpty())
			serverAccount.setUrl(updatedAccount.getUrl());
		
		if (updatedAccount.getTrustLevel() != serverAccount.getTrustLevel())
			serverAccount.setTrustLevel(updatedAccount.getTrustLevel());
		
		try{
			ServerAccountDAO.updatedAccount(serverAccount);
		} catch (HibernateException e) {
			return false;
		}
		
		return true;
	}

	@Override
	public boolean delete(String url) throws ServerAccountNotFoundException, BalanceNotZeroException {
		try {
			ServerAccountDAO.delete(url);
			return true;
		} catch (HibernateException e) {
			return false;
		}
	}

	@Override
	public List<ServerAccount> getByTrustLevel(int trustlevel) {
		return ServerAccountDAO.getByTrustLevel(trustlevel);
	}

	@Override
	public List<ServerAccount> getAll() {
		return ServerAccountDAO.getAllServerAccounts();
	}

}