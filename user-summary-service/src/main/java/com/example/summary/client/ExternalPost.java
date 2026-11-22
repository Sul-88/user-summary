package com.example.summary.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Post as returned by the external posts service.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ExternalPost(long id, long userId, String title, String body) {
}
