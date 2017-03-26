package com.coinblesk.server.controller;

import com.coinblesk.server.dto.ErrorDTO;
import com.coinblesk.server.dto.PaymentRequestDTO;
import com.coinblesk.server.dto.SignatureUtils;
import com.coinblesk.server.dto.SignedDTO;
import com.coinblesk.server.exceptions.InvalidSignatureException;
import com.coinblesk.server.exceptions.MissingFieldException;
import org.bitcoinj.core.ECKey;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;


import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.MediaType.APPLICATION_JSON_UTF8_VALUE;
import static org.springframework.web.bind.annotation.RequestMethod.POST;

@RestController
@RequestMapping(value = "/payment")
@CrossOrigin
public class MicroPaymentController {

	@RequestMapping(value = "/virtualpayment", method = POST,
			consumes = APPLICATION_JSON_UTF8_VALUE,
			produces = APPLICATION_JSON_UTF8_VALUE)
	public ResponseEntity createTimeLockedAddress(@RequestBody @Valid SignedDTO request)
	{
		final PaymentRequestDTO requestDTO;
		final String payloadBase64String = request.getPayload();
		final ECKey keySender, keyReceiver;
		try {
			// Parse Base64 payload to actual DTO
			requestDTO = SignatureUtils.parsePayload(payloadBase64String, PaymentRequestDTO.class);

			// Get public key of sender from DTO and parse into ECKey
			keySender = SignatureUtils.getECKeyFromHexPublicKey(requestDTO.getFromPublicKey());

			// The sender of coins must have signed the whole payload
			final String sigR = request.getSignature().getSigR();
			final String sigS = request.getSignature().getSigS();
			SignatureUtils.validateSignature(payloadBase64String, sigR, sigS, keySender);

			// Get the public key of the receiver from the DTO and parse into ECKey
			// This makes sure the key was given in the correct format
			keyReceiver = SignatureUtils.getECKeyFromHexPublicKey(requestDTO.getFromPublicKey());

			// Make sure the sending user is known
			// TODO: check if sender is in database

			// Abort if the nonce is not fresh
			// TODO: check nonce, for security reasons do this in the service layer aswell

			// Abort if the amount is negative
			// Abort if the funds are not sufficient
			// TODO: check amount, for security reasons do this in the service layer aswell

			// Make sure the receiving user is known
			// TODO: Get receiving 'keys' from database

			// Try to make transfer
			// TODO: serviceLayer:
			/* - @Transactional with isolation level serializable
			 * - Get both entities
			 * - Check nonce is newer than at sender
			 * - Check amount positive
			 * - Check sufficient funds
			 * - amountSender -= amount
			 * - amountNonce = newNonce
			 * - amountReceiver += amount
			 * - commit
			 */

			// Return signed message with new virtual balance
			// TODO: return signed OK message

			return new ResponseEntity<>("ok", OK);

		} catch (MissingFieldException|InvalidSignatureException e) {
			return new ResponseEntity<>(new ErrorDTO(e.getMessage()), BAD_REQUEST);
		} catch (Exception e) {
			return new ResponseEntity<>(new ErrorDTO("Bad request"), BAD_REQUEST);
		}
	}

}
