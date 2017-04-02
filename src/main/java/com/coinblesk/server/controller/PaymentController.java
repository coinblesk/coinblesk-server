/*
 * Copyright 2016 The Coinblesk team and the CSG Group at University of Zurich
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.coinblesk.server.controller;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.MediaType.APPLICATION_JSON_UTF8_VALUE;
import static org.springframework.web.bind.annotation.RequestMethod.GET;
import static org.springframework.web.bind.annotation.RequestMethod.POST;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.coinblesk.server.dto.*;
import com.coinblesk.server.exceptions.InvalidLockTimeException;
import com.coinblesk.server.exceptions.InvalidSignatureException;
import com.coinblesk.server.exceptions.MissingFieldException;
import com.coinblesk.server.exceptions.UserNotFoundException;
import com.coinblesk.server.utils.DTOUtils;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.AddressFormatException;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionOutPoint;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.crypto.TransactionSignature;
import org.bitcoinj.script.Script;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.coinblesk.bitcoin.TimeLockedAddress;
import com.coinblesk.json.v1.BalanceTO;
import com.coinblesk.json.v1.RefundTO;
import com.coinblesk.json.v1.SignVerifyTO;
import com.coinblesk.json.v1.TxSig;
import com.coinblesk.json.v1.Type;
import com.coinblesk.server.config.AppConfig;
import com.coinblesk.server.entity.Account;
import com.coinblesk.server.service.AccountService;
import com.coinblesk.server.service.TransactionService;
import com.coinblesk.server.service.WalletService;
import com.coinblesk.server.utils.ApiVersion;
import com.coinblesk.server.utils.ToUtils;
import com.coinblesk.util.BitcoinUtils;
import com.coinblesk.util.Pair;
import com.coinblesk.util.SerializeUtils;

import javax.validation.Valid;

/**
 *
 * @author Alessandro Di Carli
 * @author Thomas Bocek
 * @author Andreas Albrecht
 *
 */
@RestController
@RequestMapping(value = "/payment")
@ApiVersion({ "v1", "" })
public class PaymentController {

	private final static Logger LOG = LoggerFactory.getLogger(PaymentController.class);

	private final AppConfig appConfig;

	private final WalletService walletService;

	private final AccountService accountService;

	private final TransactionService txService;

	@Autowired
	public PaymentController(AppConfig appConfig, WalletService walletService, AccountService accountService, TransactionService txService) {
		this.appConfig = appConfig;
		this.walletService = walletService;
		this.accountService = accountService;
		this.txService = txService;
	}

	@RequestMapping(value = "/createTimeLockedAddress",
			method = POST,
			consumes = APPLICATION_JSON_UTF8_VALUE,
			produces = APPLICATION_JSON_UTF8_VALUE)
	@ResponseBody
	@CrossOrigin
	public ResponseEntity createTimeLockedAddress(@RequestBody @Valid SignedDTO request) {

		// Get the embedded payload and check signature
		final CreateAddressRequestDTO createAddressRequestDTO;
		try {
			createAddressRequestDTO = DTOUtils.parseAndValidate(request, CreateAddressRequestDTO.class);
			ECKey signingKey = DTOUtils.getECKeyFromHexPublicKey(createAddressRequestDTO.getPublicKey());
			DTOUtils.validateSignature(request.getPayload(), request.getSignature(), signingKey);
		} catch (MissingFieldException|InvalidSignatureException e) {
			return new ResponseEntity<>(new ErrorDTO(e.getMessage()), BAD_REQUEST);
		} catch (Throwable e) {
			return new ResponseEntity<>(new ErrorDTO("Bad request"), BAD_REQUEST);
		}

		// The client we want to create an address for
		final ECKey clientPublicKey = DTOUtils.getECKeyFromHexPublicKey(createAddressRequestDTO.getPublicKey());
		final long lockTime = createAddressRequestDTO.getLockTime();

		AccountService.CreateTimeLockedAddressResponse response = null;
		try {
			response = accountService.createTimeLockedAddress(clientPublicKey, lockTime);
		} catch (UserNotFoundException|InvalidLockTimeException e) {
			return new ResponseEntity<>(new ErrorDTO(e.getMessage()), BAD_REQUEST);
		} catch (Throwable e) {
			return new ResponseEntity<>(new ErrorDTO(e.getMessage()), INTERNAL_SERVER_ERROR);
		}

		final TimeLockedAddress address = response.getTimeLockedAddress();
		final ECKey serverPrivateKeyForSigning = response.getServerPrivateKey();

		if (response == null || address == null || serverPrivateKeyForSigning == null)
			return new ResponseEntity<>(new ErrorDTO("Could not create Address"), INTERNAL_SERVER_ERROR);

		// Start watching the address
		walletService.addWatching(address.createPubkeyScript());

		// Create response
		CreateAddressResponseDTO innerResponse = new CreateAddressResponseDTO(
				DTOUtils.toHex(address.getClientPubKey()),
				DTOUtils.toHex(address.getServerPubKey()),
				address.getLockTime()
				);
		SignedDTO responseDTO = DTOUtils.serializeAndSign(innerResponse, serverPrivateKeyForSigning);

		return new ResponseEntity<>(responseDTO, OK);
	}

