package com.coinblesk.server.exceptions;

import static org.springframework.http.HttpStatus.NOT_ACCEPTABLE;

import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(value = NOT_ACCEPTABLE, reason = "This user account has an unregistered token.")
public class UserAccountHasUnregisteredToken extends BusinessException { }
