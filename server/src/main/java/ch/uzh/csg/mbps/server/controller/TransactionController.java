package ch.uzh.csg.mbps.server.controller;

import java.io.IOException;
import java.util.List;

import net.minidev.json.parser.ParseException;

import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import ch.uzh.csg.mbps.customserialization.PKIAlgorithm;
import ch.uzh.csg.mbps.customserialization.PaymentRequest;
import ch.uzh.csg.mbps.customserialization.PaymentResponse;
import ch.uzh.csg.mbps.customserialization.ServerPaymentResponse;
import ch.uzh.csg.mbps.customserialization.ServerResponseStatus;
import ch.uzh.csg.mbps.model.HistoryPayInTransaction;
import ch.uzh.csg.mbps.model.HistoryPayOutTransaction;
import ch.uzh.csg.mbps.model.HistoryTransaction;
import ch.uzh.csg.mbps.responseobject.CreateTransactionTransferObject;
import ch.uzh.csg.mbps.responseobject.CustomResponseObject;
import ch.uzh.csg.mbps.responseobject.CustomResponseObject.Type;
import ch.uzh.csg.mbps.responseobject.GetHistoryTransferObject;
import ch.uzh.csg.mbps.server.clientinterface.ITransaction;
import ch.uzh.csg.mbps.server.clientinterface.IUserAccount;
import ch.uzh.csg.mbps.server.domain.PayOutTransaction;
import ch.uzh.csg.mbps.server.domain.UserAccount;
import ch.uzh.csg.mbps.server.security.KeyHandler;
import ch.uzh.csg.mbps.server.service.PayInTransactionService;
import ch.uzh.csg.mbps.server.service.PayOutTransactionService;
import ch.uzh.csg.mbps.server.util.AuthenticationInfo;
import ch.uzh.csg.mbps.server.util.Constants;
import ch.uzh.csg.mbps.server.util.ExchangeRates;
import ch.uzh.csg.mbps.server.util.HistoryEmailHandler;
import ch.uzh.csg.mbps.server.util.exceptions.TransactionException;
import ch.uzh.csg.mbps.server.util.exceptions.UserAccountNotFoundException;

import com.azazar.bitcoin.jsonrpcclient.BitcoinException;

/**
 * REST Controller for client http requests regarding Transactions between two
 * UserAccounts.
 * 
 */
@Controller
@RequestMapping("/transaction")
public class TransactionController {
	private static Logger LOGGER = Logger.getLogger(TransactionController.class);
	private static final String SUCCESS = "The transaction has been successfully created.";

	@Autowired
	private PayInTransactionService payInTransactionService;
	
	@Autowired
	private PayOutTransactionService payOutTransactionService;
	
	@Autowired
	private ITransaction transactionService;
	
	@Autowired
	private IUserAccount userAccountService;
	
	@Autowired
	private HistoryEmailHandler historyEmailHandler;
	
