package com.coinblesk.server.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(code = HttpStatus.FORBIDDEN, reason = "The user was not found.")
public class UserAccountNotFoundException extends BusinessException { }
