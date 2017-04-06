package com.coinblesk.server.controller;

import com.coinblesk.bitcoin.TimeLockedAddress;
import com.coinblesk.server.config.AppConfig;
import com.coinblesk.server.dto.*;
import com.coinblesk.server.exceptions.*;
import com.coinblesk.server.service.AccountService;
import com.coinblesk.server.service.MicropaymentService;
import com.coinblesk.server.service.WalletService;
import com.coinblesk.server.utils.DTOUtils;
import com.coinblesk.util.InsufficientFunds;
import lombok.extern.java.Log;
import org.bitcoinj.core.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;


import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;
import static org.springframework.http.MediaType.APPLICATION_JSON_UTF8_VALUE;
import static org.springframework.web.bind.annotation.RequestMethod.POST;

@RestController
@RequestMapping(value = "/payment")
@CrossOrigin
@Log
public class MicroPaymentController {

	private final MicropaymentService micropaymentService;

	@Autowired AppConfig appConfig;

	@Autowired WalletService walletService;

	@Autowired AccountService accountService;

	@Autowired
	public MicroPaymentController(MicropaymentService micropaymentService) {
		this.micropaymentService = micropaymentService;
	}

	@RequestMapping(value = "/micropayment", method = POST,
		consumes = APPLICATION_JSON_UTF8_VALUE,
		produces = APPLICATION_JSON_UTF8_VALUE)
	public ResponseEntity micropayment(@RequestBody @Valid SignedDTO request)
	{
		// Get the embedded request
		final MicroPaymentRequestDTO requestDTO;
		final ECKey senderPublicKey;
		try {
			requestDTO = DTOUtils.parseAndValidate(request, MicroPaymentRequestDTO.class);
			senderPublicKey = DTOUtils.getECKeyFromHexPublicKey(requestDTO.getFromPublicKey());
			DTOUtils.validateSignature(request.getPayload(), request.getSignature(), senderPublicKey);
		} catch (MissingFieldException|InvalidSignatureException e) {
			return new ResponseEntity<>(new ErrorDTO(e.getMessage()), BAD_REQUEST);
		} catch (Throwable e) {
			return new ResponseEntity<>(new ErrorDTO("Bad request"), BAD_REQUEST);
		}

		// Parse the transaction
		byte[] txInByes = DTOUtils.fromHex(requestDTO.getTx());
		final Transaction tx;
		try {
			tx = new Transaction(appConfig.getNetworkParameters(), txInByes);
			tx.verify();
		} catch (VerificationException e) {
			return new ResponseEntity<>(new ErrorDTO("Invalid transaction: " + e.getMessage()), BAD_REQUEST);
		} catch (Throwable e) {
			e.printStackTrace();
			return new ResponseEntity<>(new ErrorDTO("Could not parse transaction"), BAD_REQUEST);
		}

		// Make sure all the UTXOs are known to the wallet
		List<TransactionOutput> spentOutputs = tx.getInputs().stream().map(walletService::findOutputFor).collect(Collectors.toList());
		if (spentOutputs.stream().anyMatch(Objects::isNull)) {
			return new ResponseEntity<>(new ErrorDTO("Transaction spends unknown UTXOs"), BAD_REQUEST);
		}

		// Gather all addresses from the input and make sure they are in the P2SH format
		List<Address> spentAddresses = spentOutputs.stream()
			.map(transactionOutput -> transactionOutput.getAddressFromP2SH(appConfig.getNetworkParameters()))
			.collect(Collectors.toList());
		if (spentAddresses.stream().anyMatch(Objects::isNull)) {
			return new ResponseEntity<>(new ErrorDTO("Transaction must spent P2SH addresses"), BAD_REQUEST);
		}

		// Gather all TimeLockedAddresses from the database and make sure we known them all
		List<TimeLockedAddress> timeLockedAddresses = spentAddresses.stream()
			.map(Address::getHash160)
			.map(accountService::getTimeLockedAddressByAddressHash)
			.collect(Collectors.toList());
		if (timeLockedAddresses.stream().anyMatch(Objects::isNull)) {
			return new ResponseEntity<>(new ErrorDTO("Used TLA inputs are not known to server"), BAD_REQUEST);
		}

		return new ResponseEntity<>("not yet implemented", OK);
	}


	@RequestMapping(value = "/virtualpayment", method = POST,
			consumes = APPLICATION_JSON_UTF8_VALUE,
			produces = APPLICATION_JSON_UTF8_VALUE)
	public ResponseEntity virtualpayment(@RequestBody @Valid SignedDTO request)
	{
		// Get the embedded payload and check signature
		final VirtualPaymentRequestDTO requestDTO;
		try {
			requestDTO = DTOUtils.parseAndValidate(request, VirtualPaymentRequestDTO.class);
			ECKey signingKey = DTOUtils.getECKeyFromHexPublicKey(requestDTO.getFromPublicKey());
			DTOUtils.validateSignature(request.getPayload(), request.getSignature(), signingKey);
		} catch (MissingFieldException|InvalidSignatureException e) {
			return new ResponseEntity<>(new ErrorDTO(e.getMessage()), BAD_REQUEST);
		} catch (Throwable e) {
			return new ResponseEntity<>(new ErrorDTO("Bad request"), BAD_REQUEST);
		}

		final ECKey keySender, keyReceiver;

		// Get the public key of the sender from the DTO and parse into ECKey
		keySender = DTOUtils.getECKeyFromHexPublicKey(requestDTO.getFromPublicKey());

		// Get the public key of the receiver from the DTO and parse into ECKey
		keyReceiver = DTOUtils.getECKeyFromHexPublicKey(requestDTO.getToPublicKey());

		// Do payment in service
		MicropaymentService.VirtualPaymentResult result;
		try {
			result = micropaymentService.virtualPayment(keySender, keyReceiver, requestDTO.getAmount(), requestDTO.getNonce());
		} catch (InvalidNonceException|InvalidAmountException|InsufficientFunds|UserNotFoundException|InvalidRequestException e) {
			return new ResponseEntity<>(new ErrorDTO(e.getMessage()), BAD_REQUEST);
		} catch (Throwable e) {
			return new ResponseEntity<>(new ErrorDTO(e.getMessage()), SERVICE_UNAVAILABLE);
		}

		// Construct response payload
		VirtualPaymentResponseDTO virtualPaymentResponseDTO = new VirtualPaymentResponseDTO(
				requestDTO.getAmount(),
				keySender.getPublicKeyAsHex(),
				result.getNewBalanceSender(),
				keyReceiver.getPublicKeyAsHex(),
				result.getNewBalanceReceiver(),
				Instant.now().toEpochMilli()
				);
		final String responseAsJson = DTOUtils.toJSON(virtualPaymentResponseDTO);
		final String responseAsBase64 = DTOUtils.toBase64(responseAsJson);

		// We need two signatures, so both the sender and receiver can verify that this response is legit
		ECKey serverKeyForSender = ECKey.fromPrivate(result.getServerPrivateKeyForSender());
		SignatureDTO signatureForSender = DTOUtils.sign(responseAsBase64, serverKeyForSender);

		ECKey serverKeyForReceiver = ECKey.fromPrivate(result.getServerPrivateKeyForReceiver());
		SignatureDTO signatureForReceiver = DTOUtils.sign(responseAsBase64, serverKeyForReceiver);

		MultiSignedDTO signedResponse = new MultiSignedDTO(responseAsBase64, signatureForSender, signatureForReceiver);

		return new ResponseEntity<>(signedResponse, OK);
	}

}
