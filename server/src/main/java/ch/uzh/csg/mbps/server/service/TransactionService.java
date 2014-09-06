package ch.uzh.csg.mbps.server.service;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;

import net.minidev.json.parser.ParseException;

import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ch.uzh.csg.mbps.customserialization.Currency;
import ch.uzh.csg.mbps.customserialization.PKIAlgorithm;
import ch.uzh.csg.mbps.customserialization.PaymentRequest;
import ch.uzh.csg.mbps.customserialization.PaymentResponse;
import ch.uzh.csg.mbps.customserialization.ServerPaymentRequest;
import ch.uzh.csg.mbps.customserialization.ServerPaymentResponse;
import ch.uzh.csg.mbps.customserialization.ServerResponseStatus;
import ch.uzh.csg.mbps.model.HistoryTransaction;
import ch.uzh.csg.mbps.server.clientinterface.IPayOutRule;
import ch.uzh.csg.mbps.server.clientinterface.ITransaction;
import ch.uzh.csg.mbps.server.clientinterface.IUserAccount;
import ch.uzh.csg.mbps.server.dao.TransactionDAO;
import ch.uzh.csg.mbps.server.dao.UserPublicKeyDAO;
import ch.uzh.csg.mbps.server.domain.DbTransaction;
import ch.uzh.csg.mbps.server.domain.UserAccount;
import ch.uzh.csg.mbps.server.security.KeyHandler;
import ch.uzh.csg.mbps.server.util.BitstampController;
import ch.uzh.csg.mbps.server.util.Constants;
import ch.uzh.csg.mbps.server.util.Emailer;
import ch.uzh.csg.mbps.server.util.exceptions.PayOutRuleNotFoundException;
import ch.uzh.csg.mbps.server.util.exceptions.TransactionException;
import ch.uzh.csg.mbps.server.util.exceptions.UserAccountNotFoundException;
import ch.uzh.csg.mbps.util.Converter;

import com.azazar.bitcoin.jsonrpcclient.BitcoinException;
import com.xeiam.xchange.ExchangeException;
import com.xeiam.xchange.NotAvailableFromExchangeException;
import com.xeiam.xchange.NotYetImplementedForExchangeException;

/**
 * Service class for {@link DbTransaction} between two {@link UserAccount}s.
 *
 */
@Service
public class TransactionService implements ITransaction {

	//TODO: mehmet move to a config file
	public static final String BALANCE = "BALANCE";
	public static final String NEGATIVE_AMOUNT = "The transaction amount can't be negative or equals 0.";
	public static final String INTERNAL_ERROR = "An internal error occured. Please try again later.";
	public static final String PAYMENT_REFUSE = "The server refused the payment.";
	public static final String NOT_AUTHENTICATED_USER = "Only the authenticated user can act as the payer in the payment.";

	private static Logger LOGGER = Logger.getLogger(TransactionService.class);

	@Autowired 
	private TransactionDAO transactionDAO;
	@Autowired 
	private UserPublicKeyDAO userPublicKeyDAO;

	@Autowired
	private IPayOutRule payOutRuleService;

	@Autowired
	private IUserAccount userAccountService;

	@Override
	@Transactional(readOnly = true)
	public List<HistoryTransaction> getHistory(String username, int page) throws UserAccountNotFoundException {
		UserAccount user = userAccountService.getByUsername(username);
		return transactionDAO.getHistory(user, page);
	}

