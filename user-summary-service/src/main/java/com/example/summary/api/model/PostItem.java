package com.example.summary.api.model;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A single post of the user.
 */
public record PostItem(
		@Schema(requiredMode = REQUIRED) long id,
		@Schema(requiredMode = REQUIRED) String title,
		@Schema(requiredMode = REQUIRED) String body) {
}
