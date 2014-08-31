package ch.uzh.csg.mbps.server.clientinterface;

import java.math.BigDecimal;
import java.util.List;

import org.hibernate.HibernateException;

import ch.uzh.csg.mbps.server.domain.ServerAccount;
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
	 * @param serverAccount
	 * @return
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
	 * @return
	 * @throws ServerAccountNotFoundException
	 */
	public ServerAccount getByUrl(String url) throws ServerAccountNotFoundException;

	/**
	 * 
	 * @param id
	 * @return
	 * @throws UserAccountNotFoundException
	 */
	public ServerAccount getById(long id) throws ServerAccountNotFoundException;

	/**
	 * 
	 * @param url
	 * @param updatedAccount
	 * @return
	 * @throws UserAccountNotFoundException
	 */
	public boolean updateAccount(String url, ServerAccount updatedAccount) throws ServerAccountNotFoundException;

	/**
	 * 
	 * @param url
	 * @return
	 * @throws UserAccountNotFoundException
	 * @throws BalanceNotZeroException
	 */
	public boolean deleteAccount(String url) throws ServerAccountNotFoundException, BalanceNotZeroException;
	
	/**
	 * 
	 * @return
	 */
	public List<ServerAccount> getByTrustLevel(int trustlevel);
	
	/**
	 * 
	 * @return
	 */
	public List<ServerAccount> getAll();

	/**
	 * All server account which have a trust relation
	 * 
	 * @param urlPage
	 * @return List of server account
	 */
	public List<ch.uzh.csg.mbps.model.ServerAccount> getServerAccounts(int urlPage);

	public long getAccountsCount();

	public boolean checkPredefinedDeleteArguments(String url) throws ServerAccountNotFoundException, BalanceNotZeroException, HibernateException;

	public void updateTrustLevel(String url, int oldLevel, int newLevel) throws ServerAccountNotFoundException;

	public void updateBalanceLimit(String url, BigDecimal oldLimit, BigDecimal newLimit);

	/**
	 * Collects the the own url, email and public key and creates a 
	 * Server Account which will be send.
	 * 
	 * @param account
	 * @return Server Account
	 * @throws InvalidEmailException 
	 * @throws InvalidUrlException 
	 * @throws InvalidPublicKeyException 
	 * @throws UserAccountNotFoundException 
	 */
	public ServerAccount prepareAccount(ServerAccount account) throws UserAccountNotFoundException, InvalidPublicKeyException, InvalidUrlException, InvalidEmailException;

	/**
	 * Checks if Url is allready existing
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
	 * @return booelan
	 */
	boolean isDeletedById(long id);
}