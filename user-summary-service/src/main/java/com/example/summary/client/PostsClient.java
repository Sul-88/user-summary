package com.example.summary.client;

import java.util.List;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.web.client.RestClient;

/**
 * A client for the external posts service.
 */
public class PostsClient {

	private static final String SERVICE = "posts";
	private static final String POSTS_PATH = "/posts";
	private static final String USER_ID_PARAM = "userId";

	private static final ParameterizedTypeReference<List<ExternalPost>> POST_LIST = new ParameterizedTypeReference<>() {
	};

	private final RestClient restClient;

	public PostsClient(RestClient restClient) {
		this.restClient = restClient;
	}

	/**
	 * @throws ExternalServiceException on any failure.
	 */
	@Retryable(predicate = PostClientRetryPredicate.class,
			maxRetriesString = "${client.posts.retry.max-retries}",
			delayString = "${client.posts.retry.delay}")
	public List<ExternalPost> fetchPosts(long userId) {
		return ExternalCalls.call(SERVICE,
				restClient.get().uri(uri -> uri.path(POSTS_PATH).queryParam(USER_ID_PARAM, userId).build()),
				response -> response.body(POST_LIST));
	}

}
