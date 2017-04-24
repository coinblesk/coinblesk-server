package com.coinblesk.server.exceptions;

import static org.springframework.http.HttpStatus.FORBIDDEN;

import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(code = FORBIDDEN, reason = "The user account is not yet activated")
public class UserAccountNotActivatedException extends BusinessException {

}
