package com.seatflow.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.seatflow.event.EventSeatRecord;
import com.seatflow.event.EventSeatStatus;
import com.seatflow.order.OrderRecord;
import com.seatflow.order.OrderStatus;
import com.seatflow.payment.PaymentRecord;
import com.seatflow.payment.PaymentStatus;

@ExtendWith(MockitoExtension.class)
class OutboxServiceTests {

	private static final UUID ORDER_ID = UUID.fromString("d17bb023-d9b8-4128-a11f-f2ec9c9e916d");
	private static final UUID RESERVATION_ID = UUID.fromString("f1adff88-4570-4483-8f83-4d9c8453f697");
	private static final UUID USER_ID = UUID.fromString("61e67e16-df17-4b76-8a92-ad64d34ada73");
	private static final UUID PAYMENT_ID = UUID.fromString("c63e8642-926d-4890-8877-b1c0c18998fd");
	private static final UUID EVENT_SEAT_ID_1 = UUID.fromString("11111111-1111-4111-8111-111111111111");
	private static final UUID EVENT_SEAT_ID_2 = UUID.fromString("22222222-2222-4222-8222-222222222222");
	private static final Instant NOW = Instant.parse("2026-08-10T12:00:00Z");

	@Mock
	private OutboxMapper outboxMapper;

	private ObjectMapper objectMapper;
	private OutboxService outboxService;

	@BeforeEach
	void setUp() {
		objectMapper = JsonMapper.builder()
				.findAndAddModules()
				.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
				.build();
		outboxService = new OutboxService(outboxMapper, objectMapper);
	}

	@Test
	void recordOrderPaidInsertsPendingEventWithPayload() throws Exception {
		when(outboxMapper.insert(any())).thenReturn(1);

		OutboxEventRecord event = outboxService.recordOrderPaid(
				order(),
				payment(),
				List.of(eventSeat(EVENT_SEAT_ID_1), eventSeat(EVENT_SEAT_ID_2)),
				NOW);

		ArgumentCaptor<OutboxEventRecord> eventCaptor = ArgumentCaptor.forClass(OutboxEventRecord.class);
		verify(outboxMapper).insert(eventCaptor.capture());
		assertThat(event).isEqualTo(eventCaptor.getValue());
		assertThat(event.id()).isNotNull();
		assertThat(event.aggregateType()).isEqualTo("Order");
		assertThat(event.aggregateId()).isEqualTo(ORDER_ID);
		assertThat(event.eventType()).isEqualTo("OrderPaid");
		assertThat(event.eventVersion()).isEqualTo(1);
		assertThat(event.correlationId()).isEqualTo(PAYMENT_ID);
		assertThat(event.status()).isEqualTo(OutboxEventStatus.PENDING);
		assertThat(event.attemptCount()).isZero();
		assertThat(event.createdAt()).isEqualTo(NOW);
		assertThat(event.publishedAt()).isNull();
		assertThat(event.nextAttemptAt()).isEqualTo(NOW);

		JsonNode payload = objectMapper.readTree(event.payload());
		assertThat(payload.get("orderId").asText()).isEqualTo(ORDER_ID.toString());
		assertThat(payload.get("reservationId").asText()).isEqualTo(RESERVATION_ID.toString());
		assertThat(payload.get("userId").asText()).isEqualTo(USER_ID.toString());
		assertThat(payload.get("paymentId").asText()).isEqualTo(PAYMENT_ID.toString());
		assertThat(payload.get("totalAmount").decimalValue()).isEqualByComparingTo("210000.75");
		assertThat(payload.get("currency").asText()).isEqualTo("VND");
		JsonNode eventSeatIds = payload.get("eventSeatIds");
		assertThat(eventSeatIds.size()).isEqualTo(2);
		assertThat(eventSeatIds.get(0).asText()).isEqualTo(EVENT_SEAT_ID_1.toString());
		assertThat(eventSeatIds.get(1).asText()).isEqualTo(EVENT_SEAT_ID_2.toString());
		assertThat(payload.get("paidAt").asText()).isEqualTo("2026-08-10T12:00:00Z");
	}

	@Test
	void insertFailureThrowsStorageException() {
		when(outboxMapper.insert(any())).thenReturn(0);

		assertThatThrownBy(() -> outboxService.recordOrderPaid(
				order(),
				payment(),
				List.of(eventSeat(EVENT_SEAT_ID_1)),
				NOW))
				.isInstanceOf(OutboxStorageException.class);
	}

	private static OrderRecord order() {
		return new OrderRecord(
				ORDER_ID,
				RESERVATION_ID,
				USER_ID,
				OrderStatus.PAID,
				new BigDecimal("210000.75"),
				"VND",
				NOW.minusSeconds(60),
				NOW);
	}

	private static PaymentRecord payment() {
		return new PaymentRecord(
				PAYMENT_ID,
				ORDER_ID,
				PaymentStatus.SUCCEEDED,
				new BigDecimal("210000.75"),
				"sim_" + PAYMENT_ID,
				null,
				NOW.minusSeconds(30),
				NOW);
	}

	private static EventSeatRecord eventSeat(UUID id) {
		return new EventSeatRecord(
				id,
				UUID.fromString("4410d049-dd93-4d62-b337-ea6a17bccd41"),
				UUID.randomUUID(),
				new BigDecimal("125000.00"),
				EventSeatStatus.AVAILABLE,
				0,
				NOW.minusSeconds(120),
				NOW.minusSeconds(120));
	}
}
