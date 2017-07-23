package com.coinblesk.server.exceptions;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(value = BAD_REQUEST, reason = "The payment failed.")
public class PaymentFailedException extends BusinessException { }
