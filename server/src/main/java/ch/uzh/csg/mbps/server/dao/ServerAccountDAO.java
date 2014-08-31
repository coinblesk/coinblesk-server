package ch.uzh.csg.mbps.server.dao;

import java.math.BigDecimal;
import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Expression;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;

import org.apache.log4j.Logger;
import org.springframework.stereotype.Repository;

import ch.uzh.csg.mbps.server.domain.ServerAccount;
import ch.uzh.csg.mbps.server.util.Config;
import ch.uzh.csg.mbps.server.util.exceptions.BalanceNotZeroException;
import ch.uzh.csg.mbps.server.util.exceptions.ServerAccountNotFoundException;

/**
 * DatabaseAccessObject for {@link ServerAccount}s. Handles all DB operations
 * regarding {@link ServerAccount}s.
 * 
 */
@Repository
public class ServerAccountDAO {
	private static Logger LOGGER = Logger.getLogger(ServerAccountDAO.class);

	@PersistenceContext
	private EntityManager em;
	
	/**
	 * 
	 * @param serverAccount
	 */
	public void createAccount(ServerAccount serverAccount){
		em.persist(serverAccount);
		LOGGER.info("ServerAccount created: " + serverAccount.toString());
	}

	/**
	 * Set the server account as deleted. 
	 * 
	 * @pre {@link ServerAccount} has to be trust level No-Trust and the active balance has to be {@link BigDecimal} Zero.
	 * @param url of ther {@link ServerAccount}
	 * @throws ServerAccountNotFoundException
	 * @throws BalanceNotZeroException
	 */
	public void delete(String url) throws ServerAccountNotFoundException, BalanceNotZeroException {
		ServerAccount serverAccount = getByUrl(url);
		
		//TODO: mehmet: check: TrustLevel -> 
		// Hyprid: escrow account

		if(serverAccount.getActiveBalance().compareTo(BigDecimal.ZERO)==0 && serverAccount.getTrustLevel() == 0){
			serverAccount.setDeleted(true);
			em.merge(serverAccount);
		}
	}
	
	/**
	 * Persist updated {@link ServerAccount}
	 * 
	 * @pre Only Trust level, balance limit and url should be updated
	 * @param serverAccount
	 */
	public void updatedAccount(ServerAccount serverAccount){
		em.merge(serverAccount);
		LOGGER.info("Updated ServerAccount: " + serverAccount.toString());
	}
	
	/**
	 * Returns {@link ServerAccount}-Object for given parameter url. Does not
	 * return deleted ServerAccounts.
	 * 
	 * @param url
	 * @return ServerAccount
	 * @throws ServerAccountNotFoundException
	 */
	public ServerAccount getByUrl(String url) throws ServerAccountNotFoundException{
		
		CriteriaBuilder cb = em.getCriteriaBuilder();
		CriteriaQuery<ServerAccount> cq = cb.createQuery(ServerAccount.class);
		Root<ServerAccount> root = cq.from(ServerAccount.class);
		
		Predicate condition = cb.equal(root.get("url"), url);
		cq.where(condition);
		
		ServerAccount serverAccount = UserAccountDAO.getSingle(cq, em);
		
		if(serverAccount == null || serverAccount.isDeleted()) {
			throw new ServerAccountNotFoundException(url);
		}
		
		return serverAccount;
	}
	
	/**
	 * Returns {@link ServerAccount} (also deleted ones) ignoring cases. Only to
	 * use for checking if serverAccount already exists when creating new
	 * ServerAccount.
	 * 
	 * @param url
	 * @return ServerAccount
	 * @throws ServerAccountNotFoundException
	 */
	public ServerAccount getByUrlIgnoreCaseAndDeletedFlag(String url) throws ServerAccountNotFoundException{
		
		CriteriaBuilder cb = em.getCriteriaBuilder();
		CriteriaQuery<ServerAccount> cq = cb.createQuery(ServerAccount.class);
		Root<ServerAccount> root = cq.from(ServerAccount.class);
		
		Expression<String> e = root.get("url");
		Predicate condition = cb.equal(cb.upper(e), url.toUpperCase());
		cq.where(condition);
		
		ServerAccount serverAccount = UserAccountDAO.getSingle(cq, em);
		
		if(serverAccount == null) {
			throw new ServerAccountNotFoundException(url);
		}
		
		return serverAccount;
	}
	
