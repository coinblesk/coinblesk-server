package ch.uzh.csg.mbps.server.service;

import java.math.BigDecimal;
import java.util.ArrayList;

import org.hibernate.HibernateException;

import ch.uzh.csg.mbps.customserialization.Currency;
import ch.uzh.csg.mbps.customserialization.PKIAlgorithm;
import ch.uzh.csg.mbps.customserialization.PaymentRequest;
import ch.uzh.csg.mbps.customserialization.PaymentResponse;
import ch.uzh.csg.mbps.customserialization.ServerPaymentRequest;
import ch.uzh.csg.mbps.customserialization.ServerPaymentResponse;
import ch.uzh.csg.mbps.customserialization.ServerResponseStatus;
import ch.uzh.csg.mbps.model.HistoryTransaction;
import ch.uzh.csg.mbps.server.clientinterface.ITransaction;
import ch.uzh.csg.mbps.server.dao.TransactionDAO;
import ch.uzh.csg.mbps.server.dao.UserPublicKeyDAO;
import ch.uzh.csg.mbps.server.domain.DbTransaction;
import ch.uzh.csg.mbps.server.domain.UserAccount;
import ch.uzh.csg.mbps.server.security.KeyHandler;
import ch.uzh.csg.mbps.server.util.Constants;
import ch.uzh.csg.mbps.server.util.exceptions.PayOutRuleNotFoundException;
import ch.uzh.csg.mbps.server.util.exceptions.TransactionException;
import ch.uzh.csg.mbps.server.util.exceptions.UserAccountNotFoundException;
import ch.uzh.csg.mbps.util.Converter;

import com.azazar.bitcoin.jsonrpcclient.BitcoinException;

/**
 * Service class for {@link DbTransaction} between two {@link UserAccount}s.
 *
 */
public class TransactionService implements ITransaction {

	//TODO: mehmet move to a config file
	public static final String BALANCE = "Not sufficient funds. Payment rejected.";
	public static final String NEGATIVE_AMOUNT = "The transaction amount can't be negative or equals 0.";
	public static final String HIBERNATE_ERROR = "An error occured while persisting the data. Please try again later.";
	public static final String INTERNAL_ERROR = "An internal error occured. Please try again later.";
	public static final String PAYMENT_REFUSE = "The server refused the payment.";


	private static TransactionService transactionService;

	private TransactionService() {
	}

	/**
	 * Returns new or existing instance of {@link TransactionService}
	 * 
	 * @return instance of TransactionService
	 */
	public static TransactionService getInstance() {
		if (transactionService == null)
			transactionService = new TransactionService();

		return transactionService;
	}

	@Override
	public ArrayList<HistoryTransaction> getHistory(String username, int page) throws UserAccountNotFoundException {
		return TransactionDAO.getHistory(username, page);
	}

	@Override
	public ArrayList<HistoryTransaction> getLast3Transactions(String username) throws UserAccountNotFoundException {
		return  TransactionDAO.getLast3Transactions(username);
	}

	/**
	 * Counts and returns number of {@link DbTransaction}s which are saved in the DB for
	 * {@link UserAccount} with username.
	 * 
	 * @param username
	 *            of UserAccount
	 * @return number of PayInTrasactions
	 * @throws UserAccountNotFoundException
	 */
	public long getHistoryCount(String username) throws UserAccountNotFoundException {
		return TransactionDAO.getHistoryCount(username);
	}

