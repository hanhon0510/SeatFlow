package com.seatflow.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.utility.DockerImageName;

public abstract class KafkaTestContainerSupport extends PostgresTestContainerSupport {

	@Container
	private static final KafkaContainer KAFKA = new KafkaContainer(
			DockerImageName.parse("confluentinc/cp-kafka:7.7.1"));

	@DynamicPropertySource
	protected static void registerKafkaProperties(DynamicPropertyRegistry registry) {
		registry.add("seatflow.kafka.enabled", () -> "true");
		registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
		registry.add("spring.kafka.consumer.group-id", () -> "seatflow-kafka-test");
	}

	protected static KafkaContainer kafka() {
		return KAFKA;
	}
}
