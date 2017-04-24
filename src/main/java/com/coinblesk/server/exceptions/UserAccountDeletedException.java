package com.coinblesk.server.exceptions;

import static org.springframework.http.HttpStatus.NOT_ACCEPTABLE;

import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(code = NOT_ACCEPTABLE, reason = "The account is deactivated.")
public class UserAccountDeletedException extends BusinessException {

}