	@RequestMapping(
			value = "/signverify",
			method = POST,
			consumes = APPLICATION_JSON_UTF8_VALUE,
			produces = APPLICATION_JSON_UTF8_VALUE)
	@ResponseBody
	public SignVerifyTO signVerify(@RequestBody SignVerifyTO request) {
		final String tag = "{signverify}";
		final Instant startTime = Instant.now();
		String clientPubKeyHex = "(UNKNOWN)";

		try {
			final NetworkParameters params = appConfig.getNetworkParameters();
			final Account account;
			final ECKey clientKey = ECKey.fromPublicOnly(request.publicKey());
			clientPubKeyHex = clientKey.getPublicKeyAsHex();
			final ECKey serverKey;
			final Transaction transaction;
			final List<TransactionSignature> clientSigs;

			// clear payeeSig since client sig does not cover it
			final TxSig payeeSigInput = request.payeeMessageSig();
			request.payeeMessageSig(null);
			final byte[] payeePubKey = request.payeePublicKey();
			request.payeePublicKey(null);

			// NFC has a hard limit of 245, thus we have no space for the date
			// yet.
			final SignVerifyTO error = ToUtils.checkInput(request, false);
			if (error != null) {
				LOG.info("{} - clientPubKey={} - input error - type={}", tag, clientPubKeyHex, error.type());
				return error;
			}

			LOG.debug("{} - clientPubKey={} - request", tag, clientPubKeyHex);
			account = accountService.getByClientPublicKey(clientKey.getPubKey());
			if (account == null
					|| account.clientPublicKey() == null
					|| account.serverPrivateKey() == null
					|| account.serverPublicKey() == null) {
				LOG.debug("{} - clientPubKey={} - KEYS_NOT_FOUND", tag, clientPubKeyHex);
				return ToUtils.newInstance(SignVerifyTO.class, Type.KEYS_NOT_FOUND);
			}
			serverKey = ECKey.fromPrivateAndPrecalculatedPublic(account.serverPrivateKey(), account.serverPublicKey());

			if (account.timeLockedAddresses().isEmpty()) {
				LOG.debug("{} - clientPubKey={} - ADDRESS_EMPTY", tag, clientPubKeyHex);
				return ToUtils.newInstance(SignVerifyTO.class, Type.ADDRESS_EMPTY, serverKey);
			}

			/*
			 * Got a transaction in the request - sign
			 */
			if (request.transaction() != null) {
				transaction = new Transaction(params, request.transaction());
				LOG.debug("{} - clientPubKey={} - transaction from input: \n{}", tag, clientPubKeyHex, transaction);

				List<TransactionOutput> outputsToAdd = new ArrayList<>();
				// if amount to spend && address provided, add corresponding
				// output
				if (request.amountToSpend() > 0 && request.addressTo() != null && !request.addressTo().isEmpty()) {
					TransactionOutput txOut = transaction.addOutput(Coin.valueOf(request.amountToSpend()),
							Address.fromBase58(params, request.addressTo()));
					outputsToAdd.add(txOut);
					LOG.debug("{} - added output={} to Tx={}", tag, txOut, transaction.getHash());
				}

				// if change amount is provided, we add an output to the most
				// recently created address of the client.
				if (request.amountChange() > 0) {
					Address changeAddress = account.latestTimeLockedAddresses().toAddress(params);
					Coin changeAmount = Coin.valueOf(request.amountChange());
					TransactionOutput changeOut = transaction.addOutput(changeAmount, changeAddress);
					outputsToAdd.add(changeOut);
					LOG.debug("{} - added change output={} to Tx={}", tag, changeOut, transaction.getHash());
				}

				if (!outputsToAdd.isEmpty()) {
					outputsToAdd = BitcoinUtils.sortOutputs(outputsToAdd);
					transaction.clearOutputs();
					for (TransactionOutput to : outputsToAdd) {
						transaction.addOutput(to);
					}
				}

			} else {
				LOG.debug("{} - clientPubKey={} - INPUT_MISMATCH", tag, clientPubKeyHex);
				return ToUtils.newInstance(SignVerifyTO.class, Type.INPUT_MISMATCH, serverKey);
			}

			// check signatures
			if (request.signatures() == null || (request.signatures().size() != transaction.getInputs().size())) {
				LOG.debug("{} - clientPubKey={} - INPUT_MISMATCH - number of signatures ({}) != number of inputs ({})",
						tag, clientPubKeyHex, request.signatures().size(), transaction.getInputs().size());
				return ToUtils.newInstance(SignVerifyTO.class, Type.INPUT_MISMATCH, serverKey);
			}

			clientSigs = SerializeUtils.deserializeSignatures(request.signatures());
			SignVerifyTO responseTO = txService.signVerifyTransaction(transaction, clientKey, serverKey, clientSigs);
			SerializeUtils.signJSON(responseTO, serverKey);

			// if we know the receiver, we create an additional signature with
			// the key of the payee.
			if (maybeAppendPayeeSignature(request, payeePubKey, payeeSigInput, responseTO)) {
				LOG.debug("{} - created additional signature for payee.", tag);
			} else {
				LOG.debug("{} - payee unknown (no additional signature)");
			}

			return responseTO;
		} catch (Exception e) {
			LOG.error("{} - clientPubKey={} - SERVER_ERROR: ", tag, clientPubKeyHex, e);
			return new SignVerifyTO()
					.currentDate(System.currentTimeMillis())
					.type(Type.SERVER_ERROR)
					.message(e.getMessage());
		} finally {
			LOG.debug("{} - clientPubKey={} - finished in {} ms", tag, clientPubKeyHex,
					Duration.between(startTime, Instant.now()).toMillis());
		}
	}

