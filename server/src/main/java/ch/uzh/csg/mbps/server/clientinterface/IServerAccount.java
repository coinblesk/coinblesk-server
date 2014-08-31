package ch.uzh.csg.mbps.server.clientinterface;

import java.math.BigDecimal;
import java.util.List;

import org.hibernate.HibernateException;

import ch.uzh.csg.mbps.server.domain.ServerAccount;
import ch.uzh.csg.mbps.server.domain.UserAccount;
import ch.uzh.csg.mbps.server.util.exceptions.BalanceNotZeroException;
import ch.uzh.csg.mbps.server.util.exceptions.InvalidEmailException;
import ch.uzh.csg.mbps.server.util.exceptions.InvalidPublicKeyException;
import ch.uzh.csg.mbps.server.util.exceptions.InvalidUrlException;
import ch.uzh.csg.mbps.server.util.exceptions.ServerAccountNotFoundException;
import ch.uzh.csg.mbps.server.util.exceptions.UrlAlreadyExistsException;
import ch.uzh.csg.mbps.server.util.exceptions.UserAccountNotFoundException;

import com.azazar.bitcoin.jsonrpcclient.BitcoinException;

public interface IServerAccount {
	
	//TODO: mehmet: javadoc
	/**
	 * 
	 * Stores the server account into the DB
	 * 
	 * @param serverAccount Data of the new server
	 * @return Server account
	 * @throws UrlAlreadyExistsException
	 * @throws BitcoinException
	 * @throws InvalidUrlException
	 * @throws InvalidEmailException
	 * @throws InvalidPublicKeyException 
	 */
	public boolean persistAccount(ServerAccount serverAccount) throws UrlAlreadyExistsException, BitcoinException, InvalidUrlException, InvalidEmailException, InvalidPublicKeyException;
	
	/**
	 * 
	 * @param url
	 * @return Server Account
	 * @throws ServerAccountNotFoundException
	 */
	public ServerAccount getByUrl(String url) throws ServerAccountNotFoundException;

	/**
	 * 
	 * @param id
	 * @return Server Account
	 * @throws UserAccountNotFoundException
	 */
	public ServerAccount getById(long id) throws ServerAccountNotFoundException;

	/**
	 * 
	 * Updates the server url or email address or balance limit or trust level  
	 * 
	 * @param url
	 * @param updatedAccount
	 * @return boolean
	 * @throws UserAccountNotFoundException
	 */
	public boolean updateAccount(String url, ServerAccount updatedAccount) throws ServerAccountNotFoundException;

	/**
	 * Sets the deleted flag
	 * 
	 * @param url
	 * @return boolean
	 * @throws UserAccountNotFoundException
	 * @throws BalanceNotZeroException
	 */
	public boolean deleteAccount(String url) throws ServerAccountNotFoundException, BalanceNotZeroException;
	
	/**
	 * 
	 * @return list of {@link ServerAccount}s
	 */
	public List<ServerAccount> getByTrustLevel(int trustlevel);
	
	/**
	 * 
	 * @return list of {@link ServerAccount}s
	 */
	public List<ServerAccount> getAll();

	/**
	 * All server account which have a trust relation
	 * 
	 * @param urlPage
	 * @return List of server account
	 */
	public List<ch.uzh.csg.mbps.model.ServerAccount> getServerAccounts(int urlPage);

	/**
	 * 
	 * @return long
	 */
	public long getAccountsCount();

	/**
	 * Checks all conditions that have to be set before
	 * delete an account.
	 * 
	 * @param url
	 * @return boolean
	 * @throws ServerAccountNotFoundException
	 * @throws BalanceNotZeroException
	 * @throws HibernateException
	 */
	public boolean checkPredefinedDeleteArguments(String url) throws ServerAccountNotFoundException, BalanceNotZeroException, HibernateException;

	/**
	 * 
	 * @param url
	 * @param oldLevel
	 * @param newLevel
	 * @throws ServerAccountNotFoundException
	 */
	public void updateTrustLevel(String url, int oldLevel, int newLevel) throws ServerAccountNotFoundException;

	/**
	 * 
	 * @param url
	 * @param oldLimit
	 * @param newLimit
	 */
	public void updateBalanceLimit(String url, BigDecimal oldLimit, BigDecimal newLimit);

	/**
	 * Stores own url, email and public key and creates a 
	 * Server Account model which will be send.

	 * 
	 * @param userAccount
	 * @param account
	 * @return ServerAccount
	 * @throws UserAccountNotFoundException
	 * @throws InvalidPublicKeyException
	 * @throws InvalidUrlException
	 * @throws InvalidEmailException
	 */
	public ServerAccount prepareAccount(UserAccount userAccount, ServerAccount account) throws UserAccountNotFoundException, InvalidPublicKeyException, InvalidUrlException, InvalidEmailException;

	/**
	 * Checks if Url is allready existing
	 * 
	 * @param url
	 * @return boolean
	 * @throws UrlAlreadyExistsException
	 */
	public boolean checkIfExistsByUrl(String url) throws UrlAlreadyExistsException;

	/**
	 * 
	 * @param url
	 * @return boolean
	 */
	boolean isDeletedByUrl(String url);

	/**
	 * 
	 * @param id
	 * @return boolean
	 */
	boolean isDeletedById(long id);
}