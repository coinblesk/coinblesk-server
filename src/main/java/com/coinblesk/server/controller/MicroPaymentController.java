package com.coinblesk.server.controller;

import com.coinblesk.dto.*;
import com.coinblesk.server.exceptions.*;
import com.coinblesk.server.service.MicropaymentService;
import com.coinblesk.server.utils.DTOUtils;
import com.coinblesk.util.InsufficientFunds;
import org.bitcoinj.core.ECKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.time.Instant;

import static com.coinblesk.server.service.MicropaymentService.*;
import static org.springframework.http.HttpStatus.*;
import static org.springframework.http.MediaType.APPLICATION_JSON_UTF8_VALUE;
import static org.springframework.web.bind.annotation.RequestMethod.POST;

@RestController
@RequestMapping(
	value = "/payment",
	consumes = APPLICATION_JSON_UTF8_VALUE,
	produces = { APPLICATION_JSON_UTF8_VALUE, "application/vnd.coinblesk.v4+json"})
@CrossOrigin
public class MicroPaymentController {

	private final Logger LOG = LoggerFactory.getLogger(MicroPaymentController.class);

	private final MicropaymentService micropaymentService;

	@Autowired
	public MicroPaymentController(MicropaymentService micropaymentService) {
		this.micropaymentService = micropaymentService;
	}

	@RequestMapping(value = "/micropayment", method = POST)
	public ResponseEntity micropayment(@RequestBody @Valid SignedDTO request) {
		try {
			// Get the embedded request
			final MicroPaymentRequestDTO requestDTO;
			final ECKey senderPublicKey, receiverPublicKey;

			requestDTO = DTOUtils.parseAndValidate(request, MicroPaymentRequestDTO.class);
			senderPublicKey = DTOUtils.getECKeyFromHexPublicKey(requestDTO.getFromPublicKey());
			DTOUtils.validateSignature(request.getPayload(), request.getSignature(), senderPublicKey);

			// Call the service and map failures to error messages
			MicroPaymentResult result = micropaymentService.microPayment(senderPublicKey,
				requestDTO.getToPublicKey(), requestDTO.getTx(), requestDTO.getAmount(), requestDTO.getNonce());

			if (result.broadcastedTx == null) {
				return new ResponseEntity<>("New balance receiver: " + result.newBalanceReceiver, OK);
			} else {
				return new ResponseEntity<>(" Broadcast Transaction: " + result.broadcastedTx.getHashAsString(), OK);
			}

		} catch (CoinbleskInternalError e) {
			LOG.error("Error during micropayment: " + e.getMessage(), e);
			return new ResponseEntity<>(new ErrorDTO("Internal server error"), INTERNAL_SERVER_ERROR);
		} catch (Throwable e) {
			LOG.warn("Bad request for /micropayment: " + e.getMessage(), e.getCause());
			return new ResponseEntity<>(new ErrorDTO(e.getMessage()), BAD_REQUEST);
		}
	}


	@RequestMapping(value = "/virtualpayment", method = POST)
	public ResponseEntity virtualpayment(@RequestBody @Valid SignedDTO request) {
		// Get the embedded payload and check signature
		final VirtualPaymentRequestDTO requestDTO;
		try {
			requestDTO = DTOUtils.parseAndValidate(request, VirtualPaymentRequestDTO.class);
			ECKey signingKey = DTOUtils.getECKeyFromHexPublicKey(requestDTO.getFromPublicKey());
			DTOUtils.validateSignature(request.getPayload(), request.getSignature(), signingKey);
		} catch (MissingFieldException | InvalidSignatureException e) {
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
			result = micropaymentService.virtualPayment(keySender, keyReceiver, requestDTO.getAmount(), requestDTO
				.getNonce());
		} catch (InvalidNonceException | InvalidAmountException | InsufficientFunds | UserNotFoundException |
			InvalidRequestException e) {
			return new ResponseEntity<>(new ErrorDTO(e.getMessage()), BAD_REQUEST);
		} catch (Throwable e) {
			return new ResponseEntity<>(new ErrorDTO(e.getMessage()), SERVICE_UNAVAILABLE);
		}

		// Construct response payload
		VirtualPaymentResponseDTO virtualPaymentResponseDTO = new VirtualPaymentResponseDTO(requestDTO.getAmount(),
			keySender.getPublicKeyAsHex(), result.getNewBalanceSender(), keyReceiver.getPublicKeyAsHex(), result
			.getNewBalanceReceiver(), Instant.now().toEpochMilli());
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

	@RequestMapping(value = "/payout", method = POST)
	public ResponseEntity payout(@RequestBody @Valid SignedDTO request) {
		// Get the embedded payload and check signature
		final PayoutRequestDTO requestDTO;
		try {
			requestDTO = DTOUtils.parseAndValidate(request, PayoutRequestDTO.class);
			ECKey signingKey = DTOUtils.getECKeyFromHexPublicKey(requestDTO.getPublicKey());
			DTOUtils.validateSignature(request.getPayload(), request.getSignature(), signingKey);
		} catch (MissingFieldException | InvalidSignatureException e) {
			return new ResponseEntity<>(new ErrorDTO(e.getMessage()), BAD_REQUEST);
		} catch (Throwable e) {
			return new ResponseEntity<>(new ErrorDTO("Bad request"), BAD_REQUEST);
		}

		final ECKey accountOwner = DTOUtils.getECKeyFromHexPublicKey(requestDTO.getPublicKey());

		PayoutResponse res;
		try {
			res = micropaymentService.payOutVirtualBalance(accountOwner, requestDTO.getToAddress());
		} catch (Throwable e) {
			return new ResponseEntity<>(new ErrorDTO(e.getMessage()), BAD_REQUEST);
		}

		if (res.valuePaidOut == 0L) {
			return new ResponseEntity<>(new ErrorDTO("Not enough money available, try again later."), SERVICE_UNAVAILABLE);
		}

		return new ResponseEntity<>("", OK);
	}

}
