package ch.uzh.csg.mbps.server.controller;

import java.io.IOException;

import net.minidev.json.parser.ParseException;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.ModelAndView;

import ch.uzh.csg.mbps.customserialization.PKIAlgorithm;
import ch.uzh.csg.mbps.customserialization.exceptions.UnknownPKIAlgorithmException;
import ch.uzh.csg.mbps.keys.CustomPublicKey;
import ch.uzh.csg.mbps.responseobject.CustomResponseObject;
import ch.uzh.csg.mbps.responseobject.CustomResponseObject.Type;
import ch.uzh.csg.mbps.responseobject.GetHistoryTransferObject;
import ch.uzh.csg.mbps.responseobject.ReadAccountTransferObject;
import ch.uzh.csg.mbps.server.domain.UserAccount;
import ch.uzh.csg.mbps.server.service.PayInTransactionService;
import ch.uzh.csg.mbps.server.service.PayOutTransactionService;
import ch.uzh.csg.mbps.server.service.TransactionService;
import ch.uzh.csg.mbps.server.service.UserAccountService;
import ch.uzh.csg.mbps.server.util.AuthenticationInfo;
import ch.uzh.csg.mbps.server.util.Constants;
import ch.uzh.csg.mbps.server.util.ExchangeRates;
import ch.uzh.csg.mbps.server.util.PasswordMatcher;
import ch.uzh.csg.mbps.server.util.exceptions.BalanceNotZeroException;
import ch.uzh.csg.mbps.server.util.exceptions.EmailAlreadyExistsException;
import ch.uzh.csg.mbps.server.util.exceptions.InvalidEmailException;
import ch.uzh.csg.mbps.server.util.exceptions.InvalidUsernameException;
import ch.uzh.csg.mbps.server.util.exceptions.UserAccountNotFoundException;
import ch.uzh.csg.mbps.server.util.exceptions.UsernameAlreadyExistsException;

import com.azazar.bitcoin.jsonrpcclient.BitcoinException;

/**
 * REST Controller for client http requests regarding UserAccount operations.
 * 
 */
@Controller
@RequestMapping("/user")
public class UserAccountController {
	//TODO: mehmet move to Constans class
	private static final String USERNAME_ALREADY_EXISTS = "Username already exists.";
	private static final String CREATION_ERROR = "Could not create account - internal error.";
	private static final String CREATION_SUCCESS = "Your Account has successfully been created. Please check your emails to verify your account.";
	private static final String ACCOUNT_NOT_FOUND = "UserAccount not found.";
	private static final String UPDATE_SUCCESS = "Your Account has successfully been updated.";
	private static final String UPDATE_ERROR = "Could not update your Account due to an internal error.";
	private static final String TRY_AGAIN = "Please try again.";
	private static final String BALANCE_NOT_ZERO = "Balance not zero. Please pay out your Bitcoins before deleting your account.";
	private static final String DELETE_SUCCESS = "Your account has been deleted.";
	private static final String BITCOIN_ADDRESS_ERROR = "Couldn't create a bitcoin payment address.";
	private static final String INVALID_USERNAME = "Invalid Username. Username must be at minimum 4 signs and is only allowed to have digits and letters.";
	private static final String PASSWORD_RESET_LINK_SENT = 	"A link to reset your password has been sent to your email address.";
	private static final String INVALID_EMAIL = 	"Invalid emailaddress. Please enter a proper emailaddress.";
	private static final String EMAIL_ALREADY_EXISTS = 	"An account with this email address already exists.";

	/**
	 * Creates a new UserAccount and saves it to DB.
	 * 
	 * @param userAccount
	 * @return CustomResponseObject with information about successful/non
	 *         successful transaction.
	 */
	@RequestMapping(value = "/create", method = RequestMethod.POST, consumes = "application/json")
	@ResponseBody
	public CustomResponseObject createAccount(@RequestBody UserAccount userAccount) {
		try {
			boolean success = UserAccountService.getInstance().createAccount(userAccount);
			if (success)
				return new CustomResponseObject(true, CREATION_SUCCESS);
			else
				return new CustomResponseObject(false, CREATION_ERROR);
		} catch (UsernameAlreadyExistsException e) {
			return new CustomResponseObject(false, USERNAME_ALREADY_EXISTS);
		} catch (BitcoinException e) {
			return new CustomResponseObject(false, BITCOIN_ADDRESS_ERROR);
		} catch (InvalidUsernameException e) {
			return new CustomResponseObject(false, INVALID_USERNAME);
		} catch (InvalidEmailException e) {
			return new CustomResponseObject(false, INVALID_EMAIL);
		} catch (EmailAlreadyExistsException e) {
			return new CustomResponseObject(false, EMAIL_ALREADY_EXISTS);
		}
	}