	/**
	 * Creates new transaction between two UserAccounts. Verifies/validates
	 * transactions signed from the two users. Transfers money and updates
	 * balances in case of a valid transaction or returns information about
	 * reason for failure.
	 * 
	 * @param requestObject
	 * @return CustomResponseObject with information about successful/non
	 *         successful transaction.
	 */
	@RequestMapping(value = "/create", method = RequestMethod.POST, consumes = "application/json", produces = "application/json")
	@ResponseBody
	public CustomResponseObject createTransaction(@RequestBody CreateTransactionTransferObject requestObject) {
		long start = System.currentTimeMillis();
		CustomResponseObject responseObject;
		try {
			try {
				String username = AuthenticationInfo.getPrincipalUsername();
				ServerPaymentResponse serverPaymentResponse = transactionService.createTransaction(username, requestObject.getServerPaymentRequest());
				responseObject = new CustomResponseObject(true, SUCCESS, Type.CREATE_TRANSACTION);
				responseObject.setServerPaymentResponse(serverPaymentResponse.encode());
				responseObject.setBalance(userAccountService.getByUsername(username).getBalance().toPlainString());
				long diff = System.currentTimeMillis() - start;
				System.err.println("server time1:"+diff+"ms");
				return responseObject;
			} catch (TransactionException | UserAccountNotFoundException e) {
				PaymentRequest paymentRequestPayer = requestObject.getServerPaymentRequest().getPaymentRequestPayer();
				PaymentResponse paymentResponsePayer = new PaymentResponse(
						PKIAlgorithm.getPKIAlgorithm(Constants.SERVER_KEY_PAIR.getPkiAlgorithm()),
						Constants.SERVER_KEY_PAIR.getKeyNumber(),
						ServerResponseStatus.FAILURE,
						e.getMessage(),
						paymentRequestPayer.getUsernamePayer(),
						paymentRequestPayer.getUsernamePayee(),
						paymentRequestPayer.getCurrency(),
						paymentRequestPayer.getAmount(),
						paymentRequestPayer.getTimestamp());
				paymentResponsePayer.sign(KeyHandler.decodePrivateKey(Constants.SERVER_KEY_PAIR.getPrivateKey()));
				
				responseObject = new CustomResponseObject(true, null, Type.CREATE_TRANSACTION);
				responseObject.setServerPaymentResponse(new ServerPaymentResponse(paymentResponsePayer).encode());
				
				LOGGER.error(e.getMessage());
				long diff = System.currentTimeMillis() - start;
				System.err.println("server time2:"+diff+"ms");
				return responseObject;
			}
		} catch (Exception e) {
			e.printStackTrace();
			LOGGER.error(e.getMessage());
			return new CustomResponseObject(false, Constants.INTERNAL_SERVER_ERROR + ": " + e.getMessage(), Type.CREATE_TRANSACTION);
		}
	}
	
	/**
	 * Returns the history of all transactions assigned to the authenticated
	 * UserAccount. If a parameter is negative, the corresponding history type
	 * is not returned. Otherwise, the given page of the corresponding history
	 * type is returned. If a page number is too large, an empty list might be
	 * returned. The returned lists are ordered by their time stamp descending.
	 * 
	 * @param txPage
	 *            the page number of common transactions
	 * @param txPayInPage
	 *            the page number of pay in transactions
	 * @param txPayOutPage
	 *            the page number of pay out transactions
	 * @return CustomResponseObject with the ordered transactions and
	 *         information about success/non success of request.
	 */
	@RequestMapping(value = "/history", params = { "txPage", "txPayInPage", "txPayOutPage" }, method = RequestMethod.GET, produces = "application/json")
	@ResponseBody
	public CustomResponseObject getHistory(
			@RequestParam(value="txPage") int txPage,
			@RequestParam(value="txPayInPage") int txPayInPage,
			@RequestParam(value="txPayOutPage") int txPayOutPage ) {
		
		try {
			String username = AuthenticationInfo.getPrincipalUsername();
			
			List<HistoryTransaction> history = transactionService.getHistory(username, txPage);
			long nofTx = (txPage < 0) ? 0 : transactionService.getHistoryCount(username);
			
			List<HistoryPayInTransaction> payInHistory = payInTransactionService.getHistory(username, txPayInPage);
			long nofPayInTx = (txPayInPage < 0) ? 0 : payInTransactionService.getHistoryCount(username);
			
			List<HistoryPayOutTransaction> payOutHistory = payOutTransactionService.getHistory(username, txPayOutPage);
			long nofPayOutTx = (txPayOutPage < 0) ? 0 : payOutTransactionService.getHistoryCount(username);
			
			CustomResponseObject responseObject = new CustomResponseObject(true, "");
			responseObject.setGetHistoryTO(new GetHistoryTransferObject(history, payInHistory, payOutHistory, nofTx, nofPayInTx, nofPayOutTx));
			
			return responseObject;
		} catch (UserAccountNotFoundException e) {
			return new CustomResponseObject(false, e.getMessage());
		}
		catch (Exception e) {
			LOGGER.error(e.getMessage());
			return new CustomResponseObject(false, Constants.INTERNAL_SERVER_ERROR + ": " + e.getMessage());
		}
	}
	