	private boolean maybeAppendPayeeSignature(SignVerifyTO request, byte[] payeePubKey, TxSig payeeTxSig,
			SignVerifyTO response) {
		if (payeePubKey == null || !ECKey.isPubKeyCanonical(payeePubKey) || payeeTxSig == null) {
			return false;
		}

		Account payeeAccount = accountService.getByClientPublicKey(payeePubKey);
		if (payeeAccount == null) {
			return false; // payee unknown / external user.
		}
		ECKey payeeClientKey = ECKey.fromPublicOnly(payeePubKey);
		ECKey payeeServerKey = ECKey.fromPrivateAndPrecalculatedPublic(payeeAccount.serverPrivateKey(),
				payeeAccount.serverPublicKey());

		// check that payee signature is valid
		request.payeePublicKey(payeePubKey);
		if (!SerializeUtils.verifyJSONSignatureRaw(request, payeeTxSig, payeeClientKey)) {
			return false;
		}
		request.payeePublicKey(null);

		// all checks OK - sign and append signature
		response.payeePublicKey(payeeServerKey.getPubKey());
		TxSig payeeSigOutput = SerializeUtils.signJSONRaw(response, payeeServerKey);
		response.payeeMessageSig(payeeSigOutput);
		return true;
	}

	/**
	 * Input is the KeyExchangeRequestDTO with the client public key. The server will create for
	 * this client public key its own server keypair and return the server public key.
	 */
	@RequestMapping(value = "/key-exchange",
			method = POST,
			consumes = APPLICATION_JSON_UTF8_VALUE,
			produces = APPLICATION_JSON_UTF8_VALUE)
	@ResponseBody
	@CrossOrigin
	public ResponseEntity keyExchange(@RequestBody @Valid KeyExchangeRequestDTO request) {

		ECKey clientPublicKey;
		try {
			clientPublicKey = DTOUtils.getECKeyFromHexPublicKey(request.getPublicKey());
		} catch (Throwable e) {
			return new ResponseEntity<>(new ErrorDTO("Invalid publicKey given"), BAD_REQUEST);
		};

		try {
			final ECKey serverPublicKey = accountService.createAcount(clientPublicKey);
			return new ResponseEntity<>(new KeyExchangeResponseDTO(serverPublicKey.getPublicKeyAsHex()), OK);
		} catch (Throwable e) {
			return new ResponseEntity<>(new ErrorDTO("Error during key exchange"), INTERNAL_SERVER_ERROR);
		}
	}

