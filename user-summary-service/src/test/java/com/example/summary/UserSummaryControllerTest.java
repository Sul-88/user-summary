package com.example.summary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import com.example.summary.api.model.PostItem;
import com.example.summary.api.model.PostsResult;
import com.example.summary.api.model.UserSummaryResponse;

/**
 * Test for {@link UserSummaryController}.
 */
@WebMvcTest(UserSummaryController.class)
class UserSummaryControllerTest {

	private static final String SUMMARY_URL = "/users/{id}/summary";

	@Autowired
	private MockMvcTester mvc;

	@MockitoBean
	private UserSummaryService userSummaryService;

	@Test
	void returnsSummaryWithPosts() {
		given(userSummaryService.summarize(1)).willReturn(new UserSummaryResponse(1, "Leanne Graham", "Bret",
				"Sincere@april.biz", PostsResult.ok(List.of(new PostItem(10, "title", "body")))));

		assertThat(mvc.get().uri(SUMMARY_URL, 1).accept(MediaType.APPLICATION_JSON))
			.hasStatusOk()
			.hasContentTypeCompatibleWith(MediaType.APPLICATION_JSON)
			.bodyJson()
			.isStrictlyEqualTo("""
					{
					  "id": 1,
					  "name": "Leanne Graham",
					  "username": "Bret",
					  "email": "Sincere@april.biz",
					  "posts": {
					    "status": "OK",
					    "items": [{"id": 10, "title": "title", "body": "body"}]
					  }
					}
					""");
		verify(userSummaryService).summarize(1);
	}

	@Test
	void returnsSummaryWithEmptyPostList() {
		given(userSummaryService.summarize(2))
			.willReturn(new UserSummaryResponse(2, "Ervin Howell", "Antonette", "Shanna@melissa.tv",
					PostsResult.ok(List.of())));

		assertThat(mvc.get().uri(SUMMARY_URL, 2))
			.hasStatusOk()
			.bodyJson()
			.isLenientlyEqualTo("""
					{"id": 2, "posts": {"status": "OK", "items": []}}
					""");
	}

	@Test
	void unavailablePostsAreStill200WithoutItems() {
		given(userSummaryService.summarize(1)).willReturn(new UserSummaryResponse(1, "Leanne Graham", "Bret",
				"Sincere@april.biz", PostsResult.unavailable()));

		assertThat(mvc.get().uri(SUMMARY_URL, 1))
			.hasStatusOk()
			.bodyJson()
			.satisfies(json -> {
				assertThat(json).extractingPath("$.posts.status").isEqualTo("UNAVAILABLE");
				// PostsResult is @JsonInclude(NON_NULL): no "items": null in the response
				assertThat(json).doesNotHavePath("$.posts.items");
			});
	}

	@ParameterizedTest
	@ValueSource(longs = { 0, -1, Long.MIN_VALUE })
	void nonPositiveIdIsBadRequest(long id) {
		assertThat(mvc.get().uri(SUMMARY_URL, id))
			.hasStatus(HttpStatus.BAD_REQUEST)
			.hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
		verifyNoInteractions(userSummaryService);
	}

	@ParameterizedTest
	@ValueSource(strings = { "abc", "1.5", "99999999999999999999" })
	void nonNumericIdIsBadRequest(String id) {
		assertThat(mvc.get().uri(SUMMARY_URL, id))
			.hasStatus(HttpStatus.BAD_REQUEST)
			.hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
		verifyNoInteractions(userSummaryService);
	}

	@Test
	void unknownPathIsNotFound() {
		assertThat(mvc.get().uri("/users/1")).hasStatus(HttpStatus.NOT_FOUND);
		verifyNoInteractions(userSummaryService);
	}

	@Test
	void onlyGetIsAllowed() {
		assertThat(mvc.post().uri(SUMMARY_URL, 1)).hasStatus(HttpStatus.METHOD_NOT_ALLOWED);
		verifyNoInteractions(userSummaryService);
	}

}
