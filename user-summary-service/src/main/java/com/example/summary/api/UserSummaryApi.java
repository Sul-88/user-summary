package com.example.summary.api;

import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import com.example.summary.api.model.UserSummaryResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Min;

@Tag(name = "UserSummary")
@RequestMapping("/users")
public interface UserSummaryApi {

	String PROBLEM_JSON = "application/problem+json";

	@Operation(
			summary = "User plus their posts",
			description = "The user is mandatory: if it cannot be fetched, the request fails (404/502/504). "
					+ "Posts are optional: if the posts service fails or exceeds its time limit, the "
					+ "response is still 200 with posts.status = UNAVAILABLE.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Summary. posts.status may be UNAVAILABLE."),
			@ApiResponse(responseCode = "400", description = "INVALID_USER_ID – id is not a positive number",
					content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class))),
			@ApiResponse(responseCode = "404", description = "USER_NOT_FOUND – users service does not know the user",
					content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class))),
			@ApiResponse(responseCode = "500", description = "INTERNAL_ERROR – unexpected internal error",
					content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class))),
			@ApiResponse(responseCode = "502", description = "USER_SERVICE_ERROR – users service failed or returned an invalid response",
					content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class))),
			@ApiResponse(responseCode = "504", description = "USER_SERVICE_TIMEOUT – users service did not answer in time",
					content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
	})
	@GetMapping(value = "/{id}/summary", produces = MediaType.APPLICATION_JSON_VALUE)
	UserSummaryResponse getUserSummary(@PathVariable("id") @Min(1) long id);

}
