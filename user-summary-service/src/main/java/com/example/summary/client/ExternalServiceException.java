package com.example.summary.client;

/**
 * Failure of a call to an external service, classified by {@link Reason}.
 */
public class ExternalServiceException extends RuntimeException {

	public static final int NO_STATUS = -1;

	public enum Reason {
		NOT_FOUND,
		CLIENT_ERROR,
		SERVER_ERROR,
		TIMEOUT,
		CONNECTION_FAILED,
		INVALID_RESPONSE
	}

	private final Reason reason;

	private final int status;

	public ExternalServiceException(Reason reason, int status, String message, Throwable cause) {
		super(message, cause);
		this.reason = reason;
		this.status = status;
	}

	public Reason reason() {
		return reason;
	}

	public int status() {
		return status;
	}

	public boolean retryable() {
		return reason == Reason.SERVER_ERROR || reason == Reason.CONNECTION_FAILED;
	}
}
