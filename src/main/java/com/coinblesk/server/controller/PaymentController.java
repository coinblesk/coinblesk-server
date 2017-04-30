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
import static org.springframework.web.bind.annotation.RequestMethod.POST;

import javax.validation.Valid;

import org.bitcoinj.core.ECKey;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import com.coinblesk.bitcoin.TimeLockedAddress;
import com.coinblesk.server.config.AppConfig;
import com.coinblesk.dto.CreateAddressRequestDTO;
import com.coinblesk.dto.CreateAddressResponseDTO;
import com.coinblesk.dto.ErrorDTO;
import com.coinblesk.dto.KeyExchangeRequestDTO;
import com.coinblesk.dto.KeyExchangeResponseDTO;
import com.coinblesk.dto.SignedDTO;
import com.coinblesk.dto.VirtualBalanceRequestDTO;
import com.coinblesk.dto.VirtualBalanceResponseDTO;
import com.coinblesk.server.exceptions.InvalidLockTimeException;
import com.coinblesk.server.exceptions.InvalidSignatureException;
import com.coinblesk.server.exceptions.MissingFieldException;
import com.coinblesk.server.exceptions.UserNotFoundException;
import com.coinblesk.server.service.AccountService;
import com.coinblesk.server.service.WalletService;
import com.coinblesk.server.utils.ApiVersion;
import com.coinblesk.server.utils.DTOUtils;

/**
 * @author Alessandro Di Carli
 * @author Thomas Bocek
 * @author Andreas Albrecht
 */
@RestController
@RequestMapping(value = "/payment")
@ApiVersion({"v1", ""})
public class PaymentController {

	private final AppConfig appConfig;

	private final WalletService walletService;
	private final AccountService accountService;

	@Autowired
	public PaymentController(AppConfig appConfig, WalletService walletService, AccountService accountService) {
		this.appConfig = appConfig;
		this.walletService = walletService;
		this.accountService = accountService;
	}

	@RequestMapping(value = "/createTimeLockedAddress", method = POST, consumes = APPLICATION_JSON_UTF8_VALUE,
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
		} catch (MissingFieldException | InvalidSignatureException e) {
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
		} catch (UserNotFoundException | InvalidLockTimeException e) {
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
		CreateAddressResponseDTO innerResponse = new CreateAddressResponseDTO(DTOUtils.toHex(address.getClientPubKey()
		), DTOUtils.toHex(address.getServerPubKey()), address.getLockTime());
		SignedDTO responseDTO = DTOUtils.serializeAndSign(innerResponse, serverPrivateKeyForSigning);

		return new ResponseEntity<>(responseDTO, OK);
	}

	/**
	 * Input is the KeyExchangeRequestDTO with the client public key. The server will create for
	 * this client public key its own server keypair and return the server public key
	 */
	@RequestMapping(value = "/key-exchange", method = POST, consumes = APPLICATION_JSON_UTF8_VALUE, produces =
		APPLICATION_JSON_UTF8_VALUE)
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
			final ECKey serverKey = accountService.createAccount(clientPublicKey);
			walletService.getWallet().importKey(serverKey);
			return new ResponseEntity<>(new KeyExchangeResponseDTO(serverKey.getPublicKeyAsHex()), OK);
		} catch (Throwable e) {
			return new ResponseEntity<>(new ErrorDTO("Error during key exchange"), INTERNAL_SERVER_ERROR);
		}
	}

	@RequestMapping(value = {"/virtualbalance"}, method = POST, consumes = "application/json; charset=UTF-8", produces
		= "application/json; charset=UTF-8")
	@ResponseBody
	public ResponseEntity virtualBalance(@RequestBody @Valid SignedDTO request) {

		// Get the embedded payload and check signature
		final VirtualBalanceRequestDTO virtualBalanceRequestDTO;
		try {
			virtualBalanceRequestDTO = DTOUtils.parseAndValidate(request, VirtualBalanceRequestDTO.class);
			ECKey signingKey = DTOUtils.getECKeyFromHexPublicKey(virtualBalanceRequestDTO.getPublicKey());
			DTOUtils.validateSignature(request.getPayload(), request.getSignature(), signingKey);
		} catch (MissingFieldException | InvalidSignatureException e) {
			return new ResponseEntity<>(new ErrorDTO(e.getMessage()), BAD_REQUEST);
		} catch (Throwable e) {
			return new ResponseEntity<>(new ErrorDTO("Bad request"), BAD_REQUEST);
		}

		final ECKey sender = DTOUtils.getECKeyFromHexPublicKey(virtualBalanceRequestDTO.getPublicKey());
		final AccountService.GetVirtualBalanceResponse response;
		try {
			response = accountService.getVirtualBalanceByClientPublicKey(sender.getPubKey());
		} catch (UserNotFoundException e) {
			return new ResponseEntity<>(new ErrorDTO(e.getMessage()), BAD_REQUEST);
		}

		// Construct response
		VirtualBalanceResponseDTO innerResponse = new VirtualBalanceResponseDTO(response.getBalance());
		SignedDTO responseDTO = DTOUtils.serializeAndSign(innerResponse, response.getServerPrivateKey());

		return new ResponseEntity<>(responseDTO, OK);

	}

}
