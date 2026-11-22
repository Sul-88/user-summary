package com.example.summary.client;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.example.summary.client.ExternalServiceException.Reason;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;

class PostsClientTest {

	@RegisterExtension
	static WireMockExtension wireMock = WireMockExtension.newInstance()
			.options(wireMockConfig().dynamicPort())
			.build();

	private PostsClient client;

	@BeforeEach
	void setUp() {
		client = new PostsClient(TestRestClients.restClient(wireMock.baseUrl()));
	}

	@Test
	void mapsPostsAndSendsUserIdQueryParam() {
		wireMock.stubFor(get(urlPathEqualTo("/posts")).withQueryParam("userId", equalTo("1")).willReturn(okJson("""
				[{"userId": 1, "id": 1, "title": "first", "body": "one"},
				 {"userId": 1, "id": 2, "title": "second", "body": "two"}]
				""")));

		List<ExternalPost> posts = client.fetchPosts(1);

		assertThat(posts).containsExactly(
				new ExternalPost(1, 1, "first", "one"),
				new ExternalPost(2, 1, "second", "two"));
		wireMock.verify(1, getRequestedFor(urlPathEqualTo("/posts")).withQueryParam("userId", equalTo("1")));
	}

	@Test
	void emptyArrayIsEmptyList() {
		wireMock.stubFor(get(urlPathEqualTo("/posts")).willReturn(okJson("[]")));

		assertThat(client.fetchPosts(1)).isEmpty();
	}

	@Test
	void serviceUnavailableIsRetryableServerError() {
		wireMock.stubFor(get(urlPathEqualTo("/posts")).willReturn(aResponse().withStatus(503)));

		ExternalServiceException ex = fetchFailing(1);

		assertThat(ex.reason()).isEqualTo(Reason.SERVER_ERROR);
		assertThat(ex.status()).isEqualTo(503);
		assertThat(ex.retryable()).isTrue();
	}

	@Test
	void badRequestIsNonRetryableClientError() {
		wireMock.stubFor(get(urlPathEqualTo("/posts")).willReturn(aResponse().withStatus(400)));

		ExternalServiceException ex = fetchFailing(1);

		assertThat(ex.reason()).isEqualTo(Reason.CLIENT_ERROR);
		assertThat(ex.status()).isEqualTo(400);
		assertThat(ex.retryable()).isFalse();
	}

	private ExternalServiceException fetchFailing(long userId) {
		ExternalServiceException ex = catchThrowableOfType(ExternalServiceException.class,
				() -> client.fetchPosts(userId));
		assertThat(ex).isNotNull();
		return ex;
	}

}
