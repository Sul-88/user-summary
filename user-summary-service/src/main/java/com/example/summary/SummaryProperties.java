package com.example.summary;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Time limits of the summary endpoint.
 *
 * @param userTimeout time limit for the user call.
 * @param postsTotalTimeout time limit for the posts call  including retries.
 */
@ConfigurationProperties("summary")
public record SummaryProperties(Duration userTimeout, Duration postsTotalTimeout) {
}
