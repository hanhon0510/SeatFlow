package com.seatflow.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
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
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.seatflow.consumer.InvalidEventPayloadException;
import com.seatflow.consumer.ProcessedEventMapper;
import com.seatflow.consumer.ProcessedEventRecord;
import com.seatflow.consumer.UnsupportedEventVersionException;
import com.seatflow.kafka.EventEnvelope;
import com.seatflow.observability.BusinessMetrics;
import com.seatflow.refund.RefundMapper;
import com.seatflow.refund.RefundRecord;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

@ExtendWith(MockitoExtension.class)
class OrderEmailNotificationConsumerTests {

	private static final Instant NOW = Instant.parse("2026-11-01T10:00:00Z");
	private static final UUID ORDER_ID = UUID.fromString("abcdef12-3456-4789-8abc-def012345678");

	@Mock
	private ProcessedEventMapper processedEventMapper;

	@Mock
	private OrderNotificationMapper notificationMapper;

	@Mock
	private RefundMapper refundMapper;

	@Mock
	private EmailGateway emailGateway;

	private SimpleMeterRegistry meterRegistry;

	private OrderEmailNotificationConsumer consumer;

	@BeforeEach
	void setUp() {
		meterRegistry = new SimpleMeterRegistry();
		consumer = new OrderEmailNotificationConsumer(
				processedEventMapper,
				notificationMapper,
				refundMapper,
				new OrderEmailTemplates("https://seatflow.example"),
				emailGateway,
				new BusinessMetrics(meterRegistry),
				new ObjectMapper().registerModule(new JavaTimeModule()),
				Clock.fixed(NOW, ZoneOffset.UTC));
	}

	@Test
	void aPaidOrderSendsOneConfirmationAfterClaimingTheEvent() {
		stubOrder();
		when(processedEventMapper.insert(any())).thenReturn(1);

		boolean sent = consumer.process(paidEvent());

		assertThat(sent).isTrue();
		ArgumentCaptor<ProcessedEventRecord> claim = ArgumentCaptor.forClass(ProcessedEventRecord.class);
		verify(processedEventMapper).insert(claim.capture());
		assertThat(claim.getValue().consumerName()).isEqualTo("order-email-notifications");
		ArgumentCaptor<EmailMessage> message = ArgumentCaptor.forClass(EmailMessage.class);
		verify(emailGateway).send(message.capture());
		assertThat(message.getValue().subject()).isEqualTo("Your tickets for Opening Night");
		assertThat(message.getValue().to()).isEqualTo("buyer@example.com");
		assertThat(meterRegistry.get("email_sent").counter().count()).isEqualTo(1.0);
	}

	@Test
	void aRefundedOrderSendsTheReceiptWithTheStoredRefund() {
		stubOrder();
		when(processedEventMapper.insert(any())).thenReturn(1);
		when(refundMapper.findByOrderId(ORDER_ID)).thenReturn(refund());

		boolean sent = consumer.process(refundedEvent());

		assertThat(sent).isTrue();
		ArgumentCaptor<EmailMessage> message = ArgumentCaptor.forClass(EmailMessage.class);
		verify(emailGateway).send(message.capture());
		assertThat(message.getValue().subject()).isEqualTo("Your refund for Opening Night");
		assertThat(message.getValue().text()).contains("200,000 VND");
	}

	@Test
	void aRedeliveredEventIsNotEmailedAgain() {
		when(processedEventMapper.insert(any())).thenReturn(0);

		boolean sent = consumer.process(paidEvent());

		assertThat(sent).isFalse();
		verify(emailGateway, never()).send(any());
		verify(notificationMapper, never()).findOrderDetails(any());
	}

	@Test
	void otherOrderEventsAreIgnoredWithoutBeingClaimed() {
		EventEnvelope<Map<String, Object>> other = envelope("OrderCancelled", 1, Map.of("orderId", ORDER_ID));

		assertThat(consumer.process(other)).isFalse();

		verify(processedEventMapper, never()).insert(any());
		verify(emailGateway, never()).send(any());
	}

