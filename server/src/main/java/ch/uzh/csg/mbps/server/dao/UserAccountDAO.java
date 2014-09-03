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

import ch.uzh.csg.mbps.server.domain.AdminRole;
import ch.uzh.csg.mbps.server.domain.EmailVerification;
import ch.uzh.csg.mbps.server.domain.ResetPassword;
import ch.uzh.csg.mbps.server.domain.UserAccount;
import ch.uzh.csg.mbps.server.util.UserRoles.Role;
import ch.uzh.csg.mbps.server.util.exceptions.BalanceNotZeroException;
import ch.uzh.csg.mbps.server.util.exceptions.UserAccountNotFoundException;
import ch.uzh.csg.mbps.server.util.exceptions.VerificationTokenNotFoundException;

/**
 * DatabaseAccessObject for the {@link UserAccount}. Handles all DB operations
 * regarding UserAccounts.
 */
@Repository
public class UserAccountDAO {
	private static Logger LOGGER = Logger.getLogger(UserAccountDAO.class);

	@PersistenceContext(unitName = "localdb")
	private EntityManager em;

	/**
	 * Saves a new {@link UserAccount} to the database.
	 * 
	 * @param userAccount
	 *            to be saved.
	 * @param token
	 *            VerificationToken to be saved which is used for
	 *            email-verification.
	 */
	public void createAccount(UserAccount userAccount, String token) {
		em.persist(userAccount);
		em.flush();
		em.refresh(userAccount);
		long id = userAccount.getId();
		EmailVerification ev = new EmailVerification(id, token);
		em.persist(ev);
		LOGGER.info("UserAccount created: " + id + " and created EmailVerification: " + ev.toString());
	}

	/**
	 * Returns {@link UserAccount}-Object for given parameter username. Does not
	 * return deleted UserAccounts.
	 * 
	 * @param username
	 *            identifier for UserAccount
	 * @return UserAccount
	 * @throws UserAccountNotFoundException
	 */
	public UserAccount getByUsername(String username) throws UserAccountNotFoundException{
		
		CriteriaBuilder cb = em.getCriteriaBuilder();
		CriteriaQuery<UserAccount> cq = cb.createQuery(UserAccount.class);
		Root<UserAccount> root = cq.from(UserAccount.class);
		Predicate condition = cb.equal(root.get("username"), username);
		cq.where(condition);
		
		UserAccount userAccount = getSingle(cq, em);
		
		//exception in normal program code is bad!
		if (userAccount == null || userAccount.isDeleted()) {
			throw new UserAccountNotFoundException(username);
		}
		
		return userAccount;
	}
	
	/**
	 * 
	 * @param cq
	 * @param em
	 * @return
	 */
	public static<K> K getSingle(CriteriaQuery<K> cq, EntityManager em) {
		List<K> list =  em.createQuery(cq).getResultList();
		if(list.size() == 0) {
			return null;
		}
		return list.get(0);
	}
	
	/**
	 * Returns {@link UserAccount} (also deleted ones) ignoring cases. Only to
	 * use for checking if userAccount already exists when creating new
	 * UserAccount.
	 * 
	 * @param username
	 *            identifier for UserAccount
	 * @return UserAccount matching the given username
	 * @throws UserAccountNotFoundException
	 */
	public UserAccount getByUsernameIgnoreCaseAndDeletedFlag(String username) throws UserAccountNotFoundException{
		
		CriteriaBuilder cb = em.getCriteriaBuilder();
		CriteriaQuery<UserAccount> cq = cb.createQuery(UserAccount.class);
		Root<UserAccount> root = cq.from(UserAccount.class);
		Expression<String> e = root.get("username");
		Predicate condition = cb.equal(cb.upper(e), username.toUpperCase());
		cq.where(condition);
				
		UserAccount userAccount = getSingle(cq, em);
		
		//exception in normal program code is bad!
		if (userAccount == null) {
			throw new UserAccountNotFoundException(username);
		}
		
		return userAccount;
	}

	/**
	 * Returns {@link UserAccount}-Object for given parameter id.
	 * 
	 * @param id
	 *            for identifying UserAccount
	 * @return UserAccount matching the given id
	 * @throws UserAccountNotFoundException
	 */
	public UserAccount getById(long id) throws UserAccountNotFoundException {
		
		CriteriaBuilder cb = em.getCriteriaBuilder();
		CriteriaQuery<UserAccount> cq = cb.createQuery(UserAccount.class);
		Root<UserAccount> root = cq.from(UserAccount.class);
		Predicate condition = cb.equal(root.get("id"), id);
		cq.where(condition);
		
		UserAccount userAccount = getSingle(cq, em);
		
		if (userAccount == null || userAccount.isDeleted()) {
			throw new UserAccountNotFoundException("id: "+id);
		}
		
		return userAccount;
	}