	/**
	 * Request executed after successful Login of Client. Returns {@link
	 * ch.uzh.csg.mbps.model.UserAccount} and ServerPublicKey
	 * 
	 * @return CustomResponseObject with UserAccount object and ServerPublicKey
	 */
	@RequestMapping(value = "/afterLogin", method = RequestMethod.GET, produces = "application/json")
	@ResponseBody
	public CustomResponseObject getUserAccount() {
		try {
			UserAccount userAccount = UserAccountService.getInstance().getByUsername(AuthenticationInfo.getPrincipalUsername());
			CustomResponseObject responseObject = new CustomResponseObject(true, null, Type.AFTER_LOGIN);
			CustomPublicKey cpk = new CustomPublicKey(Constants.SERVER_KEY_PAIR.getKeyNumber(), Constants.SERVER_KEY_PAIR.getPkiAlgorithm(), Constants.SERVER_KEY_PAIR.getPublicKey());
			responseObject.setServerPublicKey(cpk);
			responseObject.setReadAccountTO(new ReadAccountTransferObject(transform(userAccount)));
			return responseObject;
		} catch (UserAccountNotFoundException e) {
			return new CustomResponseObject(false, ACCOUNT_NOT_FOUND);
		}
	}

	/**
	 * Transforms server UserAccount (DB-model) into UserAccount for client.
	 * Does not change any UserAccount variables, creates a copy with the same
	 * attributes.
	 * 
	 * @param userAccount
	 *            (Server/DB UserAccount object)
	 * @return ch.uzh.csg.mbps.model.UserAccount() client UserAccount object
	 */
	protected static ch.uzh.csg.mbps.model.UserAccount transform(UserAccount userAccount) {
		ch.uzh.csg.mbps.model.UserAccount ua = new ch.uzh.csg.mbps.model.UserAccount();
		ua.setBalance(userAccount.getBalance());
		ua.setCreationDate(userAccount.getCreationDate());
		ua.setDeleted(userAccount.isDeleted());
		ua.setEmail(userAccount.getEmail());
		ua.setEmailVerified(userAccount.isEmailVerified());
		ua.setId(userAccount.getId());
		ua.setPassword(userAccount.getPassword());
		ua.setPaymentAddress(userAccount.getPaymentAddress());
		ua.setUsername(userAccount.getUsername());
		ua.setRoles(userAccount.getRoles());
		return ua;
	}

	/**
	 * Updates a emailaddress or password for a  {@link UserAccount}. Can not update
	 * other variables.
	 * 
	 * @param updatedAccount
	 * @return {@link CustomResponseObject} with information if operation was
	 *         successful/unsucessful.
	 */
	@RequestMapping(value = "/update", method = RequestMethod.POST, consumes = "application/json")
	@ResponseBody
	public CustomResponseObject updateAccount(@RequestBody UserAccount updatedAccount){
		try {
			boolean success = UserAccountService.getInstance().updateAccount(AuthenticationInfo.getPrincipalUsername(), updatedAccount);
			if (success)
				return new CustomResponseObject(true, UPDATE_SUCCESS);
			else
				return new CustomResponseObject(false, UPDATE_ERROR);
		} catch (UserAccountNotFoundException e) {
			return new CustomResponseObject(false, ACCOUNT_NOT_FOUND);
		}		
	}

	/**
	 * Deletes a {@link UserAccount} if balance is equal to zero. DB entry is not
	 * deleted, instead flag "isDeleted" is set to true. A deleted {@link UserAccount}
	 * can not be used to login anymore.
	 * 
	 * @return CustomResopnseObject with information if operation was
	 *         successful/unsucessful.
	 */
	@RequestMapping(value = "/delete", method = RequestMethod.POST, produces = "application/json")
	@ResponseBody
	public CustomResponseObject deleteUserAccount() {
		try {
			boolean success = UserAccountService.getInstance().delete(AuthenticationInfo.getPrincipalUsername());
			if (success)
				return new CustomResponseObject(true, DELETE_SUCCESS);
			else
				return new CustomResponseObject(false, TRY_AGAIN);
		} catch (UserAccountNotFoundException e) {
			return new CustomResponseObject(false, ACCOUNT_NOT_FOUND);
		} catch (BalanceNotZeroException e) {
			return new CustomResponseObject(false, BALANCE_NOT_ZERO);
		}
	}

	/**
	 * Entry point used to verify a {@link UserAccount}. Link is opened by browser and
	 * returns a browser page. Not used for access via android client.
	 * 
	 * @param verificationToken
	 * @return Java server page with information if UserAccount has been
	 *         successfully verified.
	 */
	@RequestMapping(value = "/verify/{verificationToken}", method = RequestMethod.GET, produces = "application/json")
	public String verifyEmailAccount(@PathVariable String verificationToken) {
		if (UserAccountService.getInstance().verifyEmailAddress(verificationToken))
			return "SuccessfulEmailVerification";
		else
			return "FailedEmailVerification";
	}

