package com.example.summary.api.model;

/**
 *  An enum represents error codes.
 */
public enum ErrorCode {

	/** 400 – the user id is not a valid positive number. */
	INVALID_USER_ID,

	/** 404 – the users service does not know the user. */
	USER_NOT_FOUND,

	/** 502 – the users service failed or returned an invalid response. */
	USER_SERVICE_ERROR,

	/** 504 – the users service did not answer in time. */
	USER_SERVICE_TIMEOUT,

	/** 500 – unexpected internal error. */
	INTERNAL_ERROR

}
