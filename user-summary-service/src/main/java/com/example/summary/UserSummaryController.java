package com.example.summary;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.example.summary.api.UserSummaryApi;
import com.example.summary.api.model.UserSummaryResponse;

@RestController
public class UserSummaryController implements UserSummaryApi {

	@Override
	public UserSummaryResponse getUserSummary(long id) {
		throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED);
	}

}
