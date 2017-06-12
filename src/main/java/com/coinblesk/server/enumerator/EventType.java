package com.coinblesk.server.enumerator;

public enum EventType {
	EVENT_SERVICE_AUTO_REMOVAL,
	EVENT_SERVICE_EMERGENCY_EMAIL_SENT,
	USER_ACCOUNT_LOGIN_FAILED,
	USER_ACCOUNT_LOGIN_FAILED_WITH_DELETED_ACCOUNT,
	USER_ACCOUNT_COULD_NOT_CREATE_USER,
	USER_ACCOUNT_CREATE_TOKEN_COULD_NOT_BE_SENT,
	USER_ACCOUNT_COULD_NOT_BE_PROVIDED,
	USER_ACCOUNT_COULD_NOT_BE_ACTIVATED_WRONG_LINK,
	USER_ACCOUNT_COULD_NOT_BE_DELETED,
	USER_ACCOUNT_COULD_NOT_VERIFY_FORGOT_WRONG_LINK,
	USER_ACCOUNT_COULD_NOT_HANDLE_FORGET_REQUEST,
	USER_ACCOUNT_COULD_NOT_SEND_FORGET_EMAIL,
	USER_ACCOUNT_PASSWORD_COULD_NOT_BE_CHANGED,
	USER_ACCOUNT_COULD_NOT_TRANSFER_P2SH,
	MICRO_PAYMENT_CLOSING_CHANNEL,
	MICRO_PAYMENT_COULD_NOT_LOG_TO_FILE,
	MICRO_PAYMENT_CLOSING_OF_NONEXISTING_CHANNEL,
	MICRO_PAYMENT_COULD_NOT_BROADCAST_CHANNEL_TRANSACTION,
	MICRO_PAYMENT_POT_EXHAUSTED,
	MICRO_PAYMENT_PAYOUT_ERROR,
	SERVER_BALANCE_NOT_IN_SYNC
}
