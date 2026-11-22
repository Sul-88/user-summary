package com.example.summary.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * User as returned by the external users service.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ExternalUser(long id, String name, String username, String email) {
}
