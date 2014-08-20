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
import ch.uzh.csg.mbps.server.domain.UserAccount;
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
	
	//TODO: mehmet: javadoc
	
	@PersistenceContext
	private EntityManager em;
	
	/**
	 * 
	 * @param serverAccount
	 * @param token
	 */
	public void createAccount(ServerAccount serverAccount, String token){
		em.persist(serverAccount);
		LOGGER.info("ServerAccount created: " + serverAccount.toString());
	}

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
	 * 
	 * @param serverAccount
	 */
	public void updatedAccount(ServerAccount serverAccount){
		em.merge(serverAccount);
		LOGGER.info("Updated ServerAccount: " + serverAccount.toString());
	}
	
	/**
	 * 
	 * @param url
	 * @return
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
	 * 
	 * @param id
	 * @return
	 * @throws ServerAccountNotFoundException
	 */
	public ServerAccount getById(Long id) throws ServerAccountNotFoundException{
		
		
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
	 * 
	 * @param email
	 * @return
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
	 * @param email
	 * @return
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
	 * 
	 * @param address
	 * @return
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
	 * 
	 * @param trustlevel
	 * @return
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
	 * @return
	 */
	public List<ServerAccount> getAllServerAccounts(){
		CriteriaBuilder cb = em.getCriteriaBuilder();
		CriteriaQuery<ServerAccount> cq = cb.createQuery(ServerAccount.class);
		cq.from(ServerAccount.class);
		return em.createQuery(cq).getResultList();
		
	}
	
}