	/**
	 * Allows a user to reset his password. Creates a unique identification
	 * token which is sent to the users email address and saved to DB.
	 * 
	 * @param emailAddress
	 * @return CustomResponseObject with information if ResetPasswordToken has
	 *         been successfully/non successfully sent to the users email
	 *         address
	 */
	@RequestMapping(value = "/resetPasswordRequest", method = RequestMethod.POST, produces = "application/json")
	@ResponseBody
	public CustomResponseObject resetPasswordRequest(@RequestBody String emailAddress) {
		try {
			emailAddress = emailAddress.substring(1, emailAddress.length()-1);
			UserAccountService.getInstance().resetPasswordRequest(emailAddress);
			return new CustomResponseObject(true, PASSWORD_RESET_LINK_SENT);
		} catch (UserAccountNotFoundException e) {
			return new CustomResponseObject(false, e.getMessage());
		}
	}	

	/**
	 * Resets {@link UserAccount} password if passwords match. Returns java server page
	 * with information if password reset was successful or not.
	 * 
	 * @param matcher
	 *            Object which stores the two passwords entered by the user
	 * @return Java Server Page with information about successful/non successful
	 *         password reset.
	 */
	@RequestMapping(value = "/resetPassword/setPassword", method = RequestMethod.POST)
	public String setPassword(@ModelAttribute("server")PasswordMatcher matcher) {
		if (UserAccountService.getInstance().resetPassword(matcher)) {
			return "SuccessfulPasswordReset";
		} else {
			return "FailedPasswordReset";
		}
	}

	/**
	 * Checks if Url entered by the user is a valid ResetPassword link. Forwards
	 * user to /resetPassword/setPassword if url is valid or informs him about
	 * non valid ResetPassword link.
	 * 
	 * @param resetPasswordToken
	 * @return Java server page with mask to insert new password in case of a
	 *         valid link or information about non valid link
	 */
	@RequestMapping(value = "/resetPassword/{resetPasswordToken}", method = RequestMethod.GET)
	public ModelAndView resetPasswordProcessing(@PathVariable String resetPasswordToken) {
		if (UserAccountService.getInstance().isValidResetPasswordLink(resetPasswordToken)) {
			PasswordMatcher matcher = new PasswordMatcher();
			ModelAndView mv = new ModelAndView("ResetPassword", "command", matcher);
			mv.addObject("token", resetPasswordToken);
			return mv;
		} else {
			return new ModelAndView("WrongToken", "command", null);
		}
	}

	/**
	 * Request returns necessary information for updating the mainscreen of the MBPS
	 * application.
	 * 
	 * @return {@link CustomResponseObject} with information about
	 *         successful/non successful request, balance, exchangerate and last
	 *         3 transactions
	 */
	@RequestMapping(value = "/mainActivityRequests", method = RequestMethod.GET, produces = "application/json")
	@ResponseBody
	public CustomResponseObject mainActivityRequests() {
		try {
			String username = AuthenticationInfo.getPrincipalUsername();
			UserAccount userAccount = UserAccountService.getInstance().getByUsername(username);
			CustomResponseObject response = new CustomResponseObject();
			response.setSuccessful(true);
			response.setType(Type.MAIN_ACTIVITY);

			// set ExchangeRate
			response.setMessage(ExchangeRates.getExchangeRate().toString());
			//set History
			GetHistoryTransferObject ghto = new GetHistoryTransferObject();
			ghto.setTransactionHistory(TransactionService.getInstance().getLast3Transactions(username));
			ghto.setPayInTransactionHistory(PayInTransactionService.getInstance().getLast3Transactions(username));
			ghto.setPayOutTransactionHistory(PayOutTransactionService.getInstance().getLast3Transactions(username));
			response.setGetHistoryTO(ghto);
			//set Balance
			response.setBalance(userAccount.getBalance().toString());

			return response;
		} catch (ParseException | IOException | UserAccountNotFoundException e) {
			return new CustomResponseObject(false, "0.0", Type.MAIN_ACTIVITY);
		}
	}

	/**
	 * Saves a new {@link CustomPublicKey} entry for authenticated user.
	 * 
	 * @param userPublicKey
	 * @return {@link CustomResponseObject} with information about
	 *         successful/non successful request and assigned keyNumber as
	 *         message.
	 */
	@RequestMapping(value = "/savePublicKey", method = RequestMethod.POST, produces = "application/json")
	@ResponseBody
	public CustomResponseObject savePublicKey(@RequestBody CustomPublicKey userPublicKey) {
		try {
			UserAccount userAccount = UserAccountService.getInstance().getByUsername(AuthenticationInfo.getPrincipalUsername());
			PKIAlgorithm pkiAlgorithm = PKIAlgorithm.getPKIAlgorithm(userPublicKey.getPkiAlgorithm());
			byte keyNumber = UserAccountService.getInstance().saveUserPublicKey(userAccount.getId(), pkiAlgorithm, userPublicKey.getPublicKey());
			return new CustomResponseObject(true, Byte.toString(keyNumber), Type.SAVE_PUBLIC_KEY);
		} catch (UserAccountNotFoundException e) {
			return new CustomResponseObject(false, ACCOUNT_NOT_FOUND, Type.SAVE_PUBLIC_KEY);
		} catch (UnknownPKIAlgorithmException e) {
			//TODO jeton: ?
			return new CustomResponseObject(false, "unkown pki algorithm", Type.SAVE_PUBLIC_KEY);
		}
	}

}