	@Override
	public ServerPaymentResponse createTransaction(ServerPaymentRequest serverPaymentRequest) throws TransactionException, UserAccountNotFoundException {
		//TODO jeton: check timestamp how long is transaction valid!
		
		//TODO jeton: check for duplicate request! user ServerResponseStatus.DUPLICATE_REQUEST
		
		if (serverPaymentRequest == null)
			throw new TransactionException(PAYMENT_REFUSE);

		int numberOfSignatures = serverPaymentRequest.getNofSignatures();
		
		PaymentRequest payerRequest = null;
		PaymentRequest payeeRequest = null;

		payerRequest = serverPaymentRequest.getPaymentRequestPayer();
		payeeRequest = serverPaymentRequest.getPaymentRequestPayee();

		if (Converter.getBigDecimalFromLong(payerRequest.getAmount()).compareTo(BigDecimal.ZERO) <= 0)
			throw new TransactionException(NEGATIVE_AMOUNT);

		String payerUsername = payerRequest.getUsernamePayer();
		String payeeUsername = payerRequest.getUsernamePayee();

		if (payerUsername == payeeUsername)
			throw new TransactionException(PAYMENT_REFUSE);

		UserAccount payerUserAccount = null;
		UserAccount payeeUserAccount = null;
		try {
			payerUserAccount = UserAccountService.getInstance().getByUsername(payerUsername);
			payeeUserAccount = UserAccountService.getInstance().getByUsername(payeeUsername);
		} catch (UserAccountNotFoundException e) {
			throw new TransactionException(PAYMENT_REFUSE);
		}

		try {
			if(! payerRequest.verify(KeyHandler.decodePublicKey(UserPublicKeyDAO.getUserPublicKey(payerUserAccount.getId(), (byte) payerRequest.getKeyNumber()).getPublicKey()))){
				throw new TransactionException(PAYMENT_REFUSE);
			}
		} catch (Exception e) {
			throw new TransactionException(PAYMENT_REFUSE);
		}

		if(numberOfSignatures==2){
			if(! payerRequest.equals(payeeRequest)){
				throw new TransactionException(PAYMENT_REFUSE);
			}
			try {
				if(! payeeRequest.verify(KeyHandler.decodePublicKey(UserPublicKeyDAO.getUserPublicKey(payeeUserAccount.getId(), (byte) payeeRequest.getKeyNumber()).getPublicKey()))){
					throw new TransactionException(PAYMENT_REFUSE);
				}
			} catch (Exception e) {
				throw new TransactionException(PAYMENT_REFUSE);
			}
		}

		if ((payerUserAccount.getBalance().subtract(Converter.getBigDecimalFromLong(payerRequest.getAmount()))).compareTo(BigDecimal.ZERO) < 0)
			throw new TransactionException(BALANCE);

		DbTransaction dbTransaction = null;
		try {
			dbTransaction = new DbTransaction(payerRequest);
			TransactionDAO.createTransaction(dbTransaction, payerUserAccount, payeeUserAccount);
		} catch (HibernateException e) {
			throw new TransactionException(HIBERNATE_ERROR);
		}

		//check if user account balance limit has been exceeded (according to PayOutRules)
		try {
			PayOutRuleService.getInstance().checkBalanceLimitRules(payeeUserAccount);
		} catch (PayOutRuleNotFoundException | BitcoinException e) {
			// do nothing as user requests actually a transaction and not a payout
		}

		ServerPaymentResponse signedResponse = null;
		try {
			PaymentResponse paymentResponsePayer = new PaymentResponse(
					PKIAlgorithm.getPKIAlgorithm(Constants.SERVER_KEY_PAIR.getPkiAlgorithm()),
					Constants.SERVER_KEY_PAIR.getKeyNumber(),
					ServerResponseStatus.SUCCESS,
					null,
					dbTransaction.getUsernamePayer(),
					dbTransaction.getUsernamePayee(),
					Currency.getCurrency(dbTransaction.getCurrency()),
					Converter.getLongFromBigDecimal(dbTransaction.getAmount()),
					dbTransaction.getTimestamp().getTime());
			paymentResponsePayer.sign(KeyHandler.decodePrivateKey(Constants.SERVER_KEY_PAIR.getPrivateKey()));
			signedResponse = new ServerPaymentResponse(paymentResponsePayer);
		} catch (Exception e) {
			throw new TransactionException(INTERNAL_ERROR);
		}

		return signedResponse;
	}

}