	/**
	 * Deletes {@link UserAccount} by setting isDeleted-Flag for UserAccount
	 * with given username to true. Object is not deleted in database.
	 * UserAccount can only be deleted if balance is zero.
	 * 
	 * @param username
	 *            which shall be deleted
	 * @throws UserAccountNotFoundException
	 * @throws BalanceNotZeroException
	 *             if balance of UserAccount is unequal zero.
	 */
	public void delete(String username) throws UserAccountNotFoundException, BalanceNotZeroException {
		UserAccount userAccount = getByUsername(username);
		
		if (userAccount.getBalance().compareTo(BigDecimal.ZERO)==0) {
			userAccount.setDeleted(true);
			em.merge(userAccount);	
			LOGGER.info("Delted UserAccount: " + userAccount.toString());
		} else {
			throw new BalanceNotZeroException();
		}
	}

	/**
	 * Updates a {@link UserAccount} in the database.
	 * 
	 * @param userAccount
	 *            (updated UserAccount)
	 * @throws UserAccountNotFoundException
	 */
	public void updateAccount(UserAccount userAccount) throws UserAccountNotFoundException {
		em.merge(userAccount);
		LOGGER.info("Updated UserAccount: " + userAccount.toString());
	}
	
	/**
	 * Verifies the {@link UserAccount} matching the verificationToken.
	 * 
	 * @param verificationToken
	 *            used for determine UserAccount to be verified
	 * @throws UserAccountNotFoundException
	 * @throws VerificationTokenNotFoundException
	 *             if token is not found in the DB
	 */
	public void verifyEmail(String verificationToken) throws UserAccountNotFoundException, VerificationTokenNotFoundException {
		
		CriteriaBuilder cb = em.getCriteriaBuilder();
		CriteriaQuery<EmailVerification> cq = cb.createQuery(EmailVerification.class);
		Root<EmailVerification> root = cq.from(EmailVerification.class);
		Predicate condition = cb.equal(root.get("verificationToken"), verificationToken);
		cq.where(condition);
		
		EmailVerification ev = getSingle(cq, em);
		
		if (ev == null) {
			throw new VerificationTokenNotFoundException(verificationToken);
		}
		
		UserAccount userAccount = getById(ev.getUserID());
		userAccount.setEmailVerified(true);
		em.merge(userAccount);
		em.remove(ev);
		LOGGER.info("Verified Emailaddress for UserAccount with ID: " + userAccount.getId() );
	}

	/**
	 * Returns {@link UserAccount} with the assigned BitcoinAddress address.
	 * Does not return deleted UserAccounts.
	 * 
	 * @param address
	 *            BitcoinAddress of UserAccount.
	 * @return UserAccount matching the given address.
	 * @throws UserAccountNotFoundException
	 */
	public UserAccount getByBTCAddress(String paymentAddress) throws UserAccountNotFoundException {
		
		CriteriaBuilder cb = em.getCriteriaBuilder();
		CriteriaQuery<UserAccount> cq = cb.createQuery(UserAccount.class);
		Root<UserAccount> root = cq.from(UserAccount.class);
		Predicate condition = cb.equal(root.get("paymentAddress"), paymentAddress);
		cq.where(condition);
		UserAccount userAccount = getSingle(cq, em);
		
		if (userAccount == null || userAccount.isDeleted())
			throw new UserAccountNotFoundException("BTC Address: "+paymentAddress);
		
		return userAccount;
	}

	/**
	 * Returns {@link UserAccount} with the assigned emailAddress. Does not
	 * return deleted UserAccounts.
	 * 
	 * @param emailAddress
	 *            of UserAccount
	 * @return UserAccount matching the given address.
	 * @throws UserAccountNotFoundException
	 */
	public UserAccount getByEmail(String email) throws UserAccountNotFoundException {
		
		CriteriaBuilder cb = em.getCriteriaBuilder();
		CriteriaQuery<UserAccount> cq = cb.createQuery(UserAccount.class);
		Root<UserAccount> root = cq.from(UserAccount.class);
		Predicate condition = cb.equal(root.get("email"), email);
		cq.where(condition);
		UserAccount userAccount = getSingle(cq, em);
		
		if (userAccount == null || userAccount.isDeleted())
			throw new UserAccountNotFoundException("email: "+ email);
		
		return userAccount;
	}

