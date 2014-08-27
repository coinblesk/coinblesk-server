package ch.uzh.csg.mbps.server.controller;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Calendar;
import java.util.List;

import net.minidev.json.parser.ParseException;

import org.springframework.beans.factory.annotation.Autowired;
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
import ch.uzh.csg.mbps.responseobject.CustomPublicKeyObject;
import ch.uzh.csg.mbps.responseobject.GetHistoryTransferObject;
import ch.uzh.csg.mbps.responseobject.MainRequestObject;
import ch.uzh.csg.mbps.responseobject.ReadRequestObject;
import ch.uzh.csg.mbps.responseobject.TransferObject;
import ch.uzh.csg.mbps.responseobject.UserAccountObject;
import ch.uzh.csg.mbps.server.clientinterface.IPayInTransaction;
import ch.uzh.csg.mbps.server.clientinterface.IPayOutTransaction;
import ch.uzh.csg.mbps.server.clientinterface.ITransaction;
import ch.uzh.csg.mbps.server.clientinterface.IUserAccount;
import ch.uzh.csg.mbps.server.domain.UserAccount;
import ch.uzh.csg.mbps.server.domain.UserPublicKey;
import ch.uzh.csg.mbps.server.util.AdminObject;
import ch.uzh.csg.mbps.server.util.AuthenticationInfo;
import ch.uzh.csg.mbps.server.util.Constants;
import ch.uzh.csg.mbps.server.util.ExchangeRates;
import ch.uzh.csg.mbps.server.util.PasswordMatcher;
import ch.uzh.csg.mbps.server.util.UserRoles.Role;
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
	
	@Autowired
	private IPayInTransaction payInTransactionService;
	
	@Autowired
	private IPayOutTransaction payOutTransactionService;
	
	@Autowired
	private ITransaction transactionService;
	
	@Autowired
	private IUserAccount userAccountService;

	/**
	 * Creates a new UserAccount and saves it to DB.
	 * 
	 * @param userAccount
	 * @return CustomResponseObject with information about successful/non
	 *         successful transaction.
	 */
	@RequestMapping(value = "/create", method = RequestMethod.POST, consumes = "application/json")
	@ResponseBody
	public TransferObject createAccount(@RequestBody UserAccountObject userAccountObject) {
		TransferObject response = new TransferObject();
		try {
			UserAccount userAccount = new UserAccount();
			userAccount.setBalance(BigDecimal.ZERO);
			userAccount.setCreationDate(Calendar.getInstance().getTime());
			userAccount.setDeleted(false);
			userAccount.setEmail(userAccountObject.getEmail());
			userAccount.setEmailVerified(false);
			userAccount.setPassword(userAccountObject.getPassword());
			userAccount.setPaymentAddress(userAccountObject.getPaymentAddress());
			userAccount.setRoles(Role.USER.getCode());
			userAccount.setUsername(userAccountObject.getUsername());
			boolean success = userAccountService.createAccount(userAccount);
			if (success) {
				response.setSuccessful(true);
				response.setMessage(CREATION_SUCCESS);
			}
			else {
				response.setSuccessful(false);
				response.setMessage(CREATION_ERROR);
			}
		} catch (UsernameAlreadyExistsException e) {
			response.setSuccessful(false);
			response.setMessage(USERNAME_ALREADY_EXISTS);
		} catch (BitcoinException e) {
			response.setSuccessful(false);
			response.setMessage(BITCOIN_ADDRESS_ERROR);
		} catch (InvalidUsernameException e) {
			response.setSuccessful(false);
			response.setMessage(INVALID_USERNAME);
		} catch (InvalidEmailException e) {
			response.setSuccessful(false);
			response.setMessage(INVALID_EMAIL);
		} catch (EmailAlreadyExistsException e) {
			response.setSuccessful(false);
			response.setMessage(EMAIL_ALREADY_EXISTS);
		} catch (Throwable t) {
			response.setSuccessful(false);
			response.setMessage("Unexpected: "+t.getMessage());
		}
		return response;
	}

	/**
	 * Request executed after successful Login of Client. Returns {@link
	 * ch.uzh.csg.mbps.model.UserAccount} and ServerPublicKey
	 * 
	 * @return CustomResponseObject with UserAccount object and ServerPublicKey
	 */
	@RequestMapping(value = "/afterLogin", method = RequestMethod.GET, produces = "application/json")
	@ResponseBody
	public ReadRequestObject getUserAccount() {
		try {
			UserAccount userAccount = userAccountService.getByUsername(AuthenticationInfo.getPrincipalUsername());
			List<UserPublicKey> pubList = userAccountService.getUserPublicKey(userAccount.getId());
			
			CustomPublicKey cpk = new CustomPublicKey(Constants.SERVER_KEY_PAIR.getKeyNumber(), Constants.SERVER_KEY_PAIR.getPkiAlgorithm(), Constants.SERVER_KEY_PAIR.getPublicKey());
			CustomPublicKeyObject c = new CustomPublicKeyObject();
			c.setCustomPublicKey(cpk);
			ReadRequestObject response = new ReadRequestObject();
			
			response.setMessage( pubList.isEmpty()? null: Integer.toString(pubList.size()));
			response.setCustomPublicKey(c);
			
			
			UserAccountObject uao = transform1(userAccount); 
					
			response.setUserAccount(uao);
			response.setSuccessful(true);
			return response;
		} catch (UserAccountNotFoundException e) {
			ReadRequestObject failed = new ReadRequestObject();
			failed.setMessage(ACCOUNT_NOT_FOUND);
			failed.setSuccessful(false);
			return failed;
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
	
	private UserAccountObject transform1(UserAccount userAccount) {
		UserAccountObject o = new UserAccountObject();
		o.setBalanceBTC(userAccount.getBalance());
		o.setEmail(userAccount.getEmail());
		o.setId(userAccount.getId());
		o.setPassword(userAccount.getPassword());
		o.setPaymentAddress(userAccount.getPaymentAddress());
		o.setUsername(userAccount.getUsername());
		return o;
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
	public TransferObject updateAccount(@RequestBody UserAccountObject userAccountObject){
		TransferObject response = new TransferObject();
		try {
			UserAccount userAccount = new UserAccount();
			userAccount.setBalance(BigDecimal.ZERO);
			userAccount.setCreationDate(Calendar.getInstance().getTime());
			userAccount.setDeleted(false);
			userAccount.setEmail(userAccountObject.getEmail());
			userAccount.setEmailVerified(false);
			userAccount.setPassword(userAccountObject.getPassword());
			userAccount.setPaymentAddress(userAccountObject.getPaymentAddress());
			userAccount.setRoles(Role.USER.getCode());
			userAccount.setUsername(userAccountObject.getUsername());
			boolean success = userAccountService.updateAccount(AuthenticationInfo.getPrincipalUsername(), userAccount);
			if (success) {
				response.setSuccessful(true);
				response.setMessage(UPDATE_SUCCESS);
			}
			else {
				response.setSuccessful(false);
				response.setMessage(UPDATE_ERROR);
			}
		} catch (UserAccountNotFoundException e) {
			response.setSuccessful(false);
			response.setMessage(ACCOUNT_NOT_FOUND);
		} catch (Throwable t) {
			response.setSuccessful(false);
			response.setMessage("Unexpected: "+t.getMessage());
		}
		return response;	
	}

	/**
	 * Deletes a {@link UserAccount} if balance is equal to zero. DB entry is not
	 * deleted, instead flag "isDeleted" is set to true. A deleted {@link UserAccount}
	 * can not be used to login anymore.
	 * 
	 * @return CustomResopnseObject with information if operation was
	 *         successful/unsucessful.
	 */
	@RequestMapping(value = "/delete", method = RequestMethod.GET, produces = "application/json")
	@ResponseBody
	public TransferObject deleteUserAccount() {
		TransferObject response = new TransferObject();
		try {
			boolean success = userAccountService.delete(AuthenticationInfo.getPrincipalUsername());
			if (success) {
				response.setSuccessful(true);
				response.setMessage(DELETE_SUCCESS);
			}
			else {
				response.setSuccessful(false);
				response.setMessage(TRY_AGAIN);
			}
		} catch (UserAccountNotFoundException e) {
			response.setSuccessful(false);
			response.setMessage(ACCOUNT_NOT_FOUND);
		} catch (BalanceNotZeroException e) {
			response.setSuccessful(false);
			response.setMessage(BALANCE_NOT_ZERO);
		}   catch (Throwable t) {
			response.setSuccessful(false);
			response.setMessage("Unexpected: "+t.getMessage());
		}
		return response;
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
		if (userAccountService.verifyEmailAddress(verificationToken))
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
	public TransferObject resetPasswordRequest(@RequestBody TransferObject request) {
		TransferObject response = new TransferObject();
		try {
			UserAccount userAccount = userAccountService.getByEmail(request.getMessage());
			userAccountService.resetPasswordRequest(userAccount.getEmail());
			response.setSuccessful(true);
			response.setMessage(PASSWORD_RESET_LINK_SENT);
		} catch (UserAccountNotFoundException e) {
			response.setSuccessful(false);
			response.setMessage(e.getMessage());
		}  catch (Throwable t) {
			response.setSuccessful(false);
			response.setMessage("Unexpected: "+t.getMessage());
		}
		return response;
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
		if (userAccountService.resetPassword(matcher)) {
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
		if (userAccountService.isValidResetPasswordLink(resetPasswordToken)) {
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
	public MainRequestObject mainActivityRequests() {
		MainRequestObject response = new MainRequestObject();
		try {
			String username = AuthenticationInfo.getPrincipalUsername();
			UserAccount userAccount = userAccountService.getByUsername(username);
			
			response.setSuccessful(true);

			// set ExchangeRate
			response.setExchangeRate(ExchangeRates.getExchangeRate(), "CHF");
			response.setBalanceBTC(userAccount.getBalance());
			//set History
			GetHistoryTransferObject ghto = new GetHistoryTransferObject();
			ghto.setTransactionHistory(transactionService.getLast5Transactions(username));
			ghto.setPayInTransactionHistory(payInTransactionService.getLast5Transactions(username));
			ghto.setPayOutTransactionHistory(payOutTransactionService.getLast5Transactions(username));
			response.setGetHistoryTransferObject(ghto);
			UserAccountObject uao = transform1(userAccount);
			response.setUserAccount(uao);
			//set Balance
			response.setBalanceBTC(userAccount.getBalance());

			return response;
		} catch (ParseException | IOException | UserAccountNotFoundException e) {
			response.setSuccessful(false);
			response.setMessage("0.0");
			return response;
		}
	}

	/**
	 * Stores the given {@link CustomPublicKey} and maps it to the authenticated
	 * user.
	 * 
	 * @param userPublicKey
	 *            the public key to store
	 * @return {@link CustomResponseObject} indicating if the key has been
	 *         stored and the assigned key number as message.
	 */
	@RequestMapping(value = "/savePublicKey", method = RequestMethod.POST, produces = "application/json")
	@ResponseBody
	public TransferObject savePublicKey(@RequestBody CustomPublicKeyObject userPublicKey) {
		TransferObject response = new TransferObject();
		try {
			UserAccount userAccount = userAccountService.getByUsername(AuthenticationInfo.getPrincipalUsername());
			PKIAlgorithm pkiAlgorithm = PKIAlgorithm.getPKIAlgorithm(userPublicKey.getCustomPublicKey().getPkiAlgorithm());
			byte keyNumber = userAccountService.saveUserPublicKey(userAccount.getId(), pkiAlgorithm, userPublicKey.getCustomPublicKey().getPublicKey());
			response.setSuccessful(true);
			response.setMessage(Byte.toString(keyNumber));
		} catch (UserAccountNotFoundException e) {
			response.setSuccessful(false);
			response.setMessage(ACCOUNT_NOT_FOUND);
		} catch (UnknownPKIAlgorithmException e) {
			response.setSuccessful(false);
			response.setMessage(UPDATE_ERROR);
		}  catch (Throwable t) {
			response.setSuccessful(false);
			response.setMessage("Unexpected: "+t.getMessage());
		}
		return response;
	}

	// TODO: mehmet create Account should be called
	@RequestMapping(value = "/createAdmin/{adminRoleToken}", method = RequestMethod.GET)
	public ModelAndView createAdminProcessing(@PathVariable String adminRoleToken) {
		if (userAccountService.isValidResetPasswordLink(adminRoleToken)) {
			AdminObject admin = new AdminObject();
			ModelAndView mv = new ModelAndView("RegisterAdmin", "command",admin);
			mv.addObject("token", adminRoleToken);
			return mv;
		} else {
			return new ModelAndView("WrongToken", "command", null);
		}
	}
}