	@Override
	@Transactional(readOnly = true)
	public List<HistoryTransaction> getLast5Transactions(String username) throws UserAccountNotFoundException {
		UserAccount user = userAccountService.getByUsername(username);
		return  transactionDAO.getLast5Transactions(user);
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
	@Transactional(readOnly = true)
	public long getHistoryCount(String username) throws UserAccountNotFoundException {
		UserAccount user = userAccountService.getByUsername(username);
		return transactionDAO.getHistoryCount(user);
	}

	@Override
	@Transactional
	public ServerPaymentResponse createTransaction(String authenticatedUser, ServerPaymentRequest serverPaymentRequest) throws TransactionException, UserAccountNotFoundException {
		if (authenticatedUser == null || authenticatedUser.isEmpty() || serverPaymentRequest == null)
			throw new TransactionException(PAYMENT_REFUSE);

		int numberOfSignatures = serverPaymentRequest.getNofSignatures();
		PaymentRequest payerRequest = serverPaymentRequest.getPaymentRequestPayer();
		PaymentRequest payeeRequest = serverPaymentRequest.getPaymentRequestPayee();

		if (Converter.getBigDecimalFromLong(payerRequest.getAmount()).compareTo(BigDecimal.ZERO) <= 0)
			throw new TransactionException(NEGATIVE_AMOUNT);

		String payerUsername = payerRequest.getUsernamePayer();
		String payeeUsername = payerRequest.getUsernamePayee();

		if (payerUsername.equals(payeeUsername))
			throw new TransactionException(PAYMENT_REFUSE);

		/*
		 * Assure that only the authenticated user can act as the payer!
		 * Otherwise, the send money use-case is vulnerable to send money to
		 * himself from another account!
		 */
		if (numberOfSignatures == 1 && !payerUsername.equals(authenticatedUser))
			throw new TransactionException(NOT_AUTHENTICATED_USER);

		UserAccount payerUserAccount = null;
		UserAccount payeeUserAccount = null;
		try {
			payerUserAccount = userAccountService.getByUsername(payerUsername);
			payeeUserAccount = userAccountService.getByUsername(payeeUsername);
		} catch (UserAccountNotFoundException e) {
			throw new TransactionException(PAYMENT_REFUSE);
		}

		try {
			if (!payerRequest.verify(KeyHandler.decodePublicKey(userPublicKeyDAO.getUserPublicKey(payerUserAccount.getId(), (byte) payerRequest.getKeyNumber()).getPublicKey()))) {
				throw new TransactionException(PAYMENT_REFUSE);
			}
		} catch (Exception e) {
			throw new TransactionException(PAYMENT_REFUSE);
		}

		if (numberOfSignatures == 2) {
			if (!payerRequest.requestsIdentic(payeeRequest)) {
				throw new TransactionException(PAYMENT_REFUSE);
			}
			try {
				if (!payeeRequest.verify(KeyHandler.decodePublicKey(userPublicKeyDAO.getUserPublicKey(payeeUserAccount.getId(), (byte) payeeRequest.getKeyNumber()).getPublicKey()))) {
					throw new TransactionException(PAYMENT_REFUSE);
				}
			} catch (Exception e) {
				throw new TransactionException(PAYMENT_REFUSE);
			}
		}

		if ((payerUserAccount.getBalance().subtract(Converter.getBigDecimalFromLong(payerRequest.getAmount()))).compareTo(BigDecimal.ZERO) < 0)
			throw new TransactionException(BALANCE);

		if (transactionDAO.exists(payerRequest.getUsernamePayer(), payerRequest.getUsernamePayee(), payerRequest.getCurrency(), payerRequest.getAmount(), payerRequest.getTimestamp())) {
			try {
				PaymentResponse paymentResponsePayer = new PaymentResponse(
						PKIAlgorithm.getPKIAlgorithm(Constants.SERVER_KEY_PAIR.getPkiAlgorithm()),
						Constants.SERVER_KEY_PAIR.getKeyNumber(),
						ServerResponseStatus.DUPLICATE_REQUEST,
						null,
						payerRequest.getUsernamePayer(),
						payerRequest.getUsernamePayee(),
						payerRequest.getCurrency(),
						payerRequest.getAmount(),
						payerRequest.getTimestamp());
				paymentResponsePayer.sign(KeyHandler.decodePrivateKey(Constants.SERVER_KEY_PAIR.getPrivateKey()));
				return new ServerPaymentResponse(paymentResponsePayer);
			} catch (Exception e) {
				System.err.println(e);
				e.printStackTrace();
				throw new TransactionException(INTERNAL_ERROR);
			}
		}

		DbTransaction dbTransaction = new DbTransaction(payerRequest);
		if (numberOfSignatures == 2) {
			if (payeeRequest.getInputCurrency() != null) {
				dbTransaction.setInputCurrency(payeeRequest.getInputCurrency().getCurrencyCode());
				dbTransaction.setInputCurrencyAmount(Converter.getBigDecimalFromLong(payeeRequest.getInputAmount()));
			}
		}
		transactionDAO.createTransaction(dbTransaction, payerUserAccount, payeeUserAccount);
		checkForMensaOrExchangePointTransactions(dbTransaction, payerUserAccount, payeeUserAccount);
		

		//check if user account balance limit has been exceeded (according to PayOutRules)
		try {
			payOutRuleService.checkBalanceLimitRules(payeeUserAccount);
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

	private static BigDecimal openSellOrders = BigDecimal.ZERO;
	private static BigDecimal openBuyOrders = BigDecimal.ZERO;

	//TODO: for Mensa Test Run only, delete afterwards
	//TODO simon: test
	private void checkForMensaOrExchangePointTransactions(DbTransaction dbTransaction, UserAccount payerUserAccount, UserAccount payeeUserAccount) {
		BigDecimal amount = dbTransaction.getAmount();
		String transactionID = "";

		if (payerUserAccount.getUsername().equals("ExchangePoint") || payerUserAccount.getUsername().equals("MensaBinz")) {
			BigDecimal totalAmountBTC = amount.add(openBuyOrders);
			BigDecimal totalAmountUSD = BigDecimal.ZERO;
			try {
				totalAmountUSD = totalAmountBTC.multiply(BitstampController.getExchangeRate());
			} catch (ExchangeException | NotAvailableFromExchangeException
					| NotYetImplementedForExchangeException | IOException e1) {
				LOGGER.error("Bitstamp Transaction Error: Couldn't get Bitstamp ExchangeRate.");
			}

			if (totalAmountUSD.compareTo(new BigDecimal(5)) == 1) {
				try {
					transactionID = BitstampController.buyBTC(totalAmountBTC);
					LOGGER.info("Bitstamp Transaction Successful: A Limitorder to buy " + totalAmountBTC + " BTC has been placed on Bitstamp with ID: " + transactionID);
					synchronized (openBuyOrders) {
						openBuyOrders = BigDecimal.ZERO;
					}
				} catch (ExchangeException | NotAvailableFromExchangeException
						| NotYetImplementedForExchangeException | IOException| ParseException e) {
					LOGGER.error("Bitstamp Transaction Error: failed to do buyBTC limit order (ID: " + transactionID + "): " + e.getMessage() + " Transaction Details: " + dbTransaction.toString());
					Emailer.send("simon.kaeser@uzh.ch", "Bitstamp Transaction Error", "Bitstamp Transaction Error: failed to do buyBTC limit order: " + e.getMessage() + " Transaction Details: " + dbTransaction.toString());
					synchronized (openBuyOrders) {
						openBuyOrders = openBuyOrders.add(amount);	                    
                    }
					
				}
			} else {
				synchronized (openBuyOrders) {
					openBuyOrders = openBuyOrders.add(amount);	                    
                }
			}
		}

		if (payeeUserAccount.getUsername().equals("MensaBinz") || payeeUserAccount.getUsername().equals("ExchangePoint")) {
			BigDecimal totalAmountBTC = amount.add(openSellOrders);
			BigDecimal totalAmountUSD = BigDecimal.ZERO;
			try {
				totalAmountUSD = totalAmountBTC.multiply(BitstampController.getExchangeRate());
			} catch (ExchangeException | NotAvailableFromExchangeException
					| NotYetImplementedForExchangeException | IOException e1) {
				LOGGER.error("Bitstamp Transaction Error: Couldn't get Bitstamp ExchangeRate.");
			}

			if (totalAmountUSD.compareTo(new BigDecimal(5)) == 1) {
				try {
					transactionID = BitstampController.sellBTC(totalAmountBTC);
					LOGGER.info("Bitstamp Transaction Successful: A Limitorder to sell " + totalAmountBTC + " BTC has been placed on Bitstamp with ID: " + transactionID);
					synchronized (openSellOrders) {
						openSellOrders = BigDecimal.ZERO;
					}
				} catch (ExchangeException | NotAvailableFromExchangeException
						| NotYetImplementedForExchangeException | IOException e) {
					LOGGER.error("Bitstamp Transaction Error: failed to do sellBTC limit order (ID: " + transactionID + "): " + e.getMessage() + " Transaction Details: " + dbTransaction.toString());
					Emailer.send("simon.kaeser@uzh.ch", "Bitstamp Transaction Error", "Bitstamp Transaction Error: failed to do sellBTC limit order: " + e.getMessage() + " Transaction Details: " + dbTransaction.toString());
					synchronized (openSellOrders) {
						openSellOrders = openSellOrders.add(amount);
					}
				}
			} else {
				synchronized (openSellOrders) {
					openSellOrders = openSellOrders.add(amount);
				}
			}
		}
	}

	@Transactional(readOnly = true)
	public List<HistoryTransaction> getAll() {
		return transactionDAO.getAll();
	}

	@Transactional
	public void createTransaction(DbTransaction tx, UserAccount fromDB, UserAccount fromDB2) {
		transactionDAO.createTransaction(tx, fromDB, fromDB2);

	}

	@Override
	@Transactional(readOnly = true)
    public List<HistoryTransaction> getAll(String username) throws UserAccountNotFoundException {
		UserAccount user = userAccountService.getByUsername(username);
		return transactionDAO.getAll(user);
    }
	
	@Override
	@Transactional(readOnly=true)
	public BigDecimal transactionSumByServerAsPayer(String url, String username){
		return transactionDAO.transactionSumByServerAsPayer(url, username);
	}

	@Override
	@Transactional(readOnly=true)
	public	BigDecimal transactionSumByServerAsPayee(String url, String username){
		return transactionDAO.transactionSumByServerAsPayee(url, username);
	}

}