	/**
	 * Saves a token for resetting password of a {@link UserAccount}.
	 * 
	 * @param user
	 * @param token
	 */
	public void createPasswordResetToken(UserAccount user, String token){
		ResetPassword rp = new ResetPassword(user.getId(), token);
		em.persist(rp);
		LOGGER.info("Created ResetPasswordToken for UserAccount with ID: " + user.getId());
	}

	/**
	 * Returns {@link ResetPassword} for given resetPasswordToken.
	 * 
	 * @param resetPasswordToken
	 * @return ResetPassword matching between UserAccount and resetPasswordToken
	 * @throws VerificationTokenNotFoundException
	 */
	public ResetPassword getResetPassword(String token) throws VerificationTokenNotFoundException{
		
		CriteriaBuilder cb = em.getCriteriaBuilder();
		CriteriaQuery<ResetPassword> cq = cb.createQuery(ResetPassword.class);
		Root<ResetPassword> root = cq.from(ResetPassword.class);
		Predicate condition = cb.equal(root.get("token"), token);
		cq.where(condition);

		ResetPassword resetPassword = getSingle(cq, em);
		
		if (resetPassword == null) {
			throw new VerificationTokenNotFoundException(token);			
		}
		
		return resetPassword;
	}
	
	/**
	 * Returns verification token for given user-id as a string.
	 * 
	 * @param id
	 *            userAccount id
	 * @return verificationToken for userAccount with matching id.
	 * @throws VerificationTokenNotFoundException
	 */
	public String getVerificationTokenByUserId(long userID) throws VerificationTokenNotFoundException {
		
		CriteriaBuilder cb = em.getCriteriaBuilder();
		CriteriaQuery<EmailVerification> cq = cb.createQuery(EmailVerification.class);
		Root<EmailVerification> root = cq.from(EmailVerification.class);
		Predicate condition = cb.equal(root.get("userID"), userID);
		cq.where(condition);
		
		EmailVerification ev = getSingle(cq, em);
		
		if (ev == null) {
			throw new VerificationTokenNotFoundException("userID:"+userID);
		}
		
		return ev.getVerificationToken();
	}
	
	/**
	 * Saves new {@link EmailVerification} in the database. EmailVerification
	 * contains matching between UserAccount and VerificationToken.
	 * 
	 * @param userId
	 *            for which verificationToken shall be saved
	 * @param token
	 */
	public void createEmailVerificationToken(long userId, String token) {
		EmailVerification ev = new EmailVerification(userId, token);
		em.persist(ev);
		LOGGER.info("Created VerifyEmailToken for UserAccount with ID: " + userId);
	}

	/**
	 * Returns {@link UserAccount} assigned to given ResetPasswordToken.
	 * 
	 * @param token
	 * @return UserAccount for given token
	 * @throws VerificationTokenNotFoundException
	 * @throws UserAccountNotFoundException
	 */
	public UserAccount getByResetPasswordToken(String token) throws VerificationTokenNotFoundException, UserAccountNotFoundException {
		
		CriteriaBuilder cb = em.getCriteriaBuilder();
		CriteriaQuery<ResetPassword> cq = cb.createQuery(ResetPassword.class);
		Root<ResetPassword> root = cq.from(ResetPassword.class);
		Predicate condition = cb.equal(root.get("token"), token);
		cq.where(condition);
		
		ResetPassword resetPassword = getSingle(cq, em);
		
		if (resetPassword == null){
			throw new VerificationTokenNotFoundException(token);			
		}
		return getById(resetPassword.getUserID());
	}

	/**
	 * Deletes {@link ResetPassword} entry for given resetPasswordToken from DB.
	 * 
	 * @param resetPasswordToken
	 *            of ResetPassword to be deleted
	 * @throws VerificationTokenNotFoundException
	 */
	public void deleteResetPassword(String resetPasswordToken) throws VerificationTokenNotFoundException {	
		ResetPassword resetPassword = getResetPassword(resetPasswordToken);
		em.remove(resetPassword);
		LOGGER.info("Deleted ResetPasswordToken: " + resetPasswordToken);
	}