	/**
	 * Returns {@link ServerAccount}-Object for given parameter id. Does not
	 * return deleted ServerAccounts.
	 * 
	 * @param id
	 * @return ServerAccount
	 * @throws ServerAccountNotFoundException
	 */
	public ServerAccount getById(long id) throws ServerAccountNotFoundException{
		
		
		CriteriaBuilder cb = em.getCriteriaBuilder();
		CriteriaQuery<ServerAccount> cq = cb.createQuery(ServerAccount.class);
		Root<ServerAccount> root = cq.from(ServerAccount.class);
		
		Predicate condition = cb.equal(root.get("id"), id);
		cq.where(condition);
		
		ServerAccount serverAccount = UserAccountDAO.getSingle(cq, em);
		
		if(serverAccount == null || serverAccount.isDeleted()) {
			throw new ServerAccountNotFoundException("id:" + id);
		}
		
		return serverAccount;
	}
	
	/**
	 * Returns {@link ServerAccount}-Object for given parameter email. Does not
	 * return deleted ServerAccounts.
	 * 
	 * @param email
	 * @return ServerAccount
	 * @throws ServerAccountNotFoundException
	 */
	public ServerAccount getByEmail(String email) throws ServerAccountNotFoundException{
		CriteriaBuilder cb = em.getCriteriaBuilder();
		CriteriaQuery<ServerAccount> cq = cb.createQuery(ServerAccount.class);
		Root<ServerAccount> root = cq.from(ServerAccount.class);
		
		Predicate condition = cb.equal(root.get("email"), email);
		cq.where(condition);
		
		ServerAccount serverAccount = UserAccountDAO.getSingle(cq, em);
		
		if(serverAccount == null || serverAccount.isDeleted()) {
			throw new ServerAccountNotFoundException("email:" + email);
		}
		
		return serverAccount;
	}
	
	/**
	 * 
	 * Returns {@link ServerAccount} (also deleted ones) ignoring cases. Only to
	 * use for checking if serverAccount already exists when creating new
	 * ServerAccount.
	 * 
	 * @param email
	 * @return ServerAccount
	 * @throws ServerAccountNotFoundException
	 */
	public ServerAccount getByEmailIgnoreCaseAndDeletedFlag(String email) throws ServerAccountNotFoundException{
		CriteriaBuilder cb = em.getCriteriaBuilder();
		CriteriaQuery<ServerAccount> cq = cb.createQuery(ServerAccount.class);
		Root<ServerAccount> root = cq.from(ServerAccount.class);
		
		Expression<String> e = root.get("email");
		Predicate condition = cb.equal(cb.upper(e), email.toUpperCase());
		cq.where(condition);
		
		ServerAccount serverAccount = UserAccountDAO.getSingle(cq, em);
		
		if(serverAccount == null) {
			throw new ServerAccountNotFoundException("email:" + email);
		}
		
		return serverAccount;
	}

	/**
	 * Returns {@link ServerAccount} with the assigned PayinAddress address.
	 * Does not return deleted ServerAccounts.
	 * @param address
	 * @return ServerAccount matching the given address
	 * @throws ServerAccountNotFoundException
	 */
	public ServerAccount getByPayinAddress(String payinAddress) throws ServerAccountNotFoundException{
		
		CriteriaBuilder cb = em.getCriteriaBuilder();
		CriteriaQuery<ServerAccount> cq = cb.createQuery(ServerAccount.class);
		Root<ServerAccount> root = cq.from(ServerAccount.class);
		
		Predicate condition = cb.equal(root.get("payinAddress"), payinAddress);
		cq.where(condition);
		
		ServerAccount serverAccount = UserAccountDAO.getSingle(cq, em);
		
		if(serverAccount == null || serverAccount.isDeleted()) {
			throw new ServerAccountNotFoundException("payinAddress:" + payinAddress);
		}
		return serverAccount;
	}
	
	/**
	 * Returns {@link ServerAccount} with the assigned PayOutAddress address.
	 * Does not return deleted ServerAccounts.
	 * @param address
	 * @return ServerAccount matching the given address
	 * @throws ServerAccountNotFoundException
	 */
	public ServerAccount getByPayOutAddress(String payoutAddress) throws ServerAccountNotFoundException{
		
		CriteriaBuilder cb = em.getCriteriaBuilder();
		CriteriaQuery<ServerAccount> cq = cb.createQuery(ServerAccount.class);
		Root<ServerAccount> root = cq.from(ServerAccount.class);
		
		Predicate condition = cb.equal(root.get("payoutAddress"), payoutAddress);
		cq.where(condition);
		
		ServerAccount serverAccount = UserAccountDAO.getSingle(cq, em);
		
		if(serverAccount == null || serverAccount.isDeleted()) {
			throw new ServerAccountNotFoundException("payoutAddress:" + payoutAddress);
		}
		return serverAccount;
	}	
	
