package ch.uzh.csg.mbps.server.service;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;

import net.minidev.json.JSONObject;
import net.minidev.json.parser.ParseException;

import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.util.EntityUtils;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ch.uzh.csg.mbps.customserialization.Currency;
import ch.uzh.csg.mbps.customserialization.DecoderFactory;
import ch.uzh.csg.mbps.customserialization.PKIAlgorithm;
import ch.uzh.csg.mbps.customserialization.PaymentRequest;
import ch.uzh.csg.mbps.customserialization.PaymentResponse;
import ch.uzh.csg.mbps.customserialization.ServerPaymentRequest;
import ch.uzh.csg.mbps.customserialization.ServerPaymentResponse;
import ch.uzh.csg.mbps.customserialization.ServerResponseStatus;
import ch.uzh.csg.mbps.customserialization.exceptions.NotSignedException;
import ch.uzh.csg.mbps.model.HistoryTransaction;
import ch.uzh.csg.mbps.responseobject.TransactionObject;
import ch.uzh.csg.mbps.server.clientinterface.IPayOutRule;
import ch.uzh.csg.mbps.server.clientinterface.IServerAccount;
import ch.uzh.csg.mbps.server.clientinterface.IServerTransaction;
import ch.uzh.csg.mbps.server.clientinterface.ITransaction;
import ch.uzh.csg.mbps.server.clientinterface.IUserAccount;
import ch.uzh.csg.mbps.server.dao.TransactionDAO;
import ch.uzh.csg.mbps.server.dao.UserPublicKeyDAO;
import ch.uzh.csg.mbps.server.domain.DbTransaction;
import ch.uzh.csg.mbps.server.domain.ServerAccount;
import ch.uzh.csg.mbps.server.domain.UserAccount;
import ch.uzh.csg.mbps.server.response.HttpRequestHandler;
import ch.uzh.csg.mbps.server.security.KeyHandler;
import ch.uzh.csg.mbps.server.util.BitstampController;
import ch.uzh.csg.mbps.server.util.Config;
import ch.uzh.csg.mbps.server.util.Constants;
import ch.uzh.csg.mbps.server.util.Emailer;
import ch.uzh.csg.mbps.server.util.SplitNameHandler;
import ch.uzh.csg.mbps.server.util.exceptions.PayOutRuleNotFoundException;
import ch.uzh.csg.mbps.server.util.exceptions.ServerAccountNotFoundException;
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

	public static final String BALANCE = "BALANCE";
	public static final String NEGATIVE_AMOUNT = "The transaction amount can't be negative or equals 0.";
	public static final String INTERNAL_ERROR = "An internal error occured. Please try again later.";
	public static final String PAYMENT_REFUSE = "The server refused the payment.";
	public static final String PAYMENT_REFUSE_USER_LIMIT = "The server refused the payment the user balance limit is exceeded.";
	public static final String PAYMENT_REFUSE_TRUST = "The server refused the payment because of the trust relation.";
	public static final String NEGATIVE_BALANCE_LIMIT = "Transactions cannot be greater than the balance limit";
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
	@Autowired
	private IServerAccount serverAccountService;
	@Autowired
	private IServerTransaction serverTransactionService;

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
		ServerAccount payerServerAccount = null;
		ServerAccount payeeServerAccount = null;
		boolean internalTransaction = true;
		try {
			internalTransaction = false;
			payerUserAccount = userAccountService.getByUsername(payerUsername);
		} catch (UserAccountNotFoundException e) {
			// if useraccount not found check if the user is from another server which has a trust relation
			String payerUrl = SplitNameHandler.getInstance().getServerUrl(payerUsername);
			try {
				payerServerAccount = serverAccountService.getByUrl(payerUrl);
			} catch (ServerAccountNotFoundException e1) {
				throw new TransactionException(PAYMENT_REFUSE);
			}
			if(payerServerAccount.getTrustLevel() == 0)
				throw new TransactionException(PAYMENT_REFUSE_TRUST);
		}
		try{			
			payeeUserAccount = userAccountService.getByUsername(payeeUsername);
		} catch (UserAccountNotFoundException e) {			
			internalTransaction = false;
			// if useraccount not found check if the user is from another server which has a trust relation
			String payeeUrl = SplitNameHandler.getInstance().getServerUrl(payeeUsername);
			try {
				payeeServerAccount = serverAccountService.getByUrl(payeeUrl);
			} catch (ServerAccountNotFoundException e1) {
				throw new TransactionException(PAYMENT_REFUSE);
			}
			if(payeeServerAccount.getTrustLevel() == 0)
				throw new TransactionException(PAYMENT_REFUSE_TRUST);
		}
		
		//This serverpaymentRequest is for comparison
		PaymentResponse spResponse = null;
		//When the transaction is between two different servers with at least Hyprid trust
		if(!internalTransaction){
			long start = System.currentTimeMillis();
			boolean success = false;

			if(payerServerAccount != null)
				success = serverAccountService.verifyTrustAndBalance(payerServerAccount, Converter.getBigDecimalFromLong(payerRequest.getAmount()));
			if(payeeServerAccount != null)
				success = serverAccountService.verifyTrustAndBalance(payeeServerAccount, Converter.getBigDecimalFromLong(payerRequest.getAmount()));			
			
			if(!success)
				throw new TransactionException(NEGATIVE_BALANCE_LIMIT);
			
			//Get url of the other server
			String serverUrl = getServerAccountName(payerServerAccount, payeeServerAccount); 
			//check if the user has exceeded the user balance limit
			if(isUserBalanceLimitIsExceeded(serverUrl, authenticatedUser, Converter.getBigDecimalFromLong(payerRequest.getAmount()))){
				throw new TransactionException(PAYMENT_REFUSE_USER_LIMIT);
			}
			
			TransactionObject tro = new TransactionObject();
			try {
	            tro.setServerPaymentResponse(serverPaymentRequest.encode());
            } catch (NotSignedException e) {
            	throw new TransactionException(PAYMENT_REFUSE);
            }
			
			JSONObject jsonObject = new JSONObject();
			try {
				tro.encode(jsonObject);
			} catch (Exception e1) {
				throw new TransactionException(PAYMENT_REFUSE);
			}
			
			// send payer and payee request to the other server and make checks
			CloseableHttpResponse responseBody;
			String paymentUrl = serverUrl + Config.SERVER_PAYMENT_TRANSACTION;
			TransactionObject troResponse = new TransactionObject();
			try {
				//execute post request
				responseBody = HttpRequestHandler.prepPostResponse(jsonObject, paymentUrl);
				try {
					HttpEntity entity1 = responseBody.getEntity();
					String responseString = EntityUtils.toString(entity1);
					if (responseString != null && responseString.trim().length() > 0) {
						troResponse.decode(responseString);
					} 
				} catch (Exception e) {
					throw new TransactionException(PAYMENT_REFUSE);
				} finally {
					responseBody.close();
				}				
			} catch (IOException e) {
				throw new TransactionException(PAYMENT_REFUSE);
			}
			
			try {
				spResponse = DecoderFactory.decode(ServerPaymentResponse.class, troResponse.getServerPaymentResponse());
			} catch (Throwable e) {
				e.printStackTrace();
				LOGGER.error(e.getMessage());
				throw new TransactionException(PAYMENT_REFUSE);				
			}
			
			if(!spResponse.getStatus().equals(ServerResponseStatus.SUCCESS)){
				throw new TransactionException(PAYMENT_REFUSE);
			}
			
			if(!(Converter.getBigDecimalFromLong(payerRequest.getAmount()).compareTo(Converter.getBigDecimalFromLong(spResponse.getAmount())) == 0 
					&& payerRequest.getTimestamp() == spResponse.getTimestamp() && payerRequest.getUsernamePayee().equals(spResponse.getUsernamePayee())
					&& payerRequest.getUsernamePayer().equals(spResponse.getUsernamePayer()))){
				throw new TransactionException(PAYMENT_REFUSE);
			}
			
			long diff = System.currentTimeMillis() - start;
			System.err.println("server time other server:"+diff+"ms");
		}
		
		
		if(payerServerAccount == null){				
			try {
				if (!payerRequest.verify(KeyHandler.decodePublicKey(userPublicKeyDAO.getUserPublicKey(payerUserAccount.getId(), (byte) payerRequest.getKeyNumber()).getPublicKey()))) {
					throw new TransactionException(PAYMENT_REFUSE);
				}
			} catch (Exception e) {
				throw new TransactionException(PAYMENT_REFUSE);
			}
		}		
		
		if (numberOfSignatures == 2) {
			if (!payerRequest.requestsIdentic(payeeRequest)) {
				throw new TransactionException(PAYMENT_REFUSE);
			}
			if(payeeServerAccount == null){					
				try {
					if (!payeeRequest.verify(KeyHandler.decodePublicKey(userPublicKeyDAO.getUserPublicKey(payeeUserAccount.getId(), (byte) payeeRequest.getKeyNumber()).getPublicKey()))) {
						throw new TransactionException(PAYMENT_REFUSE);
					}
				} catch (Exception e) {
					throw new TransactionException(PAYMENT_REFUSE);
				}
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
		
		if(internalTransaction){			
			transactionDAO.createTransaction(dbTransaction, payerUserAccount, payeeUserAccount);
		} else {			
			if(payeeUserAccount == null){
				transactionDAO.createTransaction(dbTransaction, payerUserAccount, false);			
				serverAccountService.persistsTransactionAmount(payerServerAccount, dbTransaction, false);
			} else {
				transactionDAO.createTransaction(dbTransaction, payeeUserAccount, true);
				serverAccountService.persistsTransactionAmount(payeeServerAccount, dbTransaction, true);			
			}
		}
		

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

	@Override
	@Transactional
	public ServerPaymentResponse createTransactionOtherServer(ServerPaymentRequest serverPaymentRequest) throws TransactionException, UserAccountNotFoundException {
		
		int numberOfSignatures = serverPaymentRequest.getNofSignatures();
		PaymentRequest payerRequest = serverPaymentRequest.getPaymentRequestPayer();
		PaymentRequest payeeRequest = serverPaymentRequest.getPaymentRequestPayee();
		
		if (Converter.getBigDecimalFromLong(payerRequest.getAmount()).compareTo(BigDecimal.ZERO) <= 0)
			throw new TransactionException(NEGATIVE_AMOUNT);
		
		String payerUsername = payerRequest.getUsernamePayer();
		String payeeUsername = payerRequest.getUsernamePayee();
		
		if (payerUsername.equals(payeeUsername))
			throw new TransactionException(PAYMENT_REFUSE);
		
		String fromServerUsername = null;
		UserAccount payerUserAccount = null;
		UserAccount payeeUserAccount = null;
		ServerAccount serverAccount = null;
		try {
			payerUserAccount = userAccountService.getByUsername(payerUsername);
			fromServerUsername = payerUserAccount.getUsername();
		} catch (UserAccountNotFoundException e) {
			// if useraccount not found check for the other server
			String payerUrl = SplitNameHandler.getInstance().getServerUrl(payerUsername);
			try {
				serverAccount = serverAccountService.getByUrl(payerUrl);
			} catch (ServerAccountNotFoundException e1) {
				throw new TransactionException(PAYMENT_REFUSE);
			}
		}
		try{			
			payeeUserAccount = userAccountService.getByUsername(payeeUsername);
			fromServerUsername = payeeUserAccount.getUsername();
		} catch (UserAccountNotFoundException e) {			
			// if useraccount not found check for the other server
			String payeeUrl = SplitNameHandler.getInstance().getServerUrl(payeeUsername);
			try {
				serverAccount = serverAccountService.getByUrl(payeeUrl);
			} catch (ServerAccountNotFoundException e1) {
				throw new TransactionException(PAYMENT_REFUSE);
			}
		}

		if(serverAccount.getTrustLevel() == 0)
			throw new TransactionException(PAYMENT_REFUSE_TRUST);
		
		boolean success = serverAccountService.verifyTrustAndBalance(serverAccount, Converter.getBigDecimalFromLong(payerRequest.getAmount()));
		if(!success)
			throw new TransactionException(NEGATIVE_BALANCE_LIMIT);
		
		//Get url of the other server
		String serverUrl = serverAccount.getUrl();
		//check if the user has exceeded the user balance limit
		if(isUserBalanceLimitIsExceeded(serverUrl, fromServerUsername, Converter.getBigDecimalFromLong(payerRequest.getAmount()))){
			throw new TransactionException(PAYMENT_REFUSE_USER_LIMIT);
		}
		
		if(fromServerUsername == payerRequest.getUsernamePayer()){				
			try {
				if (!payerRequest.verify(KeyHandler.decodePublicKey(userPublicKeyDAO.getUserPublicKey(payerUserAccount.getId(), (byte) payerRequest.getKeyNumber()).getPublicKey()))) {
					throw new TransactionException(PAYMENT_REFUSE);
				}
			} catch (Exception e) {
				throw new TransactionException(PAYMENT_REFUSE);
			}
		}		
		
		if (numberOfSignatures == 2) {
			if (!payerRequest.requestsIdentic(payeeRequest)) {
				throw new TransactionException(PAYMENT_REFUSE);
			}
			if(fromServerUsername == payeeRequest.getUsernamePayee()){					
				try {
					if (!payeeRequest.verify(KeyHandler.decodePublicKey(userPublicKeyDAO.getUserPublicKey(payeeUserAccount.getId(), (byte) payeeRequest.getKeyNumber()).getPublicKey()))) {
						throw new TransactionException(PAYMENT_REFUSE);
					}
				} catch (Exception e) {
					throw new TransactionException(PAYMENT_REFUSE);
				}
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
		if(payeeUserAccount == null){
			transactionDAO.createTransaction(dbTransaction, payerUserAccount, false);			
			serverAccountService.persistsTransactionAmount(serverAccount, dbTransaction, false);
		} else {
			transactionDAO.createTransaction(dbTransaction, payeeUserAccount, true);
			serverAccountService.persistsTransactionAmount(serverAccount, dbTransaction, true);			
		}
		//check if user account balance limit has been exceeded (according to PayOutRules)
		if(fromServerUsername == payeeRequest.getUsernamePayee()){			
			try {
				payOutRuleService.checkBalanceLimitRules(payeeUserAccount);
			} catch (PayOutRuleNotFoundException | BitcoinException e) {
				// do nothing as user requests actually a transaction and not a payout
			}
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

	private String getServerAccountName(ServerAccount payerServerAccount, ServerAccount payeeServerAccount) {
		if(payerServerAccount != null){
			return payerServerAccount.getUrl();
		}else {
			return payeeServerAccount.getUrl();
		}
	}

	private boolean isUserBalanceLimitIsExceeded(String serverUrl, String username, BigDecimal amount) throws TransactionException {
		try {
			ServerAccount account = serverAccountService.getByUrl(serverUrl);
			BigDecimal payerAmount = transactionDAO.getCurrentBalanceTransactionAsPayer(serverUrl, username);
			BigDecimal payeeAmount = transactionDAO.getCurrentBalanceTransactionAsPayee(serverUrl, username);
			BigDecimal currentAmount = payeeAmount.subtract(payerAmount);
			if(account.getUserBalanceLimit().compareTo((currentAmount.add(amount))) > 0)
				return false;
		} catch (ServerAccountNotFoundException e) {
			throw new TransactionException(PAYMENT_REFUSE);
		}
		return true;
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
