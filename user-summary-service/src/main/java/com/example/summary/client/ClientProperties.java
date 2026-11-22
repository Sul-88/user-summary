package com.example.summary.client;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Connection settings for the external services.
 *
 * @param connectTimeout connect timeout shared by all clients
 * @param users users service settings
 * @param posts posts service settings
 */
@ConfigurationProperties("client")
public record ClientProperties(Duration connectTimeout, Service users, Service posts) {

	public record Service(String baseUrl, Duration readTimeout) {
	}

}
