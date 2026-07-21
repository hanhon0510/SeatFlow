package com.seatflow.health;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/health")
public class HealthController {

	private final String applicationName;
	private final DatabaseHealthService databaseHealthService;

	public HealthController(
			@Value("${spring.application.name}") String applicationName,
			DatabaseHealthService databaseHealthService) {
		this.applicationName = applicationName;
		this.databaseHealthService = databaseHealthService;
	}

	@GetMapping
	public HealthResponse health() {
		return new HealthResponse("UP", applicationName);
	}

	@GetMapping("/database")
	public DatabaseHealthService.DatabaseHealthResponse databaseHealth() {
		return databaseHealthService.checkDatabase();
	}

	public record HealthResponse(String status, String application) {
	}

}
