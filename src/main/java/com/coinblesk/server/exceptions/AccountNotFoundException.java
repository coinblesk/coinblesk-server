package com.coinblesk.server.exceptions;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(code = BAD_REQUEST, reason = "The account could not be found.")
public class AccountNotFoundException extends BusinessException {

}