	@RequestMapping(
			value = "/balance",
			method = GET,
			consumes = APPLICATION_JSON_UTF8_VALUE,
			produces = APPLICATION_JSON_UTF8_VALUE)
	@ResponseBody
	public BalanceTO balance(@RequestBody BalanceTO input) {
		final long start = System.currentTimeMillis();
		try {
			if (input.publicKey() == null || input.publicKey().length == 0) {
				return new BalanceTO().type(Type.KEYS_NOT_FOUND);
			}
			LOG.debug("{balance} clientHash for {}", SerializeUtils.bytesToHex(input.publicKey()));
			final BalanceTO error = ToUtils.checkInput(input);
			if (error != null) {
				return error;
			}
			final NetworkParameters params = appConfig.getNetworkParameters();
			final List<ECKey> keys = accountService.getPublicECKeysByClientPublicKey(input.publicKey());
			final Script script = BitcoinUtils.createP2SHOutputScript(2, keys);
			final Address p2shAddressFrom = script.getToAddress(params);
			List<TransactionOutput> outputs = walletService.verifiedOutputs(params, p2shAddressFrom);
			LOG.debug("{balance} nr. of outputs from network {} for {}. Full list: {}", outputs.size(), "tdb", outputs);
			long total = 0;
			for (TransactionOutput transactionOutput : outputs) {
				total += transactionOutput.getValue().value;
			}
			LOG.debug("{balance}:{} done", (System.currentTimeMillis() - start));
			return new BalanceTO().balance(total);

		} catch (Exception e) {
			LOG.error("{balance} keys error", e);
			return new BalanceTO().type(Type.SERVER_ERROR).message(e.getMessage());
		}
	}