	/**
	 * Sends an email to the authenticated user containing a csv-file with the
	 * given transaction type. It is only possible to request a history list for
	 * only one transaction type at once.
	 * 
	 * @param type
	 *            0 for common transactions, 1 for pay in transactions, 2 for
	 *            pay out transactions
	 * @return CustomResponseObject showing if the action was successful or not
	 *         and the appropriate message to show on the client
	 */
	@RequestMapping(value = "/history/getByEmail", params = { "type" }, method = RequestMethod.POST, produces = "application/json")
	@ResponseBody
	public CustomResponseObject getHistoryByEmail(@RequestParam(value="type") int type) {
		if (type < 0 || type > 3)
			return new CustomResponseObject(false, "parameter is not valid", Type.HISTORY_EMAIL);
		
		String username = AuthenticationInfo.getPrincipalUsername();
		try {
			historyEmailHandler.sendHistoryByEmail(username, type);
			String msg;
			if (type == 0)
				msg = "Transaction history has successfully been send to your email address.";
			else if (type == 1)
				msg = "Pay In history has successfully been send to your email address.";
			else
				msg = "Pay Out history has successfully been send to your email address.";
			
			return new CustomResponseObject(true, msg, Type.HISTORY_EMAIL);
		} catch (Exception e) {
			return new CustomResponseObject(false, "History could not be send due to an internal error. Please try again later.", Type.HISTORY_EMAIL);
		}
	}
	
	/**
	 * Returns up to date exchangerate BTC/CHF
	 * 
	 * @return CustomResponseObject with exchangeRate BTC/CHF as a String
	 */
	@RequestMapping(value = "/exchange-rate", method = RequestMethod.GET, produces = "application/json")
	@ResponseBody
	public CustomResponseObject getExchangeRate() {
		try {
			return new CustomResponseObject(true, ExchangeRates.getExchangeRate().toString(), Type.EXCHANGE_RATE);
		} catch (ParseException | IOException e) {
			LOGGER.error("Couldn't get exchange rate. Response: " + e.getMessage());
			return new CustomResponseObject(false, "0.0", Type.EXCHANGE_RATE);
		}
	}
	
	/**
	 * Controls payOut request for authenticated UserAccount. Checks if defined
	 * BTC-Address is valid and sends the defined amount to this address if the
	 * balance is high enough.
	 * 
	 * @param pot
	 * @return Information about successful/non successful request.
	 */
	@RequestMapping(value = "/payOut", method = RequestMethod.POST, consumes = "application/json", produces = "application/json")
	@ResponseBody
	public CustomResponseObject payOut(@RequestBody PayOutTransaction pot){
		try {
			String username = AuthenticationInfo.getPrincipalUsername();
			return payOutTransactionService.createPayOutTransaction(username, pot);
		} catch (UserAccountNotFoundException e) {
			return new CustomResponseObject(false, "UserAccount not found.");
		} catch (BitcoinException e) {
			return  new CustomResponseObject(false, "Error: Transaction could not be sent to the BTC Network.");
		}
	}
	
	/**
	 * Sends users bitcoin-address for pay-In to email address defined in
	 * {@link UserAccount}
	 * 
	 * @return {@link CustomResponseObject} with information about
	 *         successful/non successful request
	 */
	@RequestMapping(value = "/payIn/getByEmail", method = RequestMethod.POST, produces = "application/json")
	@ResponseBody
	public CustomResponseObject sendPayInByEmail(){
		try{
			UserAccount userAccount = userAccountService.getByUsername(AuthenticationInfo.getPrincipalUsername());
			return payInTransactionService.sendPayInAddressByEmail(userAccount.getUsername(), userAccount.getEmail(), userAccount.getPaymentAddress());
		} catch(UserAccountNotFoundException e){
			return new CustomResponseObject(false, "UserAccount not found.");
		}
	}
	
}
