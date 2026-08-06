package com.seatflow.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.utility.DockerImageName;

public abstract class RedisTestContainerSupport extends PostgresTestContainerSupport {

	@Container
	private static final GenericContainer<?> REDIS = new GenericContainer<>(
			DockerImageName.parse("redis:7-alpine"))
			.withExposedPorts(6379);

	@DynamicPropertySource
	protected static void registerRedisProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.data.redis.host", REDIS::getHost);
		registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
		registry.add("spring.data.redis.timeout", () -> "1s");
		registry.add("spring.data.redis.connect-timeout", () -> "1s");
	}
}
