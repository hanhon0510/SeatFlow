package com.seatflow.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.seatflow.kafka.SeatFlowKafkaProperties;

class KafkaHealthServiceTests {

	private static final Duration HEALTH_TIMEOUT = Duration.ofMillis(750);

	@Test
	void disabledKafkaReportsDisabledAndSkipsClient() {
		KafkaHealthClient client = org.mockito.Mockito.mock(KafkaHealthClient.class);
		KafkaHealthService service = new KafkaHealthService(
				new SeatFlowKafkaProperties(false, null, null),
				Optional.of(client));

		KafkaHealthService.KafkaHealthResponse response = service.checkKafka();

		assertThat(response.status()).isEqualTo("DISABLED");
		assertThat(response.broker()).isEqualTo("Kafka");
		verify(client, never()).check(org.mockito.ArgumentMatchers.any());
	}

	@Test
	void enabledKafkaUsesConfiguredHealthTimeout() {
		KafkaHealthClient client = org.mockito.Mockito.mock(KafkaHealthClient.class);
		KafkaHealthService service = new KafkaHealthService(
				new SeatFlowKafkaProperties(
						true,
						null,
						null,
						null,
						new SeatFlowKafkaProperties.Health(HEALTH_TIMEOUT)),
				Optional.of(client));

		KafkaHealthService.KafkaHealthResponse response = service.checkKafka();

		assertThat(response.status()).isEqualTo("UP");
		verify(client).check(HEALTH_TIMEOUT);
	}

	@Test
	void enabledKafkaWithoutClientIsUnavailable() {
		KafkaHealthService service = new KafkaHealthService(
				new SeatFlowKafkaProperties(true, null, null),
				Optional.empty());

		assertThatThrownBy(service::checkKafka)
				.isInstanceOf(KafkaHealthUnavailableException.class)
				.hasMessage("Kafka health check failed");
	}

	@Test
	void kafkaFailuresProduceSafeErrors() {
		KafkaHealthClient client = org.mockito.Mockito.mock(KafkaHealthClient.class);
		doThrow(new RuntimeException("broker.internal:9092 connection detail"))
				.when(client)
				.check(HEALTH_TIMEOUT);
		KafkaHealthService service = new KafkaHealthService(
				new SeatFlowKafkaProperties(
						true,
						null,
						null,
						null,
						new SeatFlowKafkaProperties.Health(HEALTH_TIMEOUT)),
				Optional.of(client));

		assertThatThrownBy(service::checkKafka)
				.isInstanceOf(KafkaHealthUnavailableException.class)
				.hasMessage("Kafka health check failed")
				.hasMessageNotContaining("broker.internal");
	}
}
