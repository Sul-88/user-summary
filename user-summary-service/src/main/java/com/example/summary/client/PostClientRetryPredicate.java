package com.example.summary.client;

import java.lang.reflect.Method;

import org.springframework.resilience.retry.MethodRetryPredicate;

/**
 * A Predicate for {@link PostsClient}
 */
public class PostClientRetryPredicate implements MethodRetryPredicate {

	@Override
	public boolean shouldRetry(Method method, Throwable throwable) {
		return throwable instanceof ExternalServiceException ex && ex.retryable();
	}

}
