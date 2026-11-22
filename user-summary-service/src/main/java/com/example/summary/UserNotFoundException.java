package com.example.summary;

/**
 * The users service does not know the requested user.
 */
public class UserNotFoundException extends RuntimeException {

	private final long userId;

	public UserNotFoundException(long userId) {
		super("User " + userId + " not found");
		this.userId = userId;
	}

	public long userId() {
		return userId;
	}

}
