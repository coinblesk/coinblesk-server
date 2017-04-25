package com.coinblesk.server.exceptions;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(code = BAD_REQUEST, reason = "An invalid e-mail address was provided.")
public class InvalidEmailProvidedException extends BusinessException { }
