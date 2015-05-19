package ch.uzh.csg.coinblesk.server.clientinterface;

import java.math.BigDecimal;
import java.util.List;

import org.hibernate.HibernateException;

import ch.uzh.csg.coinblesk.customserialization.PKIAlgorithm;
import ch.uzh.csg.coinblesk.server.domain.DbTransaction;
import ch.uzh.csg.coinblesk.server.domain.ServerAccount;
import ch.uzh.csg.coinblesk.server.domain.ServerPublicKey;
import ch.uzh.csg.coinblesk.server.domain.UserAccount;
import ch.uzh.csg.coinblesk.server.util.exceptions.BalanceNotZeroException;
import ch.uzh.csg.coinblesk.server.util.exceptions.InvalidEmailException;
import ch.uzh.csg.coinblesk.server.util.exceptions.InvalidUrlException;
import ch.uzh.csg.coinblesk.server.util.exceptions.ServerAccountNotFoundException;
import ch.uzh.csg.coinblesk.server.util.exceptions.UrlAlreadyExistsException;
import ch.uzh.csg.coinblesk.server.util.exceptions.UserAccountNotFoundException;

public interface IServerAccount {
	
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
	 */
	public boolean persistAccount(ServerAccount serverAccount) throws UrlAlreadyExistsException, InvalidUrlException, InvalidEmailException;
	
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
	 * Deletes {@link ServerAccount} with url. ServerAccount is not deleted from DB,
	 * but account's flag "isDeleted" is set to true.
	 * 
	 * @param url
	 * @return boolean
	 * @throws UserAccountNotFoundException
	 * @throws BalanceNotZeroException
	 */
	public boolean deleteAccount(String url) throws ServerAccountNotFoundException, BalanceNotZeroException;
	
	/**
	 * Returns a list of all accounts with the specific trust level.
	 * 
	 * @param trustlevel of the accounts
	 * @return list of {@link ServerAccount}s
	 */
	public List<ServerAccount> getByTrustLevel(int trustlevel);
	
	/**
	 * 
	 * @return list of {@link ServerAccount}s
	 */
	public List<ServerAccount> getAll();

	/**
	 * Returns a list of all {@link ch.uzh.csg.coinblesk.model.ServerAccount} that have a relation.
	 * 
	 * @param urlPage
	 * @return List of server account
	 */
	public List<ch.uzh.csg.coinblesk.model.ServerAccount> getServerAccounts(int urlPage);

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
	 * Stores own url, email and public key and creates a 
	 * Server Account model which will be send.
	 * 
	 * @param userAccount
	 * @param account
	 * @return ServerAccount
	 * @throws UserAccountNotFoundException
	 * @throws InvalidUrlException
	 * @throws InvalidEmailException
	 */
	public ServerAccount prepareAccount(UserAccount userAccount, ServerAccount account) throws UserAccountNotFoundException, InvalidUrlException, InvalidEmailException;

	/**
	 * Checks if url is already existing
	 * 
	 * @param url
	 * @return boolean
	 * @throws UrlAlreadyExistsException
	 */
	public boolean checkIfExistsByUrl(String url);

	/**
	 * 
	 * @param url
	 * @return boolean
	 */
	public boolean isDeletedByUrl(String url);

	/**
	 * 
	 * @param id
	 * @return boolean
	 */
	public boolean isDeletedById(long id);

	/**
	 * Stores a public key on the database and maps this public key to a {@link ServerAccount}.
	 * 
	 * @param serverId 
	 * @param algorithm the {@link PKIAlgorithm} used to generate the key
	 * @param publicKey the base64 encoded public key
	 * @return byte Returns the key number, indicating the (incremented) position this public
	 *         key has in a list of public keys mapped to this server account
	 * @throws UserAccountNotFoundException
	 * @throws ServerAccountNotFoundException 
	 */
	public byte saveServerPublicKey(long serverId, PKIAlgorithm algorithm,String publicKey) throws UserAccountNotFoundException, ServerAccountNotFoundException;

	/**
	 * Undoes the deletion of the {@link ServerAccount} by a given parameter url.
	 * 
	 * @param url
	 * @throws ServerAccountNotFoundException
	 */
	public void undeleteServerAccountByUrl(String url) throws ServerAccountNotFoundException;
	
	/**
	 * Undoes the deletion of the {@link ServerAccount} by a given parameter id.
	 * 
	 * @param id
	 * @throws ServerAccountNotFoundException
	 */
	public void undeleteServerAccountById(Long id) throws ServerAccountNotFoundException;

	/**
	 * 
	 * @param url
	 * @param newLevel
	 * @throws ServerAccountNotFoundException
	 */
	public void updateTrustLevel(String url, int newLevel) throws ServerAccountNotFoundException;

	/**
	 * Updates the payout address.
	 * 
	 * @param url
	 * @param account
	 * @return
	 * @throws ServerAccountNotFoundException 
	 */
	public boolean updatePayoutAddressAccount(String url, ServerAccount account) throws ServerAccountNotFoundException;

	/**
	 * Verifies the trust relation and the balance limit
	 * 
	 * @param payerServerUrl
	 * @param payeeServerUrl
	 * @param amount 
	 * @return boolean
	 */
	public boolean verifyTrustAndBalance(ServerAccount account, BigDecimal amount);

	/**
	 * Matches the active balance to the current balance.
	 * 
	 * @param serverAccount 
	 * @param dbTransaction
	 * @param received
	 */
	public void persistsTransactionAmount(ServerAccount serverAccount, DbTransaction dbTransaction, boolean received);

	/**
	 * 
	 * @param serverId
	 * @return Server Public Keys
	 */
	public ServerPublicKey getServerAccountPublicKey(long serverId, byte keyNumber);

	/**
	 * 
	 * @param serverId
	 * @return list of server public keys
	 */
	public List<ServerPublicKey> getServerAccountPublicKeys(long serverId);
}