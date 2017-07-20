package com.coinblesk.server.exceptions;

import static org.springframework.http.HttpStatus.NOT_ACCEPTABLE;

import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(value = NOT_ACCEPTABLE, reason = "You don't have enough funds.")
public class TooLittleFundsException extends BusinessException { }
