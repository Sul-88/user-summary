package com.example.summary;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.example.summary.client.PostsClient;
import com.example.summary.client.UsersClient;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SummaryProperties.class)
class SummaryConfig {

	@Bean(destroyMethod = "close")
	ExecutorService executorService() {
		return Executors.newVirtualThreadPerTaskExecutor();
	}

	@Bean
	UserSummaryService userSummaryService(UsersClient usersClient, PostsClient postsClient,
			ExecutorService executorService, SummaryProperties properties) {
		return new UserSummaryService(usersClient, postsClient, executorService, properties);
	}

}
