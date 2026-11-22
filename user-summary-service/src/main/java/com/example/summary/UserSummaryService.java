package com.example.summary;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.summary.api.model.PostItem;
import com.example.summary.api.model.PostsResult;
import com.example.summary.api.model.UserSummaryResponse;
import com.example.summary.client.ExternalPost;
import com.example.summary.client.ExternalServiceException;
import com.example.summary.client.ExternalServiceException.Reason;
import com.example.summary.client.ExternalUser;
import com.example.summary.client.PostsClient;
import com.example.summary.client.UsersClient;

/**
 * Fetches a user and their posts concurrently and merges them into a {@link UserSummaryResponse}.
 */
public class UserSummaryService {

	private static final Logger LOGGER = LoggerFactory.getLogger(UserSummaryService.class);

	private final UsersClient usersClient;

	private final PostsClient postsClient;

	private final ExecutorService executorService;

	private final Duration userTimeout;

	private final Duration postsTotalTimeout;

	public UserSummaryService(UsersClient usersClient, PostsClient postsClient, ExecutorService executorService,
			SummaryProperties properties) {
		this.usersClient = usersClient;
		this.postsClient = postsClient;
		this.executorService = executorService;
		this.userTimeout = properties.userTimeout();
		this.postsTotalTimeout = properties.postsTotalTimeout();
	}

	public UserSummaryResponse summarize(long userId) {
		CompletableFuture<ExternalUser> userFuture = CompletableFuture
				.supplyAsync(() -> usersClient.fetchUser(userId), executorService)
				.orTimeout(userTimeout.toMillis(), TimeUnit.MILLISECONDS);

		CompletableFuture<PostsResult> postsFuture = CompletableFuture
				.supplyAsync(() -> postsClient.fetchPosts(userId), executorService)
				.orTimeout(postsTotalTimeout.toMillis(), TimeUnit.MILLISECONDS)
				.thenApply(posts -> PostsResult.ok(toPostItems(posts)))
				.exceptionally(ex -> {
					LOGGER.warn("Posts of user {} unavailable, responding without them: {}", userId, describe(ex));
					return PostsResult.unavailable();
				});

		ExternalUser user;
		try {
			user = userFuture.join();
		}
		catch (CompletionException ex) {
			postsFuture.cancel(true);
			throw toUserFailure(userId, unwrap(ex));
		}

		return new UserSummaryResponse(user.id(), user.name(), user.username(), user.email(), postsFuture.join());
	}

	private RuntimeException toUserFailure(long userId, Throwable cause) {
		if (cause instanceof ExternalServiceException ex && ex.reason() == Reason.NOT_FOUND) {
			return new UserNotFoundException(userId);
		}
		boolean timeout = cause instanceof TimeoutException
				|| (cause instanceof ExternalServiceException ex && ex.reason() == Reason.TIMEOUT);
		LOGGER.warn("Fetching user {} failed: {}", userId, describe(cause));
		return new UserServiceUnavailableException("Fetching user " + userId + " failed", cause, timeout);
	}

	private static List<PostItem> toPostItems(List<ExternalPost> posts) {
		return posts.stream().map(post -> new PostItem(post.id(), post.title(), post.body())).toList();
	}

	private static Throwable unwrap(Throwable ex) {
		Throwable current = ex;
		while ((current instanceof CompletionException || current instanceof ExecutionException)
				&& current.getCause() != null) {
			current = current.getCause();
		}
		return current;
	}

	private static String describe(Throwable ex) {
		Throwable cause = unwrap(ex);
		if (cause instanceof ExternalServiceException external) {
			return external.reason() + (external.status() != ExternalServiceException.NO_STATUS
					? " (HTTP " + external.status() + ")" : "");
		}
		if (cause instanceof TimeoutException) {
			return "time limit exceeded";
		}
		return cause.toString();
	}

}
