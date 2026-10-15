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
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.seatflow.kafka.EventEnvelope;
import com.seatflow.outbox.OrderRefundedPayload;

@ExtendWith(MockitoExtension.class)
class OrderRefundedAnalyticsConsumerTests {

	private static final UUID EVENT_ID = UUID.fromString("587d3189-3d60-48b6-a31b-4db2fb819241");
	private static final UUID ORDER_ID = UUID.fromString("a26bf02f-6c2d-4a0d-aac1-6f118a7df4b2");
	private static final UUID REFUND_ID = UUID.fromString("0b9e2c10-45f0-4a9e-9d52-3c5f0a1d7e11");
	private static final Instant REFUNDED_AT = Instant.parse("2026-08-10T11:59:00Z");
	private static final Instant NOW = Instant.parse("2026-08-10T12:00:00Z");

	@Mock
	private ProcessedEventMapper processedEventMapper;

	@Mock
	private OrderPaidAnalyticsMapper analyticsMapper;

	private OrderRefundedAnalyticsConsumer consumer;

	@BeforeEach
	void setUp() {
		ObjectMapper objectMapper = JsonMapper.builder()
				.findAndAddModules()
				.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
				.build();
		consumer = new OrderRefundedAnalyticsConsumer(
				processedEventMapper, analyticsMapper, objectMapper, Clock.fixed(NOW, ZoneOffset.UTC));
	}

	@Test
	void firstDeliveryMarksThePaidOrderRefunded() {
		when(processedEventMapper.insert(any())).thenReturn(1);
		when(analyticsMapper.markRefunded(ORDER_ID, REFUNDED_AT)).thenReturn(1);

		assertThat(consumer.process(envelope("OrderRefunded", 1, payload()))).isTrue();

		verify(analyticsMapper).markRefunded(ORDER_ID, REFUNDED_AT);
	}

	@Test
	void duplicateDeliveryIsIgnored() {
		when(processedEventMapper.insert(any())).thenReturn(0);

		assertThat(consumer.process(envelope("OrderRefunded", 1, payload()))).isFalse();

		verify(analyticsMapper, never()).markRefunded(any(), any());
	}

	@Test
	void missingPaidRowIsTolerated() {
		when(processedEventMapper.insert(any())).thenReturn(1);
		when(analyticsMapper.markRefunded(ORDER_ID, REFUNDED_AT)).thenReturn(0);

		assertThat(consumer.process(envelope("OrderRefunded", 1, payload()))).isTrue();
	}

	@Test
	void wrongTypeOrVersionIsRejectedBeforeClaimingTheEvent() {
		assertThatThrownBy(() -> consumer.process(envelope("OrderPaid", 1, payload())))
				.isInstanceOf(UnsupportedEventTypeException.class);
		assertThatThrownBy(() -> consumer.process(envelope("OrderRefunded", 2, payload())))
				.isInstanceOf(UnsupportedEventVersionException.class);

		verify(processedEventMapper, never()).insert(any());
	}

	@Test
	void payloadWithoutAnOrderIsInvalid() {
		OrderRefundedPayload invalid = new OrderRefundedPayload(
				null, REFUND_ID, UUID.randomUUID(), UUID.randomUUID(), BigDecimal.TEN, "VND", List.of(), REFUNDED_AT);

		assertThatThrownBy(() -> consumer.process(envelope("OrderRefunded", 1, invalid)))
				.isInstanceOf(InvalidEventPayloadException.class);

		verify(processedEventMapper, never()).insert(any());
	}

	private static OrderRefundedPayload payload() {
		return new OrderRefundedPayload(
				ORDER_ID, REFUND_ID, UUID.randomUUID(), UUID.randomUUID(),
				new BigDecimal("210000.00"), "VND", List.of(UUID.randomUUID()), REFUNDED_AT);
	}

	private static EventEnvelope<OrderRefundedPayload> envelope(
			String type, int version, OrderRefundedPayload payload) {
		return new EventEnvelope<>(EVENT_ID, type, version, ORDER_ID, UUID.randomUUID(), REFUNDED_AT, payload);
	}
}