	@RequestMapping(
			value = "/refund",
			method = POST,
			consumes = APPLICATION_JSON_UTF8_VALUE,
			produces = APPLICATION_JSON_UTF8_VALUE)
	@ResponseBody
	public RefundTO refund(@RequestBody RefundTO input) {
		final long start = System.currentTimeMillis();
		try {
			if (input.publicKey() == null || input.publicKey().length == 0) {
				return new RefundTO().type(Type.KEYS_NOT_FOUND);
			}
			LOG.debug("{refund} for {}", SerializeUtils.bytesToHex(input.publicKey()));
			final RefundTO error = ToUtils.checkInput(input);
			if (error != null) {
				return error;
			}
			final List<ECKey> keys = accountService.getECKeysByClientPublicKey(input.publicKey());
			if (keys == null || keys.size() != 2) {
				return new RefundTO().type(Type.KEYS_NOT_FOUND);
			}
			final NetworkParameters params = appConfig.getNetworkParameters();
			final ECKey serverKey = keys.get(1);
			final ECKey clientKey = keys.get(0);
			final Script redeemScript = BitcoinUtils.createRedeemScript(2, keys);
			// this is how the client sees the tx
			final Transaction refundTransaction;

			// choice 1 - full refund tx
			if (input.refundTransaction() != null) {
				refundTransaction = new Transaction(params, input.refundTransaction());
			}
			// choice 2 - send outpoints, coins, where to send btc to, and
			// amount
			else if (input.outpointsCoinPair() != null
					&& !input.outpointsCoinPair().isEmpty()
					&& input.lockTimeSeconds() > 0
					&& input.refundSendTo() != null) {
				final List<Pair<TransactionOutPoint, Coin>> refundClientPoints = SerializeUtils.deserializeOutPointsCoin(
						params, input.outpointsCoinPair());
				try {
					Address refundSendTo = Address.fromBase58(params, input.refundSendTo());
					refundTransaction = BitcoinUtils.createRefundTx(params, refundClientPoints, redeemScript,
							refundSendTo, input.lockTimeSeconds());
				} catch (AddressFormatException e) {
					LOG.debug("{refund}:{} empty address for", (System.currentTimeMillis() - start));
					return new RefundTO().type(Type.ADDRESS_EMPTY).message(e.getMessage());
				}
			}
			// wrong choice
			else {
				return new RefundTO().type(Type.INPUT_MISMATCH);
			}

			// sanity check
			refundTransaction.verify();

			List<TransactionSignature> clientSigs = SerializeUtils.deserializeSignatures(input.clientSignatures());

			// now we can check the client sigs
			if (!SerializeUtils.verifyTxSignatures(refundTransaction, clientSigs, redeemScript, clientKey)) {
				LOG.debug("{refund} signature mismatch for tx {} with sigs {}", refundTransaction, clientSigs);
				return new RefundTO().type(Type.SIGNATURE_ERROR);
			} else {
				LOG.debug("{refund} signature good! for tx {} with sigs", refundTransaction, clientSigs);
			}
			// also check the server sigs, that the reedemscript has our public
			// key

			Collections.sort(keys, ECKey.PUBKEY_COMPARATOR);

			List<TransactionSignature> serverSigs = BitcoinUtils.partiallySign(refundTransaction, redeemScript,
					serverKey);
			boolean clientFirst = BitcoinUtils.clientFirst(keys, clientKey);
			BitcoinUtils.applySignatures(refundTransaction, redeemScript, clientSigs, serverSigs, clientFirst);
			input.serverSignatures(SerializeUtils.serializeSignatures(serverSigs));
			// TODO: enable
			// refundTransaction.verify(); make sure those inputs are from the
			// known p2sh address (min conf)
			byte[] refundTx = refundTransaction.unsafeBitcoinSerialize();
			txService.addTransaction(input.publicKey(), refundTx, refundTransaction.getHash().getBytes(), false);
			LOG.debug("{refund}:{} done", (System.currentTimeMillis() - start));
			return new RefundTO().setSuccess().refundTransaction(refundTx).serverSignatures(
					SerializeUtils.serializeSignatures(serverSigs));
		} catch (Exception e) {
			LOG.error("register keys error", e);
			return new RefundTO().type(Type.SERVER_ERROR).message(e.getMessage());
		}
	}

	@RequestMapping(value = {"/virtualbalance"}, method = POST,
			consumes = "application/json; charset=UTF-8",
			produces = "application/json; charset=UTF-8")
	@ResponseBody
	public BalanceTO virtualBalance(@RequestBody BalanceTO input) {
		if(input.publicKey() == null || input.publicKey().length == 0) {
			return new BalanceTO().type(Type.KEYS_NOT_FOUND);
		}

		// Check if message is signed correctly
		final BalanceTO error = ToUtils.checkInput(input);
		if (error != null) {
			return error;
		}

		// Fetch actual balance
		final long balance = accountService.getVirtualBalanceByClientPublicKey(input.publicKey());

		// Construct response
		BalanceTO balanceDTO = new BalanceTO().balance(balance);

		// Sign it
		Account account = accountService.getByClientPublicKey(input.publicKey());
		ECKey existingServerKey = ECKey.fromPrivateAndPrecalculatedPublic(account.serverPrivateKey(), account.serverPublicKey());
		return SerializeUtils.signJSON(balanceDTO, existingServerKey);
	}

}
