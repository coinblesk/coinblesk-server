package com.coinblesk.server.exceptions;

import static org.springframework.http.HttpStatus.FORBIDDEN;

import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(code = FORBIDDEN, reason = "You could not be logged in.")
public class CoinbleskAuthenticationException extends BusinessException { }
