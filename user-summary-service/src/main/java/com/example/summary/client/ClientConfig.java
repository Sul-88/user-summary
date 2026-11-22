package com.example.summary.client;

import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.resilience.annotation.EnableResilientMethods;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ClientProperties.class)
@EnableResilientMethods
class ClientConfig {

	@Bean
	UsersClient usersClient(RestClient.Builder builder, ClientProperties properties) {
		return new UsersClient(restClient(builder, properties.connectTimeout(), properties.users()));
	}

	@Bean
	PostsClient postsClient(RestClient.Builder builder, ClientProperties properties) {
		return new PostsClient(restClient(builder, properties.connectTimeout(), properties.posts()));
	}

	private static RestClient restClient(RestClient.Builder builder, Duration connectTimeout,
			ClientProperties.Service service) {
		HttpClient httpClient = HttpClient.newBuilder().connectTimeout(connectTimeout).build();
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
		requestFactory.setReadTimeout(service.readTimeout());
		return builder.clone()
				.baseUrl(service.baseUrl())
				.requestFactory(requestFactory)
				.build();
	}

}
