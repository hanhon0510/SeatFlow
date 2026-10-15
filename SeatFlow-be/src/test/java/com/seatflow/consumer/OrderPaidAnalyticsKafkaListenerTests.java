package com.seatflow.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

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
class OrderPaidAnalyticsKafkaListenerTests {

	private static final EventEnvelope<Map<String, Object>> EVENT = new EventEnvelope<>(
			UUID.fromString("551814d0-3e45-4c37-b8b8-55af75bd8261"),
			"OrderPaid",
			1,
			UUID.fromString("4138a06a-4e57-4925-b5e0-5b3b9b022995"),
			UUID.fromString("843dc57d-5f34-4325-95cb-014ef2006c0a"),
			Instant.parse("2026-08-23T12:00:00Z"),
			Map.of());

	@Mock
	private OrderPaidAnalyticsConsumer consumer;

	@Mock
	private OrderRefundedAnalyticsConsumer refundedConsumer;

	private SimpleMeterRegistry meterRegistry;
	private OrderPaidAnalyticsKafkaListener listener;

	@BeforeEach
	void setUp() {
		meterRegistry = new SimpleMeterRegistry();
		listener = new OrderPaidAnalyticsKafkaListener(consumer, refundedConsumer, new BusinessMetrics(meterRegistry));
	}

	@Test
	void consumerFailureIncrementsMetricAndRethrows() {
		doThrow(new InvalidEventPayloadException("OrderPaid")).when(consumer).process(EVENT);

		assertThatThrownBy(() -> listener.handle(EVENT))
				.isInstanceOf(InvalidEventPayloadException.class);

		verify(consumer).process(EVENT);
		assertThat(meterRegistry.get("kafka_consumer_failure").counter().count()).isEqualTo(1);
	}

	@Test
	void refundEventsGoToTheRefundConsumerOnly() {
		EventEnvelope<Map<String, Object>> refunded = new EventEnvelope<>(
				EVENT.eventId(), "OrderRefunded", 1, EVENT.aggregateId(),
				EVENT.correlationId(), EVENT.occurredAt(), Map.of());

		listener.handle(refunded);

		verify(refundedConsumer).process(refunded);
		verifyNoInteractions(consumer);
	}

	@Test
	void everyOtherTypeStillReachesThePaidConsumerSoUnknownOnesDeadLetter() {
		listener.handle(EVENT);

		verify(consumer).process(EVENT);
		verifyNoInteractions(refundedConsumer);
	}
}
