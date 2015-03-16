package ch.uzh.csg.coinblesk.server.controllerui;

import java.math.BigDecimal;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SignatureException;
import java.security.spec.InvalidKeySpecException;

import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import ch.uzh.csg.coinblesk.customserialization.DecoderFactory;
import ch.uzh.csg.coinblesk.customserialization.PKIAlgorithm;
import ch.uzh.csg.coinblesk.customserialization.PaymentRequest;
import ch.uzh.csg.coinblesk.customserialization.PaymentResponse;
import ch.uzh.csg.coinblesk.customserialization.ServerPaymentRequest;
import ch.uzh.csg.coinblesk.customserialization.ServerPaymentResponse;
import ch.uzh.csg.coinblesk.customserialization.ServerResponseStatus;
import ch.uzh.csg.coinblesk.customserialization.exceptions.IllegalArgumentException;
import ch.uzh.csg.coinblesk.customserialization.exceptions.NotSignedException;
import ch.uzh.csg.coinblesk.customserialization.exceptions.SerializationException;
import ch.uzh.csg.coinblesk.customserialization.exceptions.UnknownPKIAlgorithmException;
import ch.uzh.csg.coinblesk.keys.CustomPublicKey;
import ch.uzh.csg.coinblesk.responseobject.TransferObject;
import ch.uzh.csg.coinblesk.server.clientinterface.IActivities;
import ch.uzh.csg.coinblesk.server.clientinterface.IMessages;
import ch.uzh.csg.coinblesk.server.clientinterface.IServerAccount;
import ch.uzh.csg.coinblesk.server.clientinterface.ITransaction;
import ch.uzh.csg.coinblesk.server.clientinterface.IUserAccount;
import ch.uzh.csg.coinblesk.server.dao.ServerPublicKeyDAO;
import ch.uzh.csg.coinblesk.server.domain.Messages;
import ch.uzh.csg.coinblesk.server.domain.ServerAccount;
import ch.uzh.csg.coinblesk.server.security.KeyHandler;
import ch.uzh.csg.coinblesk.server.util.Config;
import ch.uzh.csg.coinblesk.server.util.Constants;
import ch.uzh.csg.coinblesk.server.util.ServerProperties;
import ch.uzh.csg.coinblesk.server.util.Subjects;
import ch.uzh.csg.coinblesk.server.util.exceptions.BalanceNotZeroException;
import ch.uzh.csg.coinblesk.server.util.exceptions.InvalidEmailException;
import ch.uzh.csg.coinblesk.server.util.exceptions.InvalidUrlException;
import ch.uzh.csg.coinblesk.server.util.exceptions.ServerAccountNotFoundException;
import ch.uzh.csg.coinblesk.server.util.exceptions.TransactionException;
import ch.uzh.csg.coinblesk.server.util.exceptions.UrlAlreadyExistsException;
import ch.uzh.csg.coinblesk.server.util.exceptions.UserAccountNotFoundException;
import ch.uzh.csg.coinblesk.server.web.customserialize.CustomServerPaymentRequest;
import ch.uzh.csg.coinblesk.server.web.customserialize.CustomServerPaymentResponse;
import ch.uzh.csg.coinblesk.server.web.customserialize.PayOutAdrressRequest;
import ch.uzh.csg.coinblesk.server.web.customserialize.PayOutAdrressResponse;
import ch.uzh.csg.coinblesk.server.web.response.CreateSAObject;
import ch.uzh.csg.coinblesk.server.web.response.ServerAccountUpdatedObject;
import ch.uzh.csg.coinblesk.server.web.response.TransferPOAObject;
import ch.uzh.csg.coinblesk.server.web.response.TransferServerObject;

import com.azazar.bitcoin.jsonrpcclient.BitcoinException;

@Controller
@RequestMapping("/communication")
public class ServerCommunicationController {

	protected static Logger LOGGER = Logger.getLogger("controller");
	public static final String PAYMENT_REFUSE = "The server refused the payment.";	
	