	@Test
	void anUnsupportedVersionIsRejectedSoItReachesTheDeadLetterTopic() {
		assertThatThrownBy(() -> consumer.process(envelope("OrderPaid", 2, Map.of("orderId", ORDER_ID))))
				.isInstanceOf(UnsupportedEventVersionException.class);

		verify(emailGateway, never()).send(any());
	}

	@Test
	void aPayloadWithoutAnOrderIdIsRejected() {
		assertThatThrownBy(() -> consumer.process(envelope("OrderPaid", 1, Map.of("currency", "VND"))))
				.isInstanceOf(InvalidEventPayloadException.class);

		verify(processedEventMapper, never()).insert(any());
	}

	@Test
	void anOrderThatNoLongerExistsIsSkippedInsteadOfRetriedForever() {
		when(processedEventMapper.insert(any())).thenReturn(1);
		when(notificationMapper.findOrderDetails(ORDER_ID)).thenReturn(null);

		assertThat(consumer.process(paidEvent())).isFalse();

		verify(emailGateway, never()).send(any());
	}

	@Test
	void aFailedSendIsCountedAndRethrownSoTheEventIsRetried() {
		stubOrder();
		when(processedEventMapper.insert(any())).thenReturn(1);
		doThrow(new EmailDeliveryException("smtp down", new RuntimeException()))
				.when(emailGateway).send(any());

		assertThatThrownBy(() -> consumer.process(paidEvent())).isInstanceOf(EmailDeliveryException.class);

		assertThat(meterRegistry.get("email_failed").counter().count()).isEqualTo(1.0);
		assertThat(meterRegistry.get("email_sent").counter().count()).isZero();
	}

	@Test
	void aRefundEventWithNoStoredRefundIsRejected() {
		stubOrder();
		when(processedEventMapper.insert(any())).thenReturn(1);
		when(refundMapper.findByOrderId(ORDER_ID)).thenReturn(null);

		assertThatThrownBy(() -> consumer.process(refundedEvent()))
				.isInstanceOf(InvalidEventPayloadException.class);

		verify(emailGateway, never()).send(any());
	}

	private void stubOrder() {
		when(notificationMapper.findOrderDetails(ORDER_ID)).thenReturn(new OrderEmailDetails(
				ORDER_ID, "buyer@example.com", UUID.randomUUID(), "Opening Night",
				Instant.parse("2026-12-05T12:30:00Z"), "Concert Hall", "1 Main Street", "Ho Chi Minh City",
				"Asia/Ho_Chi_Minh", new BigDecimal("200000.00"), BigDecimal.ZERO, null, "VND", false, null));
		when(notificationMapper.findSeats(ORDER_ID)).thenReturn(
				List.of(new OrderEmailSeat(UUID.randomUUID(), "Orchestra", "A", "A1")));
	}

	private static RefundRecord refund() {
		return new RefundRecord(UUID.randomUUID(), ORDER_ID, UUID.randomUUID(), new BigDecimal("200000.00"),
				"VND", null, UUID.randomUUID(), NOW);
	}

	private static EventEnvelope<Map<String, Object>> paidEvent() {
		return envelope("OrderPaid", 1, Map.of("orderId", ORDER_ID, "currency", "VND"));
	}

	private static EventEnvelope<Map<String, Object>> refundedEvent() {
		return envelope("OrderRefunded", 1, Map.of("orderId", ORDER_ID, "refundId", UUID.randomUUID()));
	}

	private static EventEnvelope<Map<String, Object>> envelope(String type, int version, Map<String, Object> payload) {
		return new EventEnvelope<>(
				UUID.randomUUID(), type, version, ORDER_ID, UUID.randomUUID(), NOW, payload);
	}
}
