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

import com.coinblesk.bitcoin.TimeLockedAddress;
import com.coinblesk.json.v1.BalanceTO;
import com.coinblesk.json.v1.SignVerifyTO;
import com.coinblesk.json.v1.TxSig;
import com.coinblesk.json.v1.Type;
import com.coinblesk.server.config.AppConfig;
import com.coinblesk.server.dto.*;
import com.coinblesk.server.entity.Account;
import com.coinblesk.server.exceptions.InvalidLockTimeException;
import com.coinblesk.server.exceptions.InvalidSignatureException;
import com.coinblesk.server.exceptions.MissingFieldException;
import com.coinblesk.server.exceptions.UserNotFoundException;
import com.coinblesk.server.service.AccountService;
import com.coinblesk.server.service.TransactionService;
import com.coinblesk.server.service.WalletService;
import com.coinblesk.server.utils.ApiVersion;
import com.coinblesk.server.utils.DTOUtils;
import com.coinblesk.server.utils.ToUtils;
import com.coinblesk.util.SerializeUtils;
import org.bitcoinj.core.ECKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

import static org.springframework.http.HttpStatus.*;
import static org.springframework.http.MediaType.APPLICATION_JSON_UTF8_VALUE;
import static org.springframework.web.bind.annotation.RequestMethod.POST;

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

		AccountService.CreateTimeLockedAddressResponse response;
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
		walletService.addWatching(address.getAddress(appConfig.getNetworkParameters()));

		// Create response
		CreateAddressResponseDTO innerResponse = new CreateAddressResponseDTO(
				DTOUtils.toHex(address.getClientPubKey()),
				DTOUtils.toHex(address.getServerPubKey()),
				address.getLockTime()
				);
		SignedDTO responseDTO = DTOUtils.serializeAndSign(innerResponse, serverPrivateKeyForSigning);

		return new ResponseEntity<>(responseDTO, OK);
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
	 * this client public key its own server keypair and return the server public key
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
		}

		try {
			final ECKey serverPublicKey = accountService.createAcount(clientPublicKey);
			return new ResponseEntity<>(new KeyExchangeResponseDTO(serverPublicKey.getPublicKeyAsHex()), OK);
		} catch (Throwable e) {
			return new ResponseEntity<>(new ErrorDTO("Error during key exchange"), INTERNAL_SERVER_ERROR);
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
