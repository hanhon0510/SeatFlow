package com.seatflow.health;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/health")
public class HealthController {

	private final String applicationName;
	private final DatabaseHealthService databaseHealthService;
	private final RedisHealthService redisHealthService;
	private final KafkaHealthService kafkaHealthService;

	public HealthController(
			@Value("${spring.application.name}") String applicationName,
			DatabaseHealthService databaseHealthService,
			RedisHealthService redisHealthService,
			KafkaHealthService kafkaHealthService) {
		this.applicationName = applicationName;
		this.databaseHealthService = databaseHealthService;
		this.redisHealthService = redisHealthService;
		this.kafkaHealthService = kafkaHealthService;
	}

	@GetMapping
	public HealthResponse health() {
		return new HealthResponse("UP", applicationName);
	}

	@GetMapping("/live")
	public HealthResponse liveness() {
		return health();
	}

	@GetMapping("/ready")
	public ResponseEntity<ReadinessResponse> readiness() {
		List<DependencyHealth> dependencies = List.of(
				databaseDependency(),
				redisDependency(),
				kafkaDependency());
		boolean ready = dependencies.stream().allMatch(DependencyHealth::ready);
		return ResponseEntity
				.status(ready ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE)
				.body(new ReadinessResponse(ready ? "UP" : "DOWN", applicationName, dependencies));
	}

	@GetMapping("/database")
	public DatabaseHealthService.DatabaseHealthResponse databaseHealth() {
		return databaseHealthService.checkDatabase();
	}

	@GetMapping("/redis")
	public RedisHealthService.RedisHealthResponse redisHealth() {
		return redisHealthService.checkRedis();
	}

	@GetMapping("/kafka")
	public KafkaHealthService.KafkaHealthResponse kafkaHealth() {
		return kafkaHealthService.checkKafka();
	}

	private DependencyHealth databaseDependency() {
		try {
			DatabaseHealthService.DatabaseHealthResponse response = databaseHealthService.checkDatabase();
			return DependencyHealth.up("database", response.database());
		}
		catch (RuntimeException ex) {
			return DependencyHealth.down("database", "PostgreSQL");
		}
	}

	private DependencyHealth redisDependency() {
		try {
			RedisHealthService.RedisHealthResponse response = redisHealthService.checkRedis();
			return DependencyHealth.up("redis", response.cache());
		}
		catch (RuntimeException ex) {
			return DependencyHealth.down("redis", "Redis");
		}
	}

	private DependencyHealth kafkaDependency() {
		try {
			KafkaHealthService.KafkaHealthResponse response = kafkaHealthService.checkKafka();
			boolean critical = !"DISABLED".equals(response.status());
			return new DependencyHealth("kafka", response.status(), critical, response.broker());
		}
		catch (RuntimeException ex) {
			return DependencyHealth.down("kafka", "Kafka");
		}
	}

	public record HealthResponse(String status, String application) {
	}

	public record ReadinessResponse(
			String status,
			String application,
			List<DependencyHealth> dependencies) {
	}

	public record DependencyHealth(
			String name,
			String status,
			boolean critical,
			String dependency) {

		private static DependencyHealth up(String name, String dependency) {
			return new DependencyHealth(name, "UP", true, dependency);
		}

		private static DependencyHealth down(String name, String dependency) {
			return new DependencyHealth(name, "DOWN", true, dependency);
		}

		private boolean ready() {
			return !critical || "UP".equals(status);
		}
	}
}