	@Autowired
	private IServerAccount serverAccountService;
	@Autowired
	private IUserAccount userAccountService;
	@Autowired
	private IActivities activitiesService;
	@Autowired
	private IMessages messagesService;
	@Autowired
	private ITransaction transactionService;
	@Autowired
	private ServerPublicKeyDAO serverPublicKeyDAO;
	
	@RequestMapping(value="/paymentTransaction", method = RequestMethod.POST, consumes = "application/json", produces = "application/json")
	@ResponseBody public TransferServerObject paymentTransaction(@RequestBody TransferServerObject requestObject){
		long start = System.currentTimeMillis();
		TransferServerObject responseObject = new TransferServerObject();
		try {
			//get keys
			CustomPublicKey cpk = new CustomPublicKey(Constants.SERVER_KEY_PAIR.getKeyNumber(), 
					Constants.SERVER_KEY_PAIR.getPkiAlgorithm(), Constants.SERVER_KEY_PAIR.getPublicKey());
			PKIAlgorithm pkiAlgorithm = null;
			try {
				pkiAlgorithm = PKIAlgorithm.getPKIAlgorithm(cpk.getPkiAlgorithm());
			} catch (UnknownPKIAlgorithmException e1) {
				new TransactionException(PAYMENT_REFUSE);
			}
			//initialize payment response
			CustomServerPaymentResponse responseServerPaymentResponse;
			
			CustomServerPaymentRequest customServerPaymentRequest = DecoderFactory.decode(CustomServerPaymentRequest.class, requestObject.getCustomServerPaymentResponse());
			ServerPaymentRequest serverPaymentRequest = DecoderFactory.decode(ServerPaymentRequest.class, customServerPaymentRequest.getServerPaymentRequestRaw());
			
			try {
				ServerPaymentResponse serverPaymentResponse = transactionService.createTransactionOtherServer(serverPaymentRequest, customServerPaymentRequest);
				responseObject.setSuccessful(true);
				
				try {
					responseServerPaymentResponse = new CustomServerPaymentResponse(1, pkiAlgorithm, cpk.getKeyNumber(), serverPaymentResponse);
					responseServerPaymentResponse.sign(KeyHandler.decodePrivateKey(Constants.SERVER_KEY_PAIR.getPrivateKey()));
				} catch (InvalidKeyException | NoSuchAlgorithmException | SignatureException | UnknownPKIAlgorithmException
						| NoSuchProviderException | InvalidKeySpecException | NotSignedException | IllegalArgumentException e3) {
					throw new TransactionException(PAYMENT_REFUSE);
				}
				try {
					responseObject.setCustomServerPaymentResponse(responseServerPaymentResponse.encode());
				} catch (NotSignedException e2) {
					throw new TransactionException(PAYMENT_REFUSE);
				}
				
				long diff = System.currentTimeMillis() - start;
				System.err.println("server time1:"+diff+"ms");
				return responseObject;
			} catch (TransactionException | UserAccountNotFoundException e) {
				PaymentRequest paymentRequestPayer = serverPaymentRequest.getPaymentRequestPayer();
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
				try {
					responseServerPaymentResponse = new CustomServerPaymentResponse(1, pkiAlgorithm, cpk.getKeyNumber(), new ServerPaymentResponse(paymentResponsePayer));
					responseServerPaymentResponse.sign(KeyHandler.decodePrivateKey(Constants.SERVER_KEY_PAIR.getPrivateKey()));
				} catch (InvalidKeyException | NoSuchAlgorithmException | SignatureException | UnknownPKIAlgorithmException
						| NoSuchProviderException | InvalidKeySpecException | NotSignedException | IllegalArgumentException e3) {
					throw new TransactionException(PAYMENT_REFUSE);
				}
				try {
					responseObject.setCustomServerPaymentResponse(responseServerPaymentResponse.encode());
				} catch (NotSignedException e2) {
					throw new TransactionException(PAYMENT_REFUSE);
				}
				long diff = System.currentTimeMillis() - start;
				System.err.println("server time2:"+diff+"ms");
				
			}
		} catch (Throwable e) {
			e.printStackTrace();
			LOGGER.error(e.getMessage());
			responseObject.setSuccessful(false);
			responseObject.setMessage(Constants.INTERNAL_SERVER_ERROR + ": " + e.getMessage());
		}
		long diff = System.currentTimeMillis() - start;
		System.err.println("server time payment Transaction:"+diff+"ms");
		return responseObject;
	}
	
