package com.example.summary.client;

import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Builds a {@link RestClient} to simulate RestClient in spring context.
 */
final class TestRestClients {

	static final Duration READ_TIMEOUT = Duration.ofMillis(300);

	private TestRestClients() {
	}

	static RestClient restClient(String baseUrl) {
		HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(300)).build();
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
		requestFactory.setReadTimeout(READ_TIMEOUT);
		return RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build();
	}

}
