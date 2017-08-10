package com.coinblesk.server.exceptions;

import static org.springframework.http.HttpStatus.METHOD_NOT_ALLOWED;

import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(value = METHOD_NOT_ALLOWED, reason = "The payment failed, because input transaction is not yet mined.")
public class PaymentFailedNotYetConfirmedException extends BusinessException { }