	@RequestMapping(value = "/createNewAccount", method = RequestMethod.POST, consumes="application/json", produces = "application/json")
	@ResponseBody public CreateSAObject createNewAccount(@RequestBody CreateSAObject request) {
		CreateSAObject response = new CreateSAObject();
		PKIAlgorithm pkiAlgorithm;
		ServerAccount account;
		
		try {
			pkiAlgorithm = PKIAlgorithm.getPKIAlgorithm(request.getPkiAlgorithm());

			//check if account exists already
			if(serverAccountService.checkIfExistsByUrl(request.getUrl())){
				if(serverAccountService.isDeletedByUrl(request.getUrl())){
					serverAccountService.undeleteServerAccountByUrl(request.getUrl());
					response.setMessage(Config.ACCOUNT_DELETED);
				} else {
					response.setMessage(Config.ACCOUNT_EXISTS);
				}
			} else {				
				ServerAccount newAccount = new ServerAccount(request.getUrl(),request.getEmail());
				boolean success = serverAccountService.persistAccount(newAccount);
				if(!success){
					response.setMessage("Failed to create Server Account");
					response.setSuccessful(false);
				}
			}
			// retrieve existing account and added public key to the server account
			account = serverAccountService.getByUrl(request.getUrl());
			CustomPublicKey cpkSave = new CustomPublicKey();
			cpkSave.setKeyNumber((byte) request.getKeyNumber());
			cpkSave.setPublicKey(request.getPublicKey());
			cpkSave.setPkiAlgorithm(request.getPkiAlgorithm());
			serverAccountService.saveServerPublicKey(account.getId(), pkiAlgorithm, cpkSave.getPublicKey());
			

			String serverUrl = ServerProperties.getProperty("url.base");
			
			CustomPublicKey cpk = new CustomPublicKey(Constants.SERVER_KEY_PAIR.getKeyNumber(), Constants.SERVER_KEY_PAIR.getPkiAlgorithm(), Constants.SERVER_KEY_PAIR.getPublicKey());
			response.setUrl(serverUrl);
			response.setPkiAlgorithm(cpk.getPkiAlgorithm());
			response.setKeyNumber((byte)cpk.getKeyNumber());
			response.setPublicKey(cpk.getPublicKey());
			response.setMessage(Config.ACCOUNT_SUCCESS);
			response.setSuccessful(true);

			return response;
		} catch (UnknownPKIAlgorithmException e) {
			//ignore
		} catch (UrlAlreadyExistsException e) {
			//ignore
		} catch (BitcoinException e) {
			//ignore
		} catch (UserAccountNotFoundException | ServerAccountNotFoundException e) {
			//ignore
		} catch (InvalidUrlException | InvalidEmailException e) {
			//ignore
		}
		response.setSuccessful(false);
		response.setMessage(Config.FAILED);
		return response;	
		
	}

