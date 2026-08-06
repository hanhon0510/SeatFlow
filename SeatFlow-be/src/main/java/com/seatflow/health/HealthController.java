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
	private final RedisHealthService redisHealthService;

	public HealthController(
			@Value("${spring.application.name}") String applicationName,
			DatabaseHealthService databaseHealthService,
			RedisHealthService redisHealthService) {
		this.applicationName = applicationName;
		this.databaseHealthService = databaseHealthService;
		this.redisHealthService = redisHealthService;
	}

	@GetMapping
	public HealthResponse health() {
		return new HealthResponse("UP", applicationName);
	}

	@GetMapping("/database")
	public DatabaseHealthService.DatabaseHealthResponse databaseHealth() {
		return databaseHealthService.checkDatabase();
	}

	@GetMapping("/redis")
	public RedisHealthService.RedisHealthResponse redisHealth() {
		return redisHealthService.checkRedis();
	}

	public record HealthResponse(String status, String application) {
	}

}
