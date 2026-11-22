package com.example.summary.client;

import org.springframework.web.client.RestClient;

/**
 * Client for the external users service.
 */
public class UsersClient {

	private static final String SERVICE = "users";
	private static final String USER_PATH = "/users/{id}";
	private final RestClient restClient;

	public UsersClient(RestClient restClient) {
		this.restClient = restClient;
	}

	/**
	 * @throws ExternalServiceException on any failure
	 */
	public ExternalUser fetchUser(long userId) {
		return ExternalCalls.call(SERVICE, restClient.get().uri(USER_PATH, userId),
				response -> response.body(ExternalUser.class));
	}

}
