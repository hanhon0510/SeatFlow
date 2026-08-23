package com.seatflow.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.SendResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seatflow.kafka.EventEnvelope;
import com.seatflow.kafka.KafkaEventPublisher;
import com.seatflow.kafka.SeatFlowKafkaProperties;
import com.seatflow.observability.BusinessMetrics;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

@ExtendWith(MockitoExtension.class)
class OutboxPublisherTests {

	private static final UUID EVENT_ID = UUID.fromString("a616da89-0b54-450f-a94e-c4aa68299fb0");
	private static final UUID AGGREGATE_ID = UUID.fromString("ba9fda8c-0e8a-419a-8b06-a0d02465ef4d");
	private static final UUID CORRELATION_ID = UUID.fromString("75f6f69d-3ee4-4f1e-b305-a860715d95a6");
	private static final Instant CREATED_AT = Instant.parse("2026-08-10T11:59:00Z");
	private static final Instant NOW = Instant.parse("2026-08-10T12:00:00Z");
	private static final Duration RETRY_DELAY = Duration.ofSeconds(20);
	private static final Duration RETRY_MAX_DELAY = Duration.ofMinutes(2);

	@Mock
	private OutboxMapper outboxMapper;

	@Mock
	private KafkaEventPublisher eventPublisher;

	private SeatFlowKafkaProperties kafkaProperties;
	private OutboxProperties outboxProperties;
	private SimpleMeterRegistry meterRegistry;
	private OutboxPublisher publisher;

	@BeforeEach
	void setUp() {
		kafkaProperties = new SeatFlowKafkaProperties(true, null, null);
		outboxProperties = new OutboxProperties(new OutboxProperties.Publisher(
				false,
				50,
				RETRY_DELAY,
				RETRY_MAX_DELAY,
				Duration.ofSeconds(5)));
		meterRegistry = new SimpleMeterRegistry();
		publisher = newPublisher();
	}

	@Test
	void successfulPublishUsesStableOutboxIdAndMarksPublished() {
		OutboxEventRecord event = orderPaidEvent("""
				{"orderId":"%s","paymentId":"%s"}
				""".formatted(AGGREGATE_ID, CORRELATION_ID));
		CompletableFuture<SendResult<Object, Object>> sent = CompletableFuture.completedFuture(null);
		when(outboxMapper.lockPending(50)).thenReturn(List.of(event));
		when(eventPublisher.publish(eq(kafkaProperties.topics().orderEvents()), any(EventEnvelope.class)))
				.thenReturn(sent);
		when(outboxMapper.markPublished(EVENT_ID, NOW)).thenReturn(1);

		int published = publisher.publishPending();

		assertThat(published).isEqualTo(1);
		ArgumentCaptor<EventEnvelope> envelopeCaptor = ArgumentCaptor.forClass(EventEnvelope.class);
		verify(eventPublisher).publish(eq(kafkaProperties.topics().orderEvents()), envelopeCaptor.capture());
		EventEnvelope<?> envelope = envelopeCaptor.getValue();
		assertThat(envelope.eventId()).isEqualTo(EVENT_ID);
		assertThat(envelope.eventType()).isEqualTo("OrderPaid");
		assertThat(envelope.eventVersion()).isEqualTo(1);
		assertThat(envelope.aggregateId()).isEqualTo(AGGREGATE_ID);
		assertThat(envelope.correlationId()).isEqualTo(CORRELATION_ID);
		assertThat(envelope.occurredAt()).isEqualTo(CREATED_AT);
		assertThat(envelope.payload()).isInstanceOf(JsonNode.class);
		assertThat(((JsonNode) envelope.payload()).get("orderId").asText()).isEqualTo(AGGREGATE_ID.toString());
		verify(outboxMapper).markPublished(EVENT_ID, NOW);
		verify(outboxMapper, never()).scheduleRetry(any(), any());
	}

	@Test
	void kafkaFailureLeavesEventPendingAndSchedulesRetry() {
		OutboxEventRecord event = orderPaidEvent("{}");
		CompletableFuture<SendResult<Object, Object>> failed = new CompletableFuture<>();
		failed.completeExceptionally(new IllegalStateException("broker unavailable"));
		when(outboxMapper.lockPending(1)).thenReturn(List.of(event));
		when(eventPublisher.publish(eq(kafkaProperties.topics().orderEvents()), any(EventEnvelope.class)))
				.thenReturn(failed);
		when(outboxMapper.scheduleRetry(EVENT_ID, NOW.plus(RETRY_DELAY))).thenReturn(1);

		int published = publisher.publishPending(1);

		assertThat(published).isEqualTo(1);
		verify(outboxMapper).scheduleRetry(EVENT_ID, NOW.plus(RETRY_DELAY));
		verify(outboxMapper, never()).markPublished(any(), any());
		assertThat(meterRegistry.get("outbox_publish_failure").counter().count()).isEqualTo(1);
	}

