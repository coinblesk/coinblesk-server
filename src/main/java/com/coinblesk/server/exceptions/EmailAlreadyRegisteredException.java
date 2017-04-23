package com.coinblesk.server.exceptions;

import static org.springframework.http.HttpStatus.FORBIDDEN;

import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(code = FORBIDDEN, reason = "E-mail address is already registered.")
public class EmailAlreadyRegisteredException extends BusinessException { }
