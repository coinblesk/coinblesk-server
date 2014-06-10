package ch.uzh.csg.mbps.server.clientinterface;

import java.util.List;

import ch.uzh.csg.mbps.server.domain.ServerAccount;
import ch.uzh.csg.mbps.server.util.exceptions.BalanceNotZeroException;
import ch.uzh.csg.mbps.server.util.exceptions.InvalidEmailException;
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
	 */
	public boolean createAccount(ServerAccount serverAccount) throws UrlAlreadyExistsException, BitcoinException, InvalidUrlException, InvalidEmailException;

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
	public boolean delete(String url) throws ServerAccountNotFoundException, BalanceNotZeroException;
	
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

}
