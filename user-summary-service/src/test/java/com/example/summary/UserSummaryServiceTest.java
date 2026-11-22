package com.example.summary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.example.summary.api.model.PostItem;
import com.example.summary.api.model.PostsResult;
import com.example.summary.api.model.PostsStatus;
import com.example.summary.api.model.UserSummaryResponse;
import com.example.summary.client.ExternalPost;
import com.example.summary.client.ExternalServiceException;
import com.example.summary.client.ExternalServiceException.Reason;
import com.example.summary.client.ExternalUser;
import com.example.summary.client.PostsClient;
import com.example.summary.client.UsersClient;

/**
 * Test for {@link UserSummaryService}. The clients are mocked, the executor is a real virtual-thread executor.
 */
class UserSummaryServiceTest {

	private static final long USER_ID = 1;

	private static final Duration USER_TIMEOUT = Duration.ofMillis(500);

	private static final Duration POSTS_TOTAL_TIMEOUT = Duration.ofMillis(300);

	private static final ExternalUser USER = new ExternalUser(USER_ID, "Leanne Graham", "Bret", "Sincere@april.biz");

	private final UsersClient usersClient = mock(UsersClient.class);

	private final PostsClient postsClient = mock(PostsClient.class);

	private ExecutorService executorService;

	private UserSummaryService service;

	@BeforeEach
	void setUp() {
		executorService = Executors.newVirtualThreadPerTaskExecutor();
		service = new UserSummaryService(usersClient, postsClient, executorService,
				new SummaryProperties(USER_TIMEOUT, POSTS_TOTAL_TIMEOUT));
	}

	@AfterEach
	void tearDown() {
		executorService.shutdownNow();
	}

	@Nested
	class Success {

		@Test
		void mergesUserAndPosts() {
			given(usersClient.fetchUser(USER_ID)).willReturn(USER);
			given(postsClient.fetchPosts(USER_ID)).willReturn(List.of(
					new ExternalPost(10, USER_ID, "first", "one"),
					new ExternalPost(11, USER_ID, "second", "two")));

			UserSummaryResponse summary = service.summarize(USER_ID);

			assertThat(summary).isEqualTo(new UserSummaryResponse(USER_ID, "Leanne Graham", "Bret",
					"Sincere@april.biz", PostsResult.ok(List.of(
							new PostItem(10, "first", "one"),
							new PostItem(11, "second", "two")))));
		}

		@Test
		void userWithoutPostsHasEmptyOkPosts() {
			given(usersClient.fetchUser(USER_ID)).willReturn(USER);
			given(postsClient.fetchPosts(USER_ID)).willReturn(List.of());

			assertThat(service.summarize(USER_ID).posts()).isEqualTo(PostsResult.ok(List.of()));
		}

		@Test
		void fetchesUserAndPostsConcurrently() {
			// Each call waits until the other one has started. Run one after the other, the first call
			// would time out on the latch and fail.
			CountDownLatch bothStarted = new CountDownLatch(2);
			given(usersClient.fetchUser(USER_ID)).willAnswer(invocation -> {
				awaitOther(bothStarted);
				return USER;
			});
			given(postsClient.fetchPosts(USER_ID)).willAnswer(invocation -> {
				awaitOther(bothStarted);
				return List.of();
			});

			UserSummaryResponse summary = service.summarize(USER_ID);

			assertThat(summary.id()).isEqualTo(USER_ID);
			assertThat(summary.posts().status()).isEqualTo(PostsStatus.OK);
		}

	}

	@Nested
	class PostsFailure {

		@ParameterizedTest
		@EnumSource(Reason.class)
		void anyPostsServiceErrorDegradesToUnavailable(Reason reason) {
			given(usersClient.fetchUser(USER_ID)).willReturn(USER);
			given(postsClient.fetchPosts(USER_ID)).willThrow(externalFailure(reason));

			UserSummaryResponse summary = service.summarize(USER_ID);

			assertThat(summary.id()).isEqualTo(USER_ID);
			assertThat(summary.name()).isEqualTo("Leanne Graham");
			assertThat(summary.posts()).isEqualTo(PostsResult.unavailable());
		}

		@Test
		void unexpectedPostsExceptionDegradesToUnavailable() {
			given(usersClient.fetchUser(USER_ID)).willReturn(USER);
			given(postsClient.fetchPosts(USER_ID)).willThrow(new IllegalStateException("bug"));

			assertThat(service.summarize(USER_ID).posts()).isEqualTo(PostsResult.unavailable());
		}

		@Test
		void slowPostsDegradeToUnavailableAfterTotalTimeout() {
			given(usersClient.fetchUser(USER_ID)).willReturn(USER);
			given(postsClient.fetchPosts(USER_ID)).willAnswer(invocation -> {
				Thread.sleep(POSTS_TOTAL_TIMEOUT.multipliedBy(10).toMillis());
				return List.of();
			});

			long start = System.nanoTime();
			UserSummaryResponse summary = service.summarize(USER_ID);
			Duration elapsed = Duration.ofNanos(System.nanoTime() - start);

			assertThat(summary.posts()).isEqualTo(PostsResult.unavailable());
			// Responds right after the posts time limit, without waiting for the slow call
			assertThat(elapsed).isLessThan(POSTS_TOTAL_TIMEOUT.multipliedBy(5));
		}

	}

