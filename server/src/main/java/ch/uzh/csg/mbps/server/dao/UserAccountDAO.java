package ch.uzh.csg.mbps.server.dao;

import java.math.BigDecimal;
import java.util.List;

import org.apache.log4j.Logger;
import org.hibernate.HibernateException;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.criterion.Restrictions;

import ch.uzh.csg.mbps.server.domain.EmailVerification;
import ch.uzh.csg.mbps.server.domain.ResetPassword;
import ch.uzh.csg.mbps.server.domain.UserAccount;
import ch.uzh.csg.mbps.server.util.HibernateUtil;
import ch.uzh.csg.mbps.server.util.exceptions.BalanceNotZeroException;
import ch.uzh.csg.mbps.server.util.exceptions.UserAccountNotFoundException;
import ch.uzh.csg.mbps.server.util.exceptions.VerificationTokenNotFoundException;

/**
 * DatabaseAccessObject for the {@link UserAccount}. Handles all DB operations
 * regarding UserAccounts.
 */
public class UserAccountDAO {
	private static Logger LOGGER = Logger.getLogger(UserAccountDAO.class);

	private UserAccountDAO() {
	}

	/**
	 * Saves a new {@link UserAccount} to the database.
	 * 
	 * @param userAccount
	 *            to be saved.
	 * @param token
	 *            VerificationToken to be saved which is used for
	 *            email-verification.
	 * @throws HibernateException
	 */
	public static void createAccount(UserAccount userAccount, String token) throws HibernateException {
		Session session = null;
		org.hibernate.Transaction transaction = null;
		UserAccount fromDb;
		EmailVerification ev;
		try {
			session = openSession();
			transaction = session.beginTransaction();
			session.save(userAccount);
			fromDb = (UserAccount) session.createCriteria(UserAccount.class).add(Restrictions.eq("username", userAccount.getUsername())).uniqueResult();
			ev = new EmailVerification(fromDb.getId(), token);
			session.save(ev);
			transaction.commit();
			LOGGER.info("UserAccount created: " + fromDb.toString() + " and created EmailVerification: " + ev.toString());
		} catch (HibernateException e) {
			LOGGER.error("Problem creating UserAccount: " + userAccount.toString());
			 if (transaction != null)
				 transaction.rollback();
			 throw e;
		} finally {
			session.close();
		}
	}

