package com.example.summary;

import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.servers.Server;

@Configuration
@OpenAPIDefinition(
		info = @Info(
				title = "User Summary API",
				version = "1.0.0",
				description = "Aggregates a user and their posts into a single summary."),
		// Relative URL: the generated spec stays independent of the host/port it was generated on
		servers = @Server(url = "/", description = "Current host"))
class OpenApiConfig {
}
