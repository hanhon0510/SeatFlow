package com.seatflow.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.util.backoff.FixedBackOff;

class SeatFlowKafkaConfigTests {

	@Test
	void retryBackoffUsesConfiguredMaxDeliveryAttempts() {
		FixedBackOff backOff = SeatFlowKafkaConfig.retryBackOff(
				new SeatFlowKafkaProperties.Retry(4, Duration.ofMillis(250)));

		assertThat(backOff.getInterval()).isEqualTo(250);
		assertThat(backOff.getMaxAttempts()).isEqualTo(3);
	}

	@Test
	void retryPropertiesUseSafeDefaults() {
		SeatFlowKafkaProperties properties = new SeatFlowKafkaProperties(
				true,
				null,
				null,
				new SeatFlowKafkaProperties.Retry(0, Duration.ofMillis(-1)));

		assertThat(properties.retry().maxAttempts()).isEqualTo(3);
		assertThat(properties.retry().backoff()).isEqualTo(Duration.ofSeconds(1));
	}
}
