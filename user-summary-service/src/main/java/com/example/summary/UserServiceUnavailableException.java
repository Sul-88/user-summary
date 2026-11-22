package com.example.summary;

/**
 * The user could not be fetched because the users service failed or did not answer in time.
 */
public class UserServiceUnavailableException extends RuntimeException {

	private final boolean timeout;

	public UserServiceUnavailableException(String message, Throwable cause, boolean timeout) {
		super(message, cause);
		this.timeout = timeout;
	}

	/**
	 * {@code true} if the users service did not answer in time.
	 */
	public boolean timeout() {
		return timeout;
	}

}
