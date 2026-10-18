package com.seatflow.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.seatflow.kafka.EventEnvelope;
import com.seatflow.observability.BusinessMetrics;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

@ExtendWith(MockitoExtension.class)
class OrderEmailKafkaListenerTests {

	private static final EventEnvelope<Map<String, Object>> EVENT = new EventEnvelope<>(
			UUID.fromString("551814d0-3e45-4c37-b8b8-55af75bd8261"),
			"OrderPaid",
			1,
			UUID.fromString("4138a06a-4e57-4925-b5e0-5b3b9b022995"),
			UUID.fromString("843dc57d-5f34-4325-95cb-014ef2006c0a"),
			Instant.parse("2026-08-23T12:00:00Z"),
			Map.of());

	@Mock
	private OrderEmailNotificationConsumer consumer;

	private SimpleMeterRegistry meterRegistry;

	private OrderEmailKafkaListener listener;

	@BeforeEach
	void setUp() {
		meterRegistry = new SimpleMeterRegistry();
		listener = new OrderEmailKafkaListener(consumer, new BusinessMetrics(meterRegistry));
	}

	@Test
	void handsTheEventToTheConsumer() {
		listener.handle(EVENT);

		verify(consumer).process(EVENT);
	}

	@Test
	void aFailureIsCountedAndRethrownSoTheErrorHandlerRetriesAndDeadLetters() {
		doThrow(new EmailDeliveryException("smtp down", new RuntimeException())).when(consumer).process(EVENT);

		assertThatThrownBy(() -> listener.handle(EVENT)).isInstanceOf(EmailDeliveryException.class);

		assertThat(meterRegistry.get("kafka_consumer_failure").counter().count()).isEqualTo(1);
	}
}