	/**
	 * Returns {@link ServerAccount}-Objects in a list for given parameter trustlevel. Does not
	 * return deleted ServerAccounts.
	 * 
	 * @param trustlevel
	 * @return List of ServerAccount
	 */
	public List<ServerAccount> getByTrustLevel(int trustlevel){
		CriteriaBuilder cb = em.getCriteriaBuilder();
		CriteriaQuery<ServerAccount> cq = cb.createQuery(ServerAccount.class);
		Root<ServerAccount> root = cq.from(ServerAccount.class);
		
		Predicate condition = cb.equal(root.get("trustLevel"), trustlevel);
		cq.where(condition);
		
		return em.createQuery(cq).getResultList();
	}
	
	/**
	 * 
	 * @return List of ServerAccount
	 */
	public List<ServerAccount> getAllServerAccounts(){
		CriteriaBuilder cb = em.getCriteriaBuilder();
		CriteriaQuery<ServerAccount> cq = cb.createQuery(ServerAccount.class);
		cq.from(ServerAccount.class);
		return em.createQuery(cq).getResultList();
		
	}

	/**
	 * 
	 * @param cq
	 * @param eManager
	 * @return
	 */
	public static<K> K getSingle(CriteriaQuery<K> cq, EntityManager eManager) {
		List<K> list =  eManager.createQuery(cq).getResultList();
		if(list.size() == 0) {
			return null;
		}
		return list.get(0);
	}
	
	/**
	 * 
	 * @return Amount of ServerAccount
	 */
	public long getAccountCount(){
		long nofResults = ((Number) em.createQuery(
				"SELECT COUNT(*) "
				+ "FROM ServerAccount as account"
				+ " WHERE account.deleted = :deleted")
				.setParameter("deleted", true)
				.getSingleResult())
				.longValue();
		
		return nofResults;
	}
	
	/**
	 * 
	 * 
	 * @param page
	 * @return List of ServerAcoounts
	 */
	public List<ch.uzh.csg.mbps.model.ServerAccount> getServerAccounts(int page){
		if (page < 0) {
			return null;
		}
		
		@SuppressWarnings("unchecked")
        List<ch.uzh.csg.mbps.model.ServerAccount> resultWithAliasedBean = em.createQuery(""
        		+"SELECT NEW ch.uzh.csg.mbps.model.ServerAccount(account.id, account.url, account.payinAddress, account.payoutAddress,"
        		+"account trustLevel, accout.activeBalance, account.balanceLimit) "
        		+ "FROM ServerAccount account "
        		+ "ORDER BY account.url ASC")
        		.setFirstResult(page * Config.TRANSACTIONS_MAX_RESULTS)
        		.getResultList();
		
		return resultWithAliasedBean;
	}
	
	/**
	 * Checks if arguments to deleted a {@link ServerAccount} are fulfilled.
	 * 
	 * @param url
	 * @return bollean
	 * @throws ServerAccountNotFoundException
	 * @throws BalanceNotZeroException
	 */
	public boolean checkPredefinedDeleteArguments(String url) throws ServerAccountNotFoundException, BalanceNotZeroException {
		ServerAccount serverAccount = getByUrl(url);
		
		//TODO: mehmet check Trust Level
		// Hyprid: escrow account
		
		if(serverAccount.getActiveBalance().compareTo(BigDecimal.ZERO)==0 && serverAccount.getTrustLevel() == 0){
			return true;
		}
		return false;	
	}
	
	/**
	 * Checks if {@link ServerAccount} is deleted by a given parameter url. 
	 * 
	 * @param url
	 * @return boolean
	 * @throws ServerAccountNotFoundException
	 */
	public boolean isDeletedByUrl(String url) {
		
		CriteriaBuilder cb = em.getCriteriaBuilder();
		CriteriaQuery<ServerAccount> cq = cb.createQuery(ServerAccount.class);
		Root<ServerAccount> root = cq.from(ServerAccount.class);
		
		Predicate condition = cb.equal(root.get("url"), url);
		cq.where(condition);
		
		ServerAccount serverAccount = UserAccountDAO.getSingle(cq, em);
		
		if(serverAccount == null || !serverAccount.isDeleted()) {
			return false;
		}
		
		return true;
	}
	
	/**
	 * Checks if {@link ServerAccount} is deleted by a given parameter id.
	 * 
	 * @param id
	 * @return booelan
	 * @throws ServerAccountNotFoundException
	 */
	public boolean isDeletedById(long id) {
		
		
		CriteriaBuilder cb = em.getCriteriaBuilder();
		CriteriaQuery<ServerAccount> cq = cb.createQuery(ServerAccount.class);
		Root<ServerAccount> root = cq.from(ServerAccount.class);
		
		Predicate condition = cb.equal(root.get("id"), id);
		cq.where(condition);
		
		ServerAccount serverAccount = UserAccountDAO.getSingle(cq, em);
		
		if(serverAccount == null || !serverAccount.isDeleted()) {
			return false;
		}
		
		return true;
	}
	
}