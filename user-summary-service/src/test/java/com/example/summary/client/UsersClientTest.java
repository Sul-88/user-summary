package com.example.summary.client;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.example.summary.client.ExternalServiceException.Reason;
import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;

class UsersClientTest {

	@RegisterExtension
	static WireMockExtension wireMock = WireMockExtension.newInstance()
			.options(wireMockConfig().dynamicPort())
			.build();

	private UsersClient client;

	@BeforeEach
	void setUp() {
		client = new UsersClient(TestRestClients.restClient(wireMock.baseUrl()));
	}

	@Test
	void mapsUserAndIgnoresUnknownFields() {
		wireMock.stubFor(get(urlPathEqualTo("/users/1")).willReturn(okJson("""
				{"id": 1, "name": "Leanne Graham", "username": "Bret", "email": "Sincere@april.biz",
				 "address": {"city": "Gwenborough"}, "phone": "1-770-736-8031"}
				""")));

		ExternalUser user = client.fetchUser(1);

		assertThat(user).isEqualTo(new ExternalUser(1, "Leanne Graham", "Bret", "Sincere@april.biz"));
		wireMock.verify(1, getRequestedFor(urlPathEqualTo("/users/1")));
	}

	@Test
	void unknownUserIsNonRetryableNotFound() {
		wireMock.stubFor(get(urlPathEqualTo("/users/999")).willReturn(aResponse().withStatus(404).withBody("{}")));

		ExternalServiceException ex = fetchFailing(999);

		assertThat(ex.reason()).isEqualTo(Reason.NOT_FOUND);
		assertThat(ex.status()).isEqualTo(404);
		assertThat(ex.retryable()).isFalse();
		assertThat(ex).hasMessage("users service responded with HTTP 404");
	}

	@Test
	void internalServerErrorIsRetryableServerError() {
		wireMock.stubFor(get(urlPathEqualTo("/users/1")).willReturn(aResponse().withStatus(500)));

		ExternalServiceException ex = fetchFailing(1);

		assertThat(ex.reason()).isEqualTo(Reason.SERVER_ERROR);
		assertThat(ex.status()).isEqualTo(500);
		assertThat(ex.retryable()).isTrue();
	}

	@Test
	void slowResponseIsNonRetryableTimeout() {
		int delayMillis = (int) TestRestClients.READ_TIMEOUT.toMillis() * 3;
		wireMock.stubFor(get(urlPathEqualTo("/users/1")).willReturn(okJson("{\"id\": 1}").withFixedDelay(delayMillis)));

		ExternalServiceException ex = fetchFailing(1);

		assertThat(ex.reason()).isEqualTo(Reason.TIMEOUT);
		assertThat(ex.status()).isEqualTo(ExternalServiceException.NO_STATUS);
		assertThat(ex.retryable()).isFalse();
	}

	@Test
	void connectionResetIsRetryableConnectionFailure() {
		wireMock.stubFor(get(urlPathEqualTo("/users/1")).willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

		ExternalServiceException ex = fetchFailing(1);

		assertThat(ex.reason()).isEqualTo(Reason.CONNECTION_FAILED);
		assertThat(ex.status()).isEqualTo(ExternalServiceException.NO_STATUS);
		assertThat(ex.retryable()).isTrue();
	}

	@Test
	void malformedJsonIsInvalidResponse() {
		wireMock.stubFor(get(urlPathEqualTo("/users/1")).willReturn(okJson("{\"id\": 1, \"name\": ")));

		ExternalServiceException ex = fetchFailing(1);

		assertThat(ex.reason()).isEqualTo(Reason.INVALID_RESPONSE);
		assertThat(ex.retryable()).isFalse();
	}

	private ExternalServiceException fetchFailing(long userId) {
		ExternalServiceException ex = catchThrowableOfType(ExternalServiceException.class,
				() -> client.fetchUser(userId));
		assertThat(ex).isNotNull();
		return ex;
	}

}