	private static Session openSession() {
		SessionFactory sessionFactory = HibernateUtil.getSessionFactory();
		Session session = sessionFactory.openSession();
		return session;
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
	public static UserAccount getByUsername(String username) throws UserAccountNotFoundException{
		Session session = openSession();
		session.beginTransaction();
		UserAccount userAccount = (UserAccount) session.createCriteria(UserAccount.class).add(Restrictions.eq("username", username)).uniqueResult();
		session.close();
		if (userAccount == null || userAccount.isDeleted())
			throw new UserAccountNotFoundException(username);
		
		return userAccount;
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
	public static UserAccount getByUsernameIgnoreCaseAndDeletedFlag(String username) throws UserAccountNotFoundException{
		Session session = openSession();
		session.beginTransaction();
		UserAccount userAccount = (UserAccount) session.createCriteria(UserAccount.class).add(Restrictions.eq("username", username).ignoreCase()).uniqueResult();
		session.close();
		if (userAccount == null)
			throw new UserAccountNotFoundException(username);
		
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
	public static UserAccount getById(long id) throws UserAccountNotFoundException {
		Session session = openSession();
		session.beginTransaction();
		UserAccount userAccount = (UserAccount) session.get(UserAccount.class, new Long(id));
		session.close();
		if (userAccount == null || userAccount.isDeleted())
			throw new UserAccountNotFoundException("id: "+id);
		
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
	 * @throws HibernateException
	 */
	public static void delete(String username) throws UserAccountNotFoundException, BalanceNotZeroException, HibernateException {
		UserAccount userAccount = getByUsername(username);
		
		Session session = openSession();
		org.hibernate.Transaction transaction = null;
		
		if (userAccount.getBalance().compareTo(BigDecimal.ZERO)==0) {
			try {
				userAccount.setDeleted(true);
				
				transaction = session.beginTransaction();
				session.update(userAccount);
				transaction.commit();
				LOGGER.info("Delted UserAccount: " + userAccount.toString());
			} catch (HibernateException e) {
				LOGGER.error("Problem deleting UserAccount: " + userAccount.toString() + " ErrorMessage: " + e.getMessage());
				 if (transaction != null)
					 transaction.rollback();
				 throw e;
			} finally {
				session.close();
			}
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
	 * @throws HibernateException
	 */
	public static void updateAccount(UserAccount userAccount) throws UserAccountNotFoundException, HibernateException {
		Session session = openSession();
		org.hibernate.Transaction transaction = null;
		
		try {
			transaction = session.beginTransaction();
			session.update(userAccount);
			transaction.commit();
			LOGGER.info("Updated UserAccount: " + userAccount.toString());
		} catch (HibernateException e) {
			LOGGER.error("Problem updating UserAccount: " + userAccount.toString() + " ErrorMessage: " + e.getMessage());
			 if (transaction != null)
				 transaction.rollback();
			 throw e;
		} finally {
			session.close();
		}
	}
	
	/**
	 * Verifies the {@link UserAccount} matching the verificationToken.
	 * 
	 * @param verificationToken
	 *            used for determine UserAccount to be verified
	 * @throws UserAccountNotFoundException
	 * @throws HibernateException
	 * @throws VerificationTokenNotFoundException
	 *             if token is not found in the DB
	 */
	public static void verifyEmail(String verificationToken) throws UserAccountNotFoundException, HibernateException, VerificationTokenNotFoundException {
		Session session = openSession();
		org.hibernate.Transaction tx = session.beginTransaction();
		
		EmailVerification ev = (EmailVerification) session.createCriteria(EmailVerification.class).add(Restrictions.eq("verificationToken", verificationToken)).uniqueResult();
		if (ev == null)
			throw new VerificationTokenNotFoundException(verificationToken);
		
		UserAccount userAccount = getById(ev.getUserID());
		
		try {
			userAccount.setEmailVerified(true);
			session.update(userAccount);
			session.delete(ev);
			tx.commit();
			LOGGER.info("Verified Emailaddress for UserAccount with ID: " + userAccount.getId() );
		} catch (HibernateException e) {
			LOGGER.error("Problem verifying UserAccount with ID: " + userAccount.getId() + "ErrorMessage: " + e.getMessage());
			if (tx != null)
				 tx.rollback();
			 throw e;
		} finally {
			session.close();
		}
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
	public static UserAccount getByBTCAddress(String address) throws UserAccountNotFoundException {
		Session session = openSession();
		session.beginTransaction();
		UserAccount userAccount = (UserAccount) session.createCriteria(UserAccount.class).add(Restrictions.eq("paymentAddress", address)).uniqueResult();
		
		session.close();
		if (userAccount == null || userAccount.isDeleted())
			throw new UserAccountNotFoundException("BTC Address: "+address);
		
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
	public static UserAccount getByEmail(String emailAddress) throws UserAccountNotFoundException {
		Session session = openSession();
		session.beginTransaction();
		UserAccount userAccount = (UserAccount) session.createCriteria(UserAccount.class).add(Restrictions.eq("email", emailAddress)).uniqueResult();
		session.close();
		if (userAccount == null || userAccount.isDeleted())
			throw new UserAccountNotFoundException("email: "+ emailAddress);
		
		return userAccount;
	}

	/**
	 * Saves a token for resetting password of a {@link UserAccount}.
	 * 
	 * @param user
	 * @param token
	 */
	public static void createPasswordResetToken(UserAccount user, String token){
		Session session = null;
		org.hibernate.Transaction transaction = null;
		
		try {
			session = openSession();
			transaction = session.beginTransaction();
			
			ResetPassword rp = new ResetPassword(user.getId(), token);
			session.save(rp);
			
			transaction.commit();
			LOGGER.info("Created ResetPasswordToken for UserAccount with ID: " + user.getId());
		} catch (HibernateException e) {
			LOGGER.error("Problem creating ResetPasswordToken for UserAccount with ID: " + user.getId() + "ErrorMessage: " + e.getMessage());
			 if (transaction != null)
				 transaction.rollback();
			 throw e;
		} finally {
			session.close();
		}
	}

	/**
	 * Returns {@link ResetPassword} for given resetPasswordToken.
	 * 
	 * @param resetPasswordToken
	 * @return ResetPassword matching between UserAccount and resetPasswordToken
	 * @throws VerificationTokenNotFoundException
	 */
	public static ResetPassword getResetPassword(String resetPasswordToken) throws VerificationTokenNotFoundException{
		Session session = openSession();
		session.beginTransaction();
		ResetPassword resetPassword = (ResetPassword) session.createCriteria(ResetPassword.class).add(Restrictions.eq("token", resetPasswordToken)).uniqueResult();
		
		session.close();
		if (resetPassword == null){
			throw new VerificationTokenNotFoundException(resetPasswordToken);			
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
	public static String getVerificationTokenByUserId(long id) throws VerificationTokenNotFoundException {
		Session session = openSession();
		session.beginTransaction();
		EmailVerification ev = (EmailVerification) session.createCriteria(EmailVerification.class).add(Restrictions.eq("userID", id)).uniqueResult();
		session.close();
		
		if (ev == null)
			throw new VerificationTokenNotFoundException("");			
		
		return ev.getVerificationToken();
	}
	
	/**
	 * Saves new {@link EmailVerification} in the database. EmailVerification
	 * contains matching between UserAccount and VerificationToken.
	 * 
	 * @param userId
	 *            for which verificationToken shall be saved
	 * @param token
	 * @throws HibernateException
	 */
	public static void createEmailVerificationToken(long userId, String token) throws HibernateException {
		Session session = null;
		org.hibernate.Transaction transaction = null;
		
		try {
			session = openSession();
			transaction = session.beginTransaction();
			
			EmailVerification ev = new EmailVerification(userId, token);
			session.save(ev);
			
			transaction.commit();
			LOGGER.info("Created VerifyEmailToken for UserAccount with ID: " + userId);
		} catch (HibernateException e) {
			LOGGER.error("Problem creating VerifyEmailToken for UserAccount with ID: " + userId + "ErrorMessage: " + e.getMessage());
			 if (transaction != null)
				 transaction.rollback();
			 throw e;
		} finally {
			session.close();
		}
	}

	/**
	 * Returns {@link UserAccount} assigned to given ResetPasswordToken.
	 * 
	 * @param token
	 * @return UserAccount for given token
	 * @throws VerificationTokenNotFoundException
	 * @throws UserAccountNotFoundException
	 */
	public static UserAccount getByResetPasswordToken(String token) throws VerificationTokenNotFoundException, UserAccountNotFoundException {
		Session session = openSession();
		session.beginTransaction();
		ResetPassword resetPassword = (ResetPassword) session.createCriteria(ResetPassword.class).add(Restrictions.eq("token", token)).uniqueResult();
		session.close();
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
	public static void deleteResetPassword(String resetPasswordToken) throws VerificationTokenNotFoundException {	
		Session session = openSession();
		org.hibernate.Transaction tx = session.beginTransaction();
		
		ResetPassword resetPassword = getResetPassword(resetPasswordToken);
		
		try {
			session.delete(resetPassword);
			tx.commit();
			LOGGER.info("Deleted ResetPasswordToken: " + resetPasswordToken);
		} catch (HibernateException e) {
			LOGGER.error("Problem deleting ResetPasswordToken with Token: " + resetPasswordToken + "ErrorMessage: " + e.getMessage());
			if (tx != null)
				 tx.rollback();
			 throw e;
		} finally {
			session.close();
		}
	}

	/**
	 * Returns a list with all {@link ResetPassword} objects saved in the
	 * database.
	 * 
	 * @return List<ResetPassword>
	 */
	public static List<ResetPassword> getAllResetPassword() {
		Session session = openSession();
		session.beginTransaction();
		@SuppressWarnings("unchecked")
		List<ResetPassword> list = (List<ResetPassword>) session.createCriteria(ResetPassword.class).list();
		session.close();
		
		return list;
	}

	public static UserAccount getByEmailIgnoreCaseAndDeletedFlag(String email) throws UserAccountNotFoundException {
		Session session = openSession();
		session.beginTransaction();
		UserAccount userAccount = (UserAccount) session.createCriteria(UserAccount.class).add(Restrictions.eq("email", email).ignoreCase()).uniqueResult();
		session.close();
		if (userAccount == null)
			throw new UserAccountNotFoundException(email);
		
		return userAccount;
	}

}
