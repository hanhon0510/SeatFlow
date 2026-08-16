package com.seatflow.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.seatflow.kafka.EventEnvelope;
import com.seatflow.outbox.OrderPaidPayload;

@ExtendWith(MockitoExtension.class)
class OrderPaidAnalyticsConsumerTests {

	private static final UUID EVENT_ID = UUID.fromString("587d3189-3d60-48b6-a31b-4db2fb819241");
	private static final UUID ORDER_ID = UUID.fromString("a26bf02f-6c2d-4a0d-aac1-6f118a7df4b2");
	private static final UUID RESERVATION_ID = UUID.fromString("7dc4159c-b81c-4d3d-bba4-8ec5aac236e2");
	private static final UUID USER_ID = UUID.fromString("02c1029c-9eb9-45f3-9030-31070f2284bf");
	private static final UUID PAYMENT_ID = UUID.fromString("6f2c4bd4-946d-45a5-ab3a-b99052a545f8");
	private static final UUID CORRELATION_ID = UUID.fromString("82560533-1f29-4e33-af41-94634b58bd3b");
	private static final UUID EVENT_SEAT_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
	private static final Instant OCCURRED_AT = Instant.parse("2026-08-10T11:59:00Z");
	private static final Instant NOW = Instant.parse("2026-08-10T12:00:00Z");

	@Mock
	private ProcessedEventMapper processedEventMapper;

	@Mock
	private OrderPaidAnalyticsMapper analyticsMapper;

	private OrderPaidAnalyticsConsumer consumer;

	@BeforeEach
	void setUp() {
		ObjectMapper objectMapper = JsonMapper.builder()
				.findAndAddModules()
				.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
				.build();
		consumer = new OrderPaidAnalyticsConsumer(
				processedEventMapper,
				analyticsMapper,
				objectMapper,
				Clock.fixed(NOW, ZoneOffset.UTC));
	}

	@Test
	void firstDeliveryProcessesSideEffectAndMarksEventProcessed() {
		when(processedEventMapper.insert(any())).thenReturn(1);
		when(analyticsMapper.insert(any())).thenReturn(1);

		boolean processed = consumer.process(orderPaidEnvelope(1, payload()));

		assertThat(processed).isTrue();
		ArgumentCaptor<ProcessedEventRecord> processedCaptor =
				ArgumentCaptor.forClass(ProcessedEventRecord.class);
		verify(processedEventMapper).insert(processedCaptor.capture());
		assertThat(processedCaptor.getValue().consumerName())
				.isEqualTo(OrderPaidAnalyticsConsumer.CONSUMER_NAME);
		assertThat(processedCaptor.getValue().eventId()).isEqualTo(EVENT_ID);
		assertThat(processedCaptor.getValue().processedAt()).isEqualTo(NOW);

		ArgumentCaptor<OrderPaidAnalyticsRecord> analyticsCaptor =
				ArgumentCaptor.forClass(OrderPaidAnalyticsRecord.class);
		verify(analyticsMapper).insert(analyticsCaptor.capture());
		OrderPaidAnalyticsRecord analytics = analyticsCaptor.getValue();
		assertThat(analytics.eventId()).isEqualTo(EVENT_ID);
		assertThat(analytics.orderId()).isEqualTo(ORDER_ID);
		assertThat(analytics.reservationId()).isEqualTo(RESERVATION_ID);
		assertThat(analytics.userId()).isEqualTo(USER_ID);
		assertThat(analytics.paymentId()).isEqualTo(PAYMENT_ID);
		assertThat(analytics.totalAmount()).isEqualByComparingTo("210000.75");
		assertThat(analytics.currency()).isEqualTo("VND");
		assertThat(analytics.seatCount()).isEqualTo(1);
		assertThat(analytics.occurredAt()).isEqualTo(OCCURRED_AT);
		assertThat(analytics.correlationId()).isEqualTo(CORRELATION_ID);
		assertThat(analytics.createdAt()).isEqualTo(NOW);
	}

	@Test
	void duplicateDeliveryIsIgnoredWithoutSecondSideEffect() {
		when(processedEventMapper.insert(any())).thenReturn(0);

		boolean processed = consumer.process(orderPaidEnvelope(1, payload()));

		assertThat(processed).isFalse();
		verify(analyticsMapper, never()).insert(any());
	}

	@Test
	void unsupportedVersionFailsClearlyBeforeClaimingEvent() {
		assertThatThrownBy(() -> consumer.process(orderPaidEnvelope(2, payload())))
				.isInstanceOf(UnsupportedEventVersionException.class)
				.hasMessage("Unsupported OrderPaid event version 2, expected 1");

		verify(processedEventMapper, never()).insert(any());
		verify(analyticsMapper, never()).insert(any());
	}

	@Test
	void unsupportedEventTypeFailsBeforeClaimingEvent() {
		EventEnvelope<OrderPaidPayload> envelope = new EventEnvelope<>(
				EVENT_ID,
				"SeatReserved",
				1,
				ORDER_ID,
				CORRELATION_ID,
				OCCURRED_AT,
				payload());

		assertThatThrownBy(() -> consumer.process(envelope))
				.isInstanceOf(UnsupportedEventTypeException.class)
				.hasMessage("Unsupported event type SeatReserved, expected OrderPaid");

		verify(processedEventMapper, never()).insert(any());
		verify(analyticsMapper, never()).insert(any());
	}

	@Test
	void invalidPayloadFailsBeforeClaimingEvent() {
		EventEnvelope<Map<String, Object>> envelope = orderPaidEnvelope(
				1,
				Map.of("orderId", ORDER_ID.toString()));

		assertThatThrownBy(() -> consumer.process(envelope))
				.isInstanceOf(InvalidEventPayloadException.class)
				.hasMessage("Invalid payload for OrderPaid event");

		verify(processedEventMapper, never()).insert(any());
		verify(analyticsMapper, never()).insert(any());
	}

	private static <T> EventEnvelope<T> orderPaidEnvelope(int version, T payload) {
		return new EventEnvelope<>(
				EVENT_ID,
				"OrderPaid",
				version,
				ORDER_ID,
				CORRELATION_ID,
				OCCURRED_AT,
				payload);
	}

	private static OrderPaidPayload payload() {
		return new OrderPaidPayload(
				ORDER_ID,
				RESERVATION_ID,
				USER_ID,
				PAYMENT_ID,
				new BigDecimal("210000.75"),
				"VND",
				List.of(EVENT_SEAT_ID),
				OCCURRED_AT);
	}
}
