package ch.uzh.csg.mbps.server.controller;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;

import net.minidev.json.parser.ParseException;

import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import ch.uzh.csg.mbps.customserialization.DecoderFactory;
import ch.uzh.csg.mbps.customserialization.PKIAlgorithm;
import ch.uzh.csg.mbps.customserialization.PaymentRequest;
import ch.uzh.csg.mbps.customserialization.PaymentResponse;
import ch.uzh.csg.mbps.customserialization.ServerPaymentRequest;
import ch.uzh.csg.mbps.customserialization.ServerPaymentResponse;
import ch.uzh.csg.mbps.customserialization.ServerResponseStatus;
import ch.uzh.csg.mbps.model.HistoryPayInTransaction;
import ch.uzh.csg.mbps.model.HistoryPayOutTransaction;
import ch.uzh.csg.mbps.model.HistoryTransaction;
import ch.uzh.csg.mbps.responseobject.GetHistoryTransferObject;
import ch.uzh.csg.mbps.responseobject.HistoryTransferRequestObject;
import ch.uzh.csg.mbps.responseobject.PayOutTransactionObject;
import ch.uzh.csg.mbps.responseobject.TransactionObject;
import ch.uzh.csg.mbps.responseobject.TransferObject;
import ch.uzh.csg.mbps.server.clientinterface.ITransaction;
import ch.uzh.csg.mbps.server.clientinterface.IUserAccount;
import ch.uzh.csg.mbps.server.domain.UserAccount;
import ch.uzh.csg.mbps.server.security.KeyHandler;
import ch.uzh.csg.mbps.server.service.PayInTransactionService;
import ch.uzh.csg.mbps.server.service.PayInTransactionUnverifiedService;
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

	@Autowired
	private PayInTransactionService payInTransactionService;
	
	@Autowired
	private PayInTransactionUnverifiedService payInTransactionUnverifiedService;
	
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
	public TransactionObject createTransaction(@RequestBody TransactionObject requestObject) {
		long start = System.currentTimeMillis();
		TransactionObject responseObject = new TransactionObject();
		try {
			ServerPaymentRequest spr = DecoderFactory.decode(ServerPaymentRequest.class, requestObject.getServerPaymentResponse());
			try {
				String username = AuthenticationInfo.getPrincipalUsername();
				ServerPaymentResponse serverPaymentResponse = transactionService.createTransaction(username, spr);
				responseObject.setSuccessful(true);
				responseObject.setServerPaymentResponse(serverPaymentResponse.encode());
				responseObject.setBalanceBTC(userAccountService.getByUsername(username).getBalance());
				long diff = System.currentTimeMillis() - start;
				System.err.println("server time1:"+diff+"ms");
				return responseObject;
			} catch (TransactionException | UserAccountNotFoundException e) {
				PaymentRequest paymentRequestPayer = spr.getPaymentRequestPayer();
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
				
				responseObject.setSuccessful(true);
				responseObject.setServerPaymentResponse(new ServerPaymentResponse(paymentResponsePayer).encode());
				long diff = System.currentTimeMillis() - start;
				System.err.println("server time2:"+diff+"ms");
				
			}
		} catch (Throwable e) {
			e.printStackTrace();
			LOGGER.error(e.getMessage());
			responseObject.setSuccessful(false);
			responseObject.setMessage(Constants.INTERNAL_SERVER_ERROR + ": " + e.getMessage());
		}
		return responseObject;
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
	@RequestMapping(value = "/history", method = RequestMethod.POST, produces = "application/json")
	@ResponseBody
	public GetHistoryTransferObject getHistory(@RequestBody HistoryTransferRequestObject request) {
		GetHistoryTransferObject response = new GetHistoryTransferObject();
		if(!request.isComplete()) {
			response.setSuccessful(false);
			response.setMessage("request has missing parameters");
			return response;
		}
		try {
			String username = AuthenticationInfo.getPrincipalUsername();
			
			int txPage = request.getTxPage().intValue();
			int txPayInPage = request.getTxPayInPage().intValue();
			int txPayInUnverifiedPage = request.getTxPayInUnverifiedPage().intValue();
			int txPayOutPage = request.getTxPayOutPage().intValue();
			List<HistoryTransaction> history = transactionService.getHistory(username, txPage);
			long nofTx = (txPage < 0) ? 0 : transactionService.getHistoryCount(username);
			
			List<HistoryPayInTransaction> payInHistory = payInTransactionService.getHistory(username, txPayInPage);
			long nofPayInTx = (txPayInPage < 0) ? 0 : payInTransactionService.getHistoryCount(username);
			
			List<HistoryPayInTransaction> payInUnverifiedHistory = payInTransactionUnverifiedService.getHistory(username, txPayInUnverifiedPage);
			long nofPayInUnverifiedTx = (txPayInUnverifiedPage < 0) ? 0 : payInTransactionUnverifiedService.getHistoryCount(username);
			
			List<HistoryPayOutTransaction> payOutHistory = payOutTransactionService.getHistory(username, txPayOutPage);
			long nofPayOutTx = (txPayOutPage < 0) ? 0 : payOutTransactionService.getHistoryCount(username);
			
			response.setSuccessful(true);
			response.setTransactionHistory(history);
			response.setPayInTransactionHistory(payInHistory);
			response.setPayInTransactionUnverifiedHistory(payInUnverifiedHistory);
			response.setPayOutTransactionHistory(payOutHistory);
			
			response.setNofTransactions(nofTx);
			response.setNofPayInTransactions(nofPayInTx);
			response.setNofPayInTransactionsUnverified(nofPayInUnverifiedTx);
			response.setNofPayOutTransactions(nofPayOutTx);
			
			
			return response;
		} catch (Exception e) {
			response.setSuccessful(false);
			response.setMessage(e.getMessage());
			LOGGER.error(e.getMessage());
			return response;
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
	@RequestMapping(value = "/history/getByEmail", method = RequestMethod.POST, produces = "application/json")
	@ResponseBody
	public TransferObject getHistoryByEmail(@RequestBody TransferObject transferObject) {
		TransferObject response = new TransferObject();
		String sType = transferObject.getMessage();
		
		
		if(sType == null) {
			response.setSuccessful(false);
			response.setMessage("not type specified");
			return response;
		}
		
		int type;
		try {
			type = Integer.parseInt(sType);
		} catch (NumberFormatException nfe) {
			response.setSuccessful(false);
			response.setMessage(nfe.getMessage());
			return response;
		}
		
		if (type < 0 || type > 3) {
			response.setSuccessful(false);
			response.setMessage("parameter is not valid");
			return response;
		}
		
		String username = AuthenticationInfo.getPrincipalUsername();
		try {
			historyEmailHandler.sendHistoryByEmail(username, type);
			String msg;
			if (type == 0) {
				msg = "Transaction history has successfully been send to your email address.";
			} else if (type == 1) {
				msg = "Pay In history has successfully been send to your email address.";
			} else if (type == 2) {
				msg = "Pay In unverified history has successfully been send to your email address.";
			} else {
				msg = "Pay Out history has successfully been send to your email address.";
			}
			
			response.setSuccessful(true);
			response.setMessage(msg);
			return response;
			
		} catch (Exception e) {
			response.setSuccessful(false);
			response.setMessage("History could not be send due to an internal error. Please try again later.");
			return response;
		}
	}
	
	/**
	 * Returns up to date exchangerate BTC/CHF
	 * 
	 * @return CustomResponseObject with exchangeRate BTC/CHF as a String
	 */
	@RequestMapping(value = "/exchange-rate", method = RequestMethod.GET, produces = "application/json")
	@ResponseBody
	public TransferObject getExchangeRate() {
		TransferObject transferObject = new TransferObject();
		try {
			transferObject.setSuccessful(true);
			transferObject.setMessage(ExchangeRates.getExchangeRate().toString());
		} catch (ParseException | IOException e) {
			LOGGER.error("Couldn't get exchange rate. Response: " + e.getMessage());
			transferObject.setSuccessful(false);
			transferObject.setMessage(e.getMessage());
		}  catch (Throwable t) {
			transferObject.setSuccessful(false);
			transferObject.setMessage("Unexpected: "+t.getMessage());
		}
		return transferObject;
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
	public TransferObject payOut(@RequestBody PayOutTransactionObject pot){
		TransferObject transferObject = new TransferObject();
		try {
			String username = AuthenticationInfo.getPrincipalUsername();
			BigDecimal amount = pot.getAmount();
			String address = pot.getBtcAddress();
			
			transferObject.setSuccessful(true);
			transferObject = payOutTransactionService.createPayOutTransaction(username, amount, address);
		} catch (UserAccountNotFoundException e) {
			transferObject.setSuccessful(false);
			transferObject.setMessage("UserAccount not found.");
		} catch (BitcoinException e) {
			transferObject.setSuccessful(false);
			transferObject.setMessage("Error: Transaction could not be sent to the BTC Network.");
		} catch (Throwable t) {
			transferObject.setSuccessful(false);
			transferObject.setMessage("Unexpected: "+t.getMessage());
		}
		return transferObject;
	}
	
	/**
	 * Sends users bitcoin-address for pay-In to email address defined in
	 * {@link UserAccount}
	 * 
	 * @return {@link CustomResponseObject} with information about
	 *         successful/non successful request
	 */
	@RequestMapping(value = "/payIn/getByEmail", method = RequestMethod.GET, produces = "application/json")
	@ResponseBody
	public TransferObject sendPayInByEmail(){
		TransferObject transferObject = new TransferObject();
		try{
			UserAccount userAccount = userAccountService.getByUsername(AuthenticationInfo.getPrincipalUsername());
			
			transferObject.setSuccessful(true);
			transferObject = payInTransactionService.sendPayInAddressByEmail(userAccount.getUsername(), userAccount.getEmail(), userAccount.getPaymentAddress());
		} catch(UserAccountNotFoundException e){
			transferObject.setSuccessful(false);
			transferObject.setMessage("UserAccount not found.");
		}  catch (Throwable t) {
			transferObject.setSuccessful(false);
			transferObject.setMessage("Unexpected: "+t.getMessage());
		}
		return transferObject;
	}
	
}
