package com.example.summary.api.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

/**
 * Result of fetching the user's posts.
 */
@Schema(description = "Result of fetching the user's posts.")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PostsResult(
        @Schema(requiredMode = REQUIRED) PostsStatus status,
        List<PostItem> items) {

    public static PostsResult ok(List<PostItem> items) {
        return new PostsResult(PostsStatus.OK, items);
    }

    public static PostsResult unavailable() {
        return new PostsResult(PostsStatus.UNAVAILABLE, null);
    }

}
