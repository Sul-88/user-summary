package com.example.summary.api.model;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Summary of a user combined with the result of fetching their posts.
 */
public record UserSummaryResponse(
        @Schema(requiredMode = REQUIRED) long id,
        @Schema(requiredMode = REQUIRED) String name,
        @Schema(requiredMode = REQUIRED) String username,
        @Schema(requiredMode = REQUIRED) String email,
        @Schema(requiredMode = REQUIRED) PostsResult posts) {
}
