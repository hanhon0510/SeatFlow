package com.seatflow.health;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/health")
public class HealthController {

	private final String applicationName;

	public HealthController(@Value("${spring.application.name}") String applicationName) {
		this.applicationName = applicationName;
	}

	@GetMapping
	public HealthResponse health() {
		return new HealthResponse("UP", applicationName);
	}

	public record HealthResponse(String status, String application) {
	}

}