	@Nested
	class UserFailure {

		@Test
		void notFoundBecomesUserNotFound() {
			given(usersClient.fetchUser(USER_ID)).willThrow(externalFailure(Reason.NOT_FOUND));
			given(postsClient.fetchPosts(USER_ID)).willReturn(List.of());

			UserNotFoundException ex = catchThrowableOfType(UserNotFoundException.class,
					() -> service.summarize(USER_ID));

			assertThat(ex).isNotNull().hasMessage("User 1 not found");
			assertThat(ex.userId()).isEqualTo(USER_ID);
		}

		@Test
		void clientTimeoutBecomesUnavailableWithTimeoutFlag() {
			ExternalServiceException cause = externalFailure(Reason.TIMEOUT);
			given(usersClient.fetchUser(USER_ID)).willThrow(cause);
			given(postsClient.fetchPosts(USER_ID)).willReturn(List.of());

			UserServiceUnavailableException ex = summarizeFailing();

			assertThat(ex.timeout()).isTrue();
			assertThat(ex).hasMessage("Fetching user 1 failed").hasCause(cause);
		}

		@Test
		void exceedingUserTimeoutBecomesUnavailableWithTimeoutFlag() {
			given(usersClient.fetchUser(USER_ID)).willAnswer(invocation -> {
				Thread.sleep(USER_TIMEOUT.multipliedBy(10).toMillis());
				return USER;
			});
			given(postsClient.fetchPosts(USER_ID)).willReturn(List.of());

			long start = System.nanoTime();
			UserServiceUnavailableException ex = summarizeFailing();
			Duration elapsed = Duration.ofNanos(System.nanoTime() - start);

			assertThat(ex.timeout()).isTrue();
			assertThat(ex).hasCauseInstanceOf(TimeoutException.class);
			assertThat(elapsed).isLessThan(USER_TIMEOUT.multipliedBy(5));
		}

		@ParameterizedTest
		@EnumSource(value = Reason.class, names = { "CLIENT_ERROR", "SERVER_ERROR", "CONNECTION_FAILED",
				"INVALID_RESPONSE" })
		void otherServiceErrorsBecomeUnavailableWithoutTimeoutFlag(Reason reason) {
			ExternalServiceException cause = externalFailure(reason);
			given(usersClient.fetchUser(USER_ID)).willThrow(cause);
			given(postsClient.fetchPosts(USER_ID)).willReturn(List.of());

			UserServiceUnavailableException ex = summarizeFailing();

			assertThat(ex.timeout()).isFalse();
			assertThat(ex).hasCause(cause);
		}

		@Test
		void unexpectedExceptionBecomesUnavailableWithoutTimeoutFlag() {
			IllegalStateException cause = new IllegalStateException("bug");
			given(usersClient.fetchUser(USER_ID)).willThrow(cause);
			given(postsClient.fetchPosts(USER_ID)).willReturn(List.of());

			UserServiceUnavailableException ex = summarizeFailing();

			assertThat(ex.timeout()).isFalse();
			assertThat(ex).hasCause(cause);
		}

		@Test
		void failsFastWithoutWaitingForPosts() {
			// Posts are slow but still within their time limit; the user failure must not wait for them.
			Duration postsTimeout = Duration.ofSeconds(5);
			service = new UserSummaryService(usersClient, postsClient, executorService,
					new SummaryProperties(USER_TIMEOUT, postsTimeout));
			given(usersClient.fetchUser(USER_ID)).willThrow(externalFailure(Reason.NOT_FOUND));
			given(postsClient.fetchPosts(USER_ID)).willAnswer(invocation -> {
				Thread.sleep(postsTimeout.minusSeconds(1).toMillis());
				return List.of();
			});

			long start = System.nanoTime();
			UserNotFoundException ex = catchThrowableOfType(UserNotFoundException.class,
					() -> service.summarize(USER_ID));
			Duration elapsed = Duration.ofNanos(System.nanoTime() - start);

			assertThat(ex).isNotNull();
			assertThat(elapsed).isLessThan(Duration.ofSeconds(1));
		}

	}

	private UserServiceUnavailableException summarizeFailing() {
		UserServiceUnavailableException ex = catchThrowableOfType(UserServiceUnavailableException.class,
				() -> service.summarize(USER_ID));
		assertThat(ex).as("expected UserServiceUnavailableException").isNotNull();
		return ex;
	}

	private static ExternalServiceException externalFailure(Reason reason) {
		int status = switch (reason) {
			case NOT_FOUND -> 404;
			case CLIENT_ERROR -> 400;
			case SERVER_ERROR -> 503;
			default -> ExternalServiceException.NO_STATUS;
		};
		return new ExternalServiceException(reason, status, "simulated " + reason, null);
	}

	private static void awaitOther(CountDownLatch bothStarted) throws InterruptedException {
		bothStarted.countDown();
		if (!bothStarted.await(USER_TIMEOUT.dividedBy(2).toMillis(), TimeUnit.MILLISECONDS)) {
			throw new IllegalStateException("calls did not run concurrently");
		}
	}

}
