package com.coinblesk.server.controller;

import com.coinblesk.server.dto.*;
import com.coinblesk.server.exceptions.*;
import com.coinblesk.server.service.MicropaymentService;
import com.coinblesk.server.utils.DTOUtils;
import com.coinblesk.server.utils.SignatureUtils;
import com.coinblesk.util.InsufficientFunds;
import org.bitcoinj.core.ECKey;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;


import java.time.Instant;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;
import static org.springframework.http.MediaType.APPLICATION_JSON_UTF8_VALUE;
import static org.springframework.web.bind.annotation.RequestMethod.POST;

@RestController
@RequestMapping(value = "/payment")
@CrossOrigin
public class MicroPaymentController {

	private final MicropaymentService micropaymentService;

	@Autowired
	public MicroPaymentController(MicropaymentService micropaymentService) {
		this.micropaymentService = micropaymentService;
	}

	@RequestMapping(value = "/virtualpayment", method = POST,
			consumes = APPLICATION_JSON_UTF8_VALUE,
			produces = APPLICATION_JSON_UTF8_VALUE)
	public ResponseEntity virtualpayment(@RequestBody @Valid SignedDTO request)
	{
		// Get the embedded payload and check signature
		final PaymentRequestDTO requestDTO;
		try {
			requestDTO = DTOUtils.parseAndValidatePayload(request, PaymentRequestDTO.class);
		} catch (MissingFieldException|InvalidSignatureException e) {
			return new ResponseEntity<>(new ErrorDTO(e.getMessage()), BAD_REQUEST);
		} catch (Throwable e) {
			return new ResponseEntity<>(new ErrorDTO("Bad request"), BAD_REQUEST);
		}

		final ECKey keySender, keyReceiver;

		// Get the public key of the sender from the DTO and parse into ECKey
		keySender = SignatureUtils.getECKeyFromHexPublicKey(requestDTO.getFromPublicKey());

		// Get the public key of the receiver from the DTO and parse into ECKey
		keyReceiver = SignatureUtils.getECKeyFromHexPublicKey(requestDTO.getToPublicKey());

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
		final String responseAsJson = DTOUtils.gson.toJson(virtualPaymentResponseDTO);
		final String responseAsBase64 = DTOUtils.toBase64(responseAsJson);

		// We need two signatures, so both the sender and receiver can verify that this response is legit
		ECKey serverKeyForSender = ECKey.fromPrivate(result.getServerPrivateKeyForSender());
		SignatureDTO signatureForSender = SignatureUtils.sign(responseAsBase64, serverKeyForSender);

		ECKey serverKeyForReceiver = ECKey.fromPrivate(result.getServerPrivateKeyForReceiver());
		SignatureDTO signatureForReceiver = SignatureUtils.sign(responseAsBase64, serverKeyForReceiver);

		MultiSignedDTO signedResponse = new MultiSignedDTO(responseAsBase64, signatureForSender, signatureForReceiver);

		return new ResponseEntity<>(signedResponse, OK);
	}

}