	/**
	 * Saves a token for resetting password of a {@link UserAccount}.
	 * 
	 * @param user
	 * @param token
	 */
	public void createAdminToken(UserAccount user, String token){
		AdminRole ar = new AdminRole(user.getId(), token);
		em.persist(ar);
		LOGGER.info("Created AdminRoleToken for UserAccount with ID: " + user.getId());
	}

	/**
	 * 
	 * 
	 * @param adminToken
	 * @return
	 * @throws VerificationTokenNotFoundException 
	 */
	public AdminRole getCreateAdmin(String token) throws VerificationTokenNotFoundException {
		
		CriteriaBuilder cb = em.getCriteriaBuilder();
		CriteriaQuery<AdminRole> cq = cb.createQuery(AdminRole.class);
		Root<AdminRole> root = cq.from(AdminRole.class);
		Predicate condition = cb.equal(root.get("token"), token);
		cq.where(condition);
		
		AdminRole adminRole = getSingle(cq, em);
		
		if (adminRole == null) {
			throw new VerificationTokenNotFoundException(token);			
		}
		
		return adminRole;
	}
	
	/**
	 * Returns a list with all {@link ResetPassword} objects saved in the
	 * database.
	 * 
	 * @return List<ResetPassword>
	 */
	public List<ResetPassword> getAllResetPassword() {
		CriteriaBuilder cb = em.getCriteriaBuilder();
		CriteriaQuery<ResetPassword> cq = cb.createQuery(ResetPassword.class);
		cq.from(ResetPassword.class);
		return em.createQuery(cq).getResultList();
	}

	public UserAccount getByEmailIgnoreCaseAndDeletedFlag(String email) throws UserAccountNotFoundException {
		CriteriaBuilder cb = em.getCriteriaBuilder();
		CriteriaQuery<UserAccount> cq = cb.createQuery(UserAccount.class);
		Root<UserAccount> root = cq.from(UserAccount.class);
		Expression<String> e = root.get("email");
		Predicate condition = cb.equal(cb.upper(e), email.toUpperCase());
		cq.where(condition);
		UserAccount userAccount = getSingle(cq, em);
		
		if (userAccount == null) {
			throw new UserAccountNotFoundException(email);
		}
		
		return userAccount;
	}
	

	/**
	 * Returns a list with all {@link UserAccount} objects saved in the
	 * database.
	 * 
	 * @return List<UserAccount>
	 */
	public List<UserAccount> getAllUserAccounts() {
		CriteriaBuilder cb = em.getCriteriaBuilder();
		CriteriaQuery<UserAccount> cq = cb.createQuery(UserAccount.class);
		cq.from(UserAccount.class);
		return em.createQuery(cq).getResultList();
	}
	
	/**
	 * Returns all users by a given parameter role
	 * 
	 * @param role
	 * @return List of UserAccounts
	 */
	public List<UserAccount> getAllUsersByRoles(Role role){
		CriteriaBuilder cb = em.getCriteriaBuilder();
		CriteriaQuery<UserAccount> cq = cb.createQuery(UserAccount.class);
		Root<UserAccount> root = cq.from(UserAccount.class);
		Predicate condition = cb.and(cb.equal(root.get("roles"), role.getCode()), cb.equal(root.get("deleted"), false));
		cq.where(condition);
		return em.createQuery(cq).getResultList();
	}
	
	/**
	 * Returns the sum of all account balances.
	 * 
	 * @return sumOfBalances
	 */
	public BigDecimal getSumOfAccountBalance() {
		BigDecimal amount = new BigDecimal(em.createQuery("SELECT SUM(u.balance) FROM UserAccount u").getSingleResult().toString());
		return amount;
	}

	/**
	 * Returns all email address of all users by the given parameter role.
	 * 
	 * @param role
	 * @return List of String
	 */
	public List<String> getEmailOfAllUsersByRoles(Role role) {
		CriteriaBuilder cb = em.getCriteriaBuilder();
		CriteriaQuery<String> cq = cb.createQuery(String.class);
		Root<UserAccount> root = cq.from(UserAccount.class);
		cq.select(cb.construct(String.class, root.get("email")));
		
		Predicate condition1 = cb.equal(root.get("roles"), role.getCode());
		Predicate condition2 = cb.equal(root.get("deleted"), false);
		Predicate condition3 = cb.and(condition1, condition2);
		cq.where(condition3);
		List<String> resultWithAliasedBean = em.createQuery(cq).getResultList();

		return resultWithAliasedBean;
	}
}