	@RequestMapping(value = "/createNewAccountData", method = RequestMethod.POST, produces = "application/json")
	public @ResponseBody TransferPOAObject createNewAccountData(@RequestBody TransferPOAObject request){
		TransferPOAObject response = new TransferPOAObject();
		ServerAccount account;
		try {
			
			PayOutAdrressRequest paarRequest = DecoderFactory.decode(PayOutAdrressRequest.class, request.getPayOutAddress());
			//check if account exists already
			if(serverAccountService.checkIfExistsByUrl(request.getUrl())){
				if(!serverAccountService.isDeletedByUrl(request.getUrl())){
					account = serverAccountService.getByUrl(request.getUrl());
					if(!paarRequest.verify(KeyHandler.decodePublicKey(serverPublicKeyDAO.getServerPublicKey(account.getId(), (byte) paarRequest.getKeyNumber()).getPublicKey()))){
						response.setSuccessful(false);
						response.setMessage(Config.ACCOUNT_FAILED);
						return response;
					}
						
					account.setPayoutAddress(paarRequest.getPayOutAddressRequest());
					boolean success = serverAccountService.updatePayoutAddressAccount(request.getUrl(), account);
					if(success){
						//get keys
						CustomPublicKey cpk = new CustomPublicKey(Constants.SERVER_KEY_PAIR.getKeyNumber(), 
								Constants.SERVER_KEY_PAIR.getPkiAlgorithm(), Constants.SERVER_KEY_PAIR.getPublicKey());
						PKIAlgorithm pkiAlgorithm;
						try {
							pkiAlgorithm = PKIAlgorithm.getPKIAlgorithm(cpk.getPkiAlgorithm());
						} catch (UnknownPKIAlgorithmException e1) {
							response.setSuccessful(false);
							response.setMessage(Config.ACCOUNT_FAILED);
							return response;
						}
						PayOutAdrressResponse paarResponse = new PayOutAdrressResponse(1, pkiAlgorithm,(byte) cpk.getKeyNumber(), account.getPayinAddress());
						paarResponse.sign(KeyHandler.decodePrivateKey(Constants.SERVER_KEY_PAIR.getPrivateKey()));
						response.setPayOutAddress(paarResponse.encode());
						response.setUrl(ServerProperties.getProperty("url.base"));
						response.setSuccessful(true);
						response.setMessage(Config.ACCOUNT_SUCCESS);
						return response;
					}
				} 		
			} 
		} 
		catch (ServerAccountNotFoundException e) {
			//ignore
		} catch (IllegalArgumentException e) {
			//ignore
		} catch (NotSignedException e) {
			//ignore
		}  catch (UnknownPKIAlgorithmException e) {
			//ignore
		} catch (InvalidKeyException | InvalidKeySpecException e) {
			//ignore
		} catch (NoSuchAlgorithmException | NoSuchProviderException e) {
			//ignore
		} catch (SignatureException | SerializationException e) {
			//ignore
		}
		response.setSuccessful(false);
		response.setMessage(Config.FAILED);
		return response;	
	}
	
	@RequestMapping(value = "/downgradeTrustLevel", method = RequestMethod.POST,consumes="application/json", produces = "application/json")
	@ResponseBody public TransferObject downgradeTrustLevel(@RequestBody ServerAccountUpdatedObject request) throws ServerAccountNotFoundException{
		TransferObject response = new TransferObject();
		try{
			ServerAccount account = serverAccountService.getByUrl(request.getUrl());
			if(account.getTrustLevel() > request.getTrustLevel()){
				ServerAccount updated = new ServerAccount();
				updated.setTrustLevel(request.getTrustLevel());
				boolean success = serverAccountService.updateAccount(request.getUrl(), updated);
				if(success){
					activitiesService.activityLog(Config.NOT_AVAILABLE, Subjects.DOWNGRADE_TRUST_LEVEL, "Trust level of Server account "+ request.getUrl() + " is downgraded to " + request.getTrustLevel());
					response.setSuccessful(true);
					response.setMessage(Config.DOWNGRADE_SUCCEEDED);
					return response;
				}
			}
		} catch (ServerAccountNotFoundException e) {
			//ignore
		}
		response.setSuccessful(false);
		response.setMessage(Config.FAILED);
		return response;
	}
	
