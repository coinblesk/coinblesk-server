package com.coinblesk.server.exceptions;

import static org.springframework.http.HttpStatus.FORBIDDEN;

import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(code = FORBIDDEN, reason = "The unregistered token was invalid.")
public class UserAccountUnregisteredTokenInvalid extends BusinessException { }
