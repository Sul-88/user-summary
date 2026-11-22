package com.example.summary;

import org.springframework.web.bind.annotation.RestController;

import com.example.summary.api.UserSummaryApi;
import com.example.summary.api.model.UserSummaryResponse;

@RestController
public class UserSummaryController implements UserSummaryApi {

	private final UserSummaryService userSummaryService;

	public UserSummaryController(UserSummaryService userSummaryService) {
		this.userSummaryService = userSummaryService;
	}

	@Override
	public UserSummaryResponse getUserSummary(long id) {
		return userSummaryService.summarize(id);
	}

}