	@RequestMapping(value = "/upgradeTrustLevel", method = RequestMethod.POST, consumes="application/json", produces = "application/json")
	@ResponseBody public TransferObject upgradeTrustLevel(@RequestBody ServerAccountUpdatedObject request) throws ServerAccountNotFoundException{
		TransferObject response = new TransferObject();
		try{
			ServerAccount account = serverAccountService.getByUrl(request.getUrl());
			if(account.getTrustLevel() < request.getTrustLevel()){
				String messageInput = "Upgrade trust level to " + request.getTrustLevel();
				Messages message = new Messages(Subjects.UPGRADE_TRUST_LEVEL, messageInput, request.getUrl());
				message.setTrustLevel(request.getTrustLevel());
				boolean exists = messagesService.exits(Subjects.UPGRADE_TRUST_LEVEL, request.getUrl());
				if(!exists){					
					boolean success = messagesService.createMessage(message);
					if(success){						
						response.setSuccessful(true);
						response.setMessage(Config.SUCCESS);
						return response;
					}
				}
			}
		} catch (ServerAccountNotFoundException e) {
			//ignore
		}
		response.setSuccessful(false);
		response.setMessage("Failed to upgrade");
		return response;
	}	
	
	@RequestMapping(value ="/deletedAccount", method = RequestMethod.POST, consumes="application/json")
	public @ResponseBody TransferObject deleteAccount(@RequestBody ServerAccountUpdatedObject request){
		TransferObject response = new TransferObject();
		try{
			if(!serverAccountService.isDeletedByUrl(request.getUrl())){
				ServerAccount account = serverAccountService.getByUrl(request.getUrl());
				if(account.getTrustLevel() == 0 && account.getActiveBalance().compareTo(BigDecimal.ZERO) == 0){					
					boolean success = serverAccountService.deleteAccount(request.getUrl());
					if(success){					
						response.setSuccessful(true);
						response.setMessage("Succeeded to deleted!");
						activitiesService.activityLog(Config.NOT_AVAILABLE, Subjects.DELETE_ACCOUNT, "Server account "+ request.getUrl()+ " is deleted");
						return response;
					}
				}
			}
		} catch (ServerAccountNotFoundException e) {
			//ignore
		} catch (BalanceNotZeroException e) {
			//ignore
		}
		response.setSuccessful(false);
		response.setMessage(Config.FAILED);
		return response;
	}

	@RequestMapping(value = "/updateAccepted", method = RequestMethod.POST, consumes="application/json", produces = "application/json")
	public @ResponseBody TransferObject upgradeAccepted(@RequestBody ServerAccountUpdatedObject request) throws ServerAccountNotFoundException{
		TransferObject response = new TransferObject();
		try{
			serverAccountService.getByUrl(request.getUrl());
		} catch (ServerAccountNotFoundException e) {
			response.setSuccessful(false);
			response.setMessage("Server Account does not exists!");
			return response;
		}
		ServerAccount account = new ServerAccount();
		account.setTrustLevel(request.getTrustLevel());
		
		serverAccountService.updateAccount(request.getUrl(), account);
		activitiesService.activityLog(Config.NOT_AVAILABLE, Subjects.UPGRADE_TRUST_LEVEL, "Trust level of url: " + request.getUrl() + " is updated to " + request.getTrustLevel());
		response.setSuccessful(true);
		response.setMessage("Update trust level to " + request.getTrustLevel());
		return response;
	}
	
	@RequestMapping(value = "/updateDeclined", method = RequestMethod.POST, consumes="application/json", produces = "application/json")
	public @ResponseBody TransferObject upgradeDeclined(@RequestBody ServerAccountUpdatedObject request) throws ServerAccountNotFoundException{
		TransferObject response = new TransferObject();
		try{
			serverAccountService.getByUrl(request.getUrl());
		} catch (ServerAccountNotFoundException e) {
			response.setSuccessful(false);
			response.setMessage("Server Account does not exists!");
			return response;
		}
		activitiesService.activityLog(Config.NOT_AVAILABLE, Subjects.UPGRADE_TRUST_LEVEL, "Trust level of url: " + request.getUrl() + " is declined to updated to level " + request.getTrustLevel());
		response.setSuccessful(true);
		response.setMessage("Update trust level to " + request.getTrustLevel());
		return response;
	}
}