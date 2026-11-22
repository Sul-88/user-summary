package com.example.summary;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Checks the error mapping and that error responses are {@code application/problem+json}
 * although the endpoint declares {@code produces = application/json}.
 */
@WebMvcTest(UserSummaryController.class)
class ApiExceptionHandlerTest {

	private static final MediaType PROBLEM_JSON = MediaType.APPLICATION_PROBLEM_JSON;

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private UserSummaryService userSummaryService;

	@Test
	void userNotFound() throws Exception {
		given(userSummaryService.summarize(7)).willThrow(new UserNotFoundException(7));

		mockMvc.perform(get("/users/7/summary"))
				.andExpect(status().isNotFound())
				.andExpect(content().contentType(PROBLEM_JSON))
				.andExpect(jsonPath("$.code").value("USER_NOT_FOUND"))
				.andExpect(jsonPath("$.detail").value("User 7 not found"));
	}

	@Test
	void userServiceTimeout() throws Exception {
		given(userSummaryService.summarize(1))
				.willThrow(new UserServiceUnavailableException("timed out", null, true));

		mockMvc.perform(get("/users/1/summary"))
				.andExpect(status().isGatewayTimeout())
				.andExpect(content().contentType(PROBLEM_JSON))
				.andExpect(jsonPath("$.code").value("USER_SERVICE_TIMEOUT"))
				.andExpect(jsonPath("$.detail").value("User data is temporarily unavailable"));
	}

	@Test
	void userServiceError() throws Exception {
		given(userSummaryService.summarize(1)).willThrow(new UserServiceUnavailableException(
				"failed: https://upstream.example/users/1", new IllegalStateException("upstream body"), false));

		mockMvc.perform(get("/users/1/summary"))
				.andExpect(status().isBadGateway())
				.andExpect(content().contentType(PROBLEM_JSON))
				.andExpect(jsonPath("$.code").value("USER_SERVICE_ERROR"))
				.andExpect(jsonPath("$.detail").value("User data is temporarily unavailable"))
				.andExpect(content().string(not(containsString("upstream"))));
	}

	@ParameterizedTest
	@ValueSource(strings = { "0", "-1", "abc" })
	void invalidUserId(String id) throws Exception {
		mockMvc.perform(get("/users/{id}/summary", id))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentType(PROBLEM_JSON))
				.andExpect(jsonPath("$.code").value("INVALID_USER_ID"));

		verifyNoInteractions(userSummaryService);
	}

	@Test
	void unexpectedError() throws Exception {
		given(userSummaryService.summarize(anyLong())).willThrow(new IllegalStateException("secret internals"));

		mockMvc.perform(get("/users/1/summary"))
				.andExpect(status().isInternalServerError())
				.andExpect(content().contentType(PROBLEM_JSON))
				.andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
				.andExpect(content().string(not(containsString("secret"))));
	}

	@Test
	void unknownPathKeepsSpringHandling() throws Exception {
		mockMvc.perform(get("/does-not-exist"))
				.andExpect(status().isNotFound())
				.andExpect(content().contentType(PROBLEM_JSON))
				.andExpect(jsonPath("$.code").doesNotExist());
	}

	@Test
	void wrongMethodKeepsSpringHandling() throws Exception {
		mockMvc.perform(post("/users/1/summary"))
				.andExpect(status().isMethodNotAllowed())
				.andExpect(content().contentType(PROBLEM_JSON))
				.andExpect(jsonPath("$.code").doesNotExist());
	}

}
