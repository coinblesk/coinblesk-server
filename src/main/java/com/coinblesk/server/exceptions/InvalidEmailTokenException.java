package com.coinblesk.server.exceptions;

import static org.springframework.http.HttpStatus.FORBIDDEN;

import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(code = FORBIDDEN, reason = "Invalid e-mail token provided.")
public class InvalidEmailTokenException extends BusinessException { }