	@Test
	void kafkaRecoveryPublishesPendingEventAfterPublisherRestart() {
		OutboxEventRecord event = orderPaidEvent("{}");
		CompletableFuture<SendResult<Object, Object>> failed = new CompletableFuture<>();
		failed.completeExceptionally(new IllegalStateException("broker unavailable"));
		CompletableFuture<SendResult<Object, Object>> sent = CompletableFuture.completedFuture(null);
		when(outboxMapper.lockPending(1)).thenReturn(List.of(event), List.of(event));
		when(eventPublisher.publish(eq(kafkaProperties.topics().orderEvents()), any(EventEnvelope.class)))
				.thenReturn(failed, sent);
		when(outboxMapper.scheduleRetry(EVENT_ID, NOW.plus(RETRY_DELAY))).thenReturn(1);
		when(outboxMapper.markPublished(EVENT_ID, NOW)).thenReturn(1);

		assertThat(publisher.publishPending(1)).isEqualTo(1);
		assertThat(newPublisher().publishPending(1)).isEqualTo(1);

		verify(outboxMapper).scheduleRetry(EVENT_ID, NOW.plus(RETRY_DELAY));
		verify(outboxMapper).markPublished(EVENT_ID, NOW);
		verify(eventPublisher, times(2)).publish(eq(kafkaProperties.topics().orderEvents()), any(EventEnvelope.class));
	}

	@Test
	void retryDelayBacksOffAndCapsAtConfiguredMaximum() {
		OutboxEventRecord event = orderPaidEvent("{}", 4);
		CompletableFuture<SendResult<Object, Object>> failed = new CompletableFuture<>();
		failed.completeExceptionally(new IllegalStateException("broker unavailable"));
		when(outboxMapper.lockPending(1)).thenReturn(List.of(event));
		when(eventPublisher.publish(eq(kafkaProperties.topics().orderEvents()), any(EventEnvelope.class)))
				.thenReturn(failed);
		when(outboxMapper.scheduleRetry(EVENT_ID, NOW.plus(RETRY_MAX_DELAY))).thenReturn(1);

		int published = publisher.publishPending(1);

		assertThat(published).isEqualTo(1);
		verify(outboxMapper).scheduleRetry(EVENT_ID, NOW.plus(RETRY_MAX_DELAY));
		verify(outboxMapper, never()).markPublished(any(), any());
	}

	@Test
	void invalidPayloadIsNotPublishedAndSchedulesRetry() {
		OutboxEventRecord event = orderPaidEvent("{invalid-json");
		when(outboxMapper.lockPending(1)).thenReturn(List.of(event));
		when(outboxMapper.scheduleRetry(EVENT_ID, NOW.plus(RETRY_DELAY))).thenReturn(1);

		int published = publisher.publishPending(1);

		assertThat(published).isEqualTo(1);
		verify(eventPublisher, never()).publish(any(), any());
		verify(outboxMapper).scheduleRetry(EVENT_ID, NOW.plus(RETRY_DELAY));
		verify(outboxMapper, never()).markPublished(any(), any());
	}

	@Test
	void markPublishedFailureIsNotConvertedToRetry() {
		OutboxEventRecord event = orderPaidEvent("{}");
		CompletableFuture<SendResult<Object, Object>> sent = CompletableFuture.completedFuture(null);
		when(outboxMapper.lockPending(50)).thenReturn(List.of(event));
		when(eventPublisher.publish(eq(kafkaProperties.topics().orderEvents()), any(EventEnvelope.class)))
				.thenReturn(sent);
		when(outboxMapper.markPublished(EVENT_ID, NOW)).thenReturn(0);

		assertThatThrownBy(() -> publisher.publishPending())
				.isInstanceOf(OutboxPublishException.class);

		verify(outboxMapper, never()).scheduleRetry(any(), any());
	}

	private OutboxPublisher newPublisher() {
		return new OutboxPublisher(
				outboxMapper,
				eventPublisher,
			kafkaProperties,
			outboxProperties,
			new ObjectMapper(),
			Clock.fixed(NOW, ZoneOffset.UTC),
			new BusinessMetrics(meterRegistry));
	}

	private static OutboxEventRecord orderPaidEvent(String payload) {
		return orderPaidEvent(payload, 0);
	}

	private static OutboxEventRecord orderPaidEvent(String payload, int attemptCount) {
		return new OutboxEventRecord(
				EVENT_ID,
				"Order",
				AGGREGATE_ID,
				"OrderPaid",
				1,
				payload,
				CORRELATION_ID,
				OutboxEventStatus.PENDING,
				attemptCount,
				CREATED_AT,
				null,
				CREATED_AT);
	}
}
