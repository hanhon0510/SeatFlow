package com.seatflow.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class HealthControllerUnitTests {

	@Mock
	private DatabaseHealthService databaseHealthService;

	@Mock
	private RedisHealthService redisHealthService;

	@Mock
	private KafkaHealthService kafkaHealthService;

	@Test
	void livenessDoesNotCheckDependencies() {
		HealthController.HealthResponse response = controller().liveness();

		assertThat(response.status()).isEqualTo("UP");
		assertThat(response.application()).isEqualTo("seatflow-backend");
	}

	@Test
	void readinessIsUpWhenCriticalDependenciesAreUpAndKafkaIsDisabled() {
		when(databaseHealthService.checkDatabase())
				.thenReturn(new DatabaseHealthService.DatabaseHealthResponse("UP", "PostgreSQL"));
		when(redisHealthService.checkRedis())
				.thenReturn(new RedisHealthService.RedisHealthResponse("UP", "Redis"));
		when(kafkaHealthService.checkKafka())
				.thenReturn(new KafkaHealthService.KafkaHealthResponse("DISABLED", "Kafka"));

		ResponseEntity<HealthController.ReadinessResponse> response = controller().readiness();

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().status()).isEqualTo("UP");
		assertThat(response.getBody().dependencies())
				.extracting(
						HealthController.DependencyHealth::name,
						HealthController.DependencyHealth::status,
						HealthController.DependencyHealth::critical,
						HealthController.DependencyHealth::dependency)
				.containsExactly(
						tuple("database", "UP", true, "PostgreSQL"),
						tuple("redis", "UP", true, "Redis"),
						tuple("kafka", "DISABLED", false, "Kafka"));
	}

	@Test
	void readinessIsUnavailableWhenRedisIsDown() {
		when(databaseHealthService.checkDatabase())
				.thenReturn(new DatabaseHealthService.DatabaseHealthResponse("UP", "PostgreSQL"));
		when(redisHealthService.checkRedis())
				.thenThrow(new RedisHealthUnavailableException(new IllegalStateException("internal redis detail")));
		when(kafkaHealthService.checkKafka())
				.thenReturn(new KafkaHealthService.KafkaHealthResponse("DISABLED", "Kafka"));

		ResponseEntity<HealthController.ReadinessResponse> response = controller().readiness();

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().status()).isEqualTo("DOWN");
		assertThat(response.getBody().dependencies())
				.extracting(HealthController.DependencyHealth::name, HealthController.DependencyHealth::status)
				.contains(tuple("redis", "DOWN"));
	}

	@Test
	void readinessIsUnavailableWhenDatabaseIsDown() {
		when(databaseHealthService.checkDatabase())
				.thenThrow(new DatabaseHealthUnavailableException(new IllegalStateException("internal database detail")));
		when(redisHealthService.checkRedis())
				.thenReturn(new RedisHealthService.RedisHealthResponse("UP", "Redis"));
		when(kafkaHealthService.checkKafka())
				.thenReturn(new KafkaHealthService.KafkaHealthResponse("DISABLED", "Kafka"));

		ResponseEntity<HealthController.ReadinessResponse> response = controller().readiness();

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().status()).isEqualTo("DOWN");
		assertThat(response.getBody().dependencies())
				.extracting(HealthController.DependencyHealth::name, HealthController.DependencyHealth::status)
				.contains(tuple("database", "DOWN"));
	}

	@Test
	void readinessIsUnavailableWhenEnabledKafkaIsDown() {
		when(databaseHealthService.checkDatabase())
				.thenReturn(new DatabaseHealthService.DatabaseHealthResponse("UP", "PostgreSQL"));
		when(redisHealthService.checkRedis())
				.thenReturn(new RedisHealthService.RedisHealthResponse("UP", "Redis"));
		when(kafkaHealthService.checkKafka())
				.thenThrow(new KafkaHealthUnavailableException(new IllegalStateException("internal broker detail")));

		ResponseEntity<HealthController.ReadinessResponse> response = controller().readiness();

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().status()).isEqualTo("DOWN");
		assertThat(response.getBody().dependencies())
				.extracting(
						HealthController.DependencyHealth::name,
						HealthController.DependencyHealth::status,
						HealthController.DependencyHealth::critical)
				.contains(tuple("kafka", "DOWN", true));
	}

	private HealthController controller() {
		return new HealthController(
				"seatflow-backend",
				databaseHealthService,
				redisHealthService,
				kafkaHealthService);
	}
}
