package com.coinblesk.server.exceptions;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(code = BAD_REQUEST, reason = "An invalid address was provided.")
public class InvalidAddressException extends BusinessException {

}
