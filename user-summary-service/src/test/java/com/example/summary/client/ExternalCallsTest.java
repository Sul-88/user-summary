package com.example.summary.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withRawStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseCreator;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.example.summary.client.ExternalServiceException.Reason;

/**
 * Unit tests for {@link ExternalCalls}
 */
class ExternalCallsTest {

	private static final String BASE_URL = "http://users.test";

	private static final String USER_URL = BASE_URL + "/users/1";

	private static final String USER_JSON = """
			{"id": 1, "name": "Leanne Graham", "username": "Bret", "email": "leanne@example.com", "phone": "ignored"}
			""";

	private MockRestServiceServer server;

	private RestClient restClient;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
		server = MockRestServiceServer.bindTo(builder).build();
		restClient = builder.build();
	}

	@Test
	void returnsExtractedBodyOnSuccess() {
		respondWith(withSuccess(USER_JSON, MediaType.APPLICATION_JSON));

		ExternalUser user = fetchUser();

		assertThat(user).isEqualTo(new ExternalUser(1, "Leanne Graham", "Bret", "leanne@example.com"));
		server.verify();
	}

	@Nested
	class HttpErrorStatus {

		@Test
		void notFoundIsNonRetryableNotFound() {
			respondWith(withStatus(HttpStatus.NOT_FOUND));

			ExternalServiceException ex = fetchFailing();

			assertThat(ex.reason()).isEqualTo(Reason.NOT_FOUND);
			assertThat(ex.status()).isEqualTo(404);
			assertThat(ex.retryable()).isFalse();
			assertThat(ex).hasMessage("users service responded with HTTP 404");
		}

		@ParameterizedTest
		@ValueSource(ints = { 400, 401, 403, 409, 422, 429 })
		void otherClientErrorsAreNonRetryableClientErrors(int status) {
			respondWith(withRawStatus(status));

			ExternalServiceException ex = fetchFailing();

			assertThat(ex.reason()).isEqualTo(Reason.CLIENT_ERROR);
			assertThat(ex.status()).isEqualTo(status);
			assertThat(ex.retryable()).isFalse();
		}

		@ParameterizedTest
		@ValueSource(ints = { 500, 502, 503, 504 })
		void serverErrorsAreRetryableServerErrors(int status) {
			respondWith(withRawStatus(status));

			ExternalServiceException ex = fetchFailing();

			assertThat(ex.reason()).isEqualTo(Reason.SERVER_ERROR);
			assertThat(ex.status()).isEqualTo(status);
			assertThat(ex.retryable()).isTrue();
			assertThat(ex).hasMessage("users service responded with HTTP " + status);
		}

		@Test
		void exceptionFromStatusHandlerIsNotRewrapped() {
			respondWith(withRawStatus(500).body("{\"error\": \"boom\"}").contentType(MediaType.APPLICATION_JSON));

			ExternalServiceException ex = fetchFailing();

			// Handler output is passed through as-is: no ResourceAccessException/RestClientException cause
			assertThat(ex.getCause()).isNull();
		}

	}

	@Nested
	class IoFailure {

		@Test
		void httpTimeoutIsNonRetryableTimeout() {
			respondWith(withException(new HttpTimeoutException("request timed out")));

			ExternalServiceException ex = fetchFailing();

			assertThat(ex.reason()).isEqualTo(Reason.TIMEOUT);
			assertThat(ex.status()).isEqualTo(ExternalServiceException.NO_STATUS);
			assertThat(ex.retryable()).isFalse();
			assertThat(ex).hasMessage("users service call failed: TIMEOUT")
				.hasCauseInstanceOf(ResourceAccessException.class)
				.hasRootCauseInstanceOf(HttpTimeoutException.class);
		}

		@Test
		void socketTimeoutIsTimeout() {
			respondWith(withException(new SocketTimeoutException("Read timed out")));

			assertThat(fetchFailing().reason()).isEqualTo(Reason.TIMEOUT);
		}

		@Test
		void timeoutDeepInCauseChainIsDetected() {
			respondWith(withException(new IOException("wrapper", new SocketTimeoutException("Read timed out"))));

			assertThat(fetchFailing().reason()).isEqualTo(Reason.TIMEOUT);
		}

		@Test
		void connectTimeoutIsClassifiedAsTimeoutNotConnectionFailure() {
			// HttpConnectTimeoutException extends HttpTimeoutException -> TIMEOUT (and therefore not retryable)
			respondWith(withException(new HttpConnectTimeoutException("HTTP connect timed out")));

			ExternalServiceException ex = fetchFailing();

			assertThat(ex.reason()).isEqualTo(Reason.TIMEOUT);
			assertThat(ex.retryable()).isFalse();
		}

		@Test
		void connectionRefusedIsRetryableConnectionFailure() {
			respondWith(withException(new ConnectException("Connection refused")));

			ExternalServiceException ex = fetchFailing();

			assertThat(ex.reason()).isEqualTo(Reason.CONNECTION_FAILED);
			assertThat(ex.status()).isEqualTo(ExternalServiceException.NO_STATUS);
			assertThat(ex.retryable()).isTrue();
			assertThat(ex).hasMessage("users service call failed: CONNECTION_FAILED")
				.hasCauseInstanceOf(ResourceAccessException.class)
				.hasRootCauseInstanceOf(ConnectException.class);
		}

		@Test
		void unknownHostIsConnectionFailure() {
			respondWith(withException(new UnknownHostException("users.test")));

			assertThat(fetchFailing().reason()).isEqualTo(Reason.CONNECTION_FAILED);
		}

	}

	@Nested
	class InvalidResponse {

		@Test
		void malformedJsonIsInvalidResponse() {
			respondWith(withSuccess("{\"id\": 1, \"name\": ", MediaType.APPLICATION_JSON));

			ExternalServiceException ex = fetchFailing();

			assertInvalidResponse(ex, "users service returned an invalid response");
			assertThat(ex).hasCauseInstanceOf(RestClientException.class);
		}

		@Test
		void wrongJsonShapeIsInvalidResponse() {
			respondWith(withSuccess("[1, 2, 3]", MediaType.APPLICATION_JSON));

			assertInvalidResponse(fetchFailing(), "users service returned an invalid response");
		}

		@Test
		void unsupportedContentTypeIsInvalidResponse() {
			respondWith(withSuccess("<html>maintenance</html>", MediaType.TEXT_HTML));

			ExternalServiceException ex = fetchFailing();

			assertInvalidResponse(ex, "users service returned an invalid response");
			assertThat(ex).hasCauseInstanceOf(RestClientException.class);
		}

		@Test
		void failureWhileReadingBodyIsInvalidResponse() {
			// Headers arrive fine, the body stream breaks. Spring wraps this in a plain RestClientException
			// (not ResourceAccessException), so it is classified as INVALID_RESPONSE, not as an I/O failure.
			respondWith(request -> {
				MockClientHttpResponse response = new MockClientHttpResponse(failingStream(), HttpStatus.OK);
				response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
				return response;
			});

			assertInvalidResponse(fetchFailing(), "users service returned an invalid response");
		}

	}

	@Nested
	class EmptyBody {

		@Test
		void noContentIsInvalidResponse() {
			respondWith(withNoContent());

			ExternalServiceException ex = fetchFailing();

			assertInvalidResponse(ex, "users service returned an empty body");
			assertThat(ex.getCause()).isNull();
		}

		@Test
		void okWithEmptyBodyIsInvalidResponse() {
			respondWith(withSuccess("", MediaType.APPLICATION_JSON));

			assertInvalidResponse(fetchFailing(), "users service returned an empty body");
		}

		@Test
		void jsonNullLiteralIsInvalidResponse() {
			respondWith(withSuccess("null", MediaType.APPLICATION_JSON));

			assertInvalidResponse(fetchFailing(), "users service returned an empty body");
		}

		@Test
		void redirectIsNotAnErrorButYieldsEmptyBody() {
			// 3xx is not HttpStatusCode#isError, so the status handler does not fire
			respondWith(withStatus(HttpStatus.FOUND).location(URI.create(BASE_URL + "/elsewhere")));

			assertInvalidResponse(fetchFailing(), "users service returned an empty body");
		}

	}

	private void respondWith(ResponseCreator responseCreator) {
		server.expect(requestTo(USER_URL)).andExpect(method(HttpMethod.GET)).andRespond(responseCreator);
	}

	private ExternalUser fetchUser() {
		return ExternalCalls.call("users", restClient.get().uri("/users/{id}", 1),
				response -> response.body(ExternalUser.class));
	}

	private ExternalServiceException fetchFailing() {
		ExternalServiceException ex = catchThrowableOfType(ExternalServiceException.class, this::fetchUser);
		assertThat(ex).as("expected ExternalServiceException").isNotNull();
		server.verify();
		return ex;
	}

	private static void assertInvalidResponse(ExternalServiceException ex, String message) {
		assertThat(ex.reason()).isEqualTo(Reason.INVALID_RESPONSE);
		assertThat(ex.status()).isEqualTo(ExternalServiceException.NO_STATUS);
		assertThat(ex.retryable()).isFalse();
		assertThat(ex).hasMessage(message);
	}

	private static InputStream failingStream() {
		return new InputStream() {

			@Override
			public int read() throws IOException {
				throw new IOException("Connection reset");
			}

		};
	}

}
