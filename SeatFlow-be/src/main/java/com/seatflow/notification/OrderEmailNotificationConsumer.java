package com.seatflow.notification;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.seatflow.consumer.InvalidEventPayloadException;
import com.seatflow.consumer.ProcessedEventMapper;
import com.seatflow.consumer.ProcessedEventRecord;
import com.seatflow.consumer.UnsupportedEventVersionException;
import com.seatflow.kafka.EventEnvelope;
import com.seatflow.observability.BusinessMetrics;
import com.seatflow.outbox.OrderPaidPayload;
import com.seatflow.outbox.OrderRefundedPayload;
import com.seatflow.refund.RefundMapper;
import com.seatflow.refund.RefundRecord;

/**
 * Tells buyers about their orders: a confirmation with the tickets when an order is paid, and a
 * receipt when it is refunded (including the refunds that follow an event being cancelled).
 *
 * <p>Delivery is at least once. The event is claimed in {@code processed_events} in the same
 * transaction as the send, so a failed send rolls the claim back and Kafka redelivers it, while a
 * redelivery of an event that already went out is recognised and skipped. The one gap is a crash
 * after the mail server accepted the message but before the transaction commits, which can send
 * that single email twice.
 */
@Service
public class OrderEmailNotificationConsumer {

	static final String CONSUMER_NAME = "order-email-notifications";
	static final String ORDER_PAID = "OrderPaid";
	static final String ORDER_REFUNDED = "OrderRefunded";

	private static final Logger log = LoggerFactory.getLogger(OrderEmailNotificationConsumer.class);
	private static final int EVENT_VERSION = 1;

	private final ProcessedEventMapper processedEventMapper;
	private final OrderNotificationMapper notificationMapper;
	private final RefundMapper refundMapper;
	private final OrderEmailTemplates templates;
	private final EmailGateway emailGateway;
	private final BusinessMetrics businessMetrics;
	private final ObjectMapper objectMapper;
	private final Clock clock;

	public OrderEmailNotificationConsumer(
			ProcessedEventMapper processedEventMapper,
			OrderNotificationMapper notificationMapper,
			RefundMapper refundMapper,
			OrderEmailTemplates templates,
			EmailGateway emailGateway,
			BusinessMetrics businessMetrics,
			ObjectMapper objectMapper,
			Clock clock) {
		this.processedEventMapper = processedEventMapper;
		this.notificationMapper = notificationMapper;
		this.refundMapper = refundMapper;
		this.templates = templates;
		this.emailGateway = emailGateway;
		this.businessMetrics = businessMetrics;
		this.objectMapper = objectMapper;
		this.clock = clock;
	}

	/** @return true when an email was sent; false for events that need none or were already handled */
	@Transactional
	public boolean process(EventEnvelope<?> event) {
		Objects.requireNonNull(event, "event is required");
		if (!ORDER_PAID.equals(event.eventType()) && !ORDER_REFUNDED.equals(event.eventType())) {
			// Other order events are not worth an email. The analytics consumer already rejects types
			// nobody knows, so there is no need to dead-letter them a second time here.
			return false;
		}
		if (event.eventVersion() != EVENT_VERSION) {
			throw new UnsupportedEventVersionException(event.eventType(), event.eventVersion(), EVENT_VERSION);
		}
		UUID orderId = orderId(event);

		int claimed = processedEventMapper.insert(new ProcessedEventRecord(
				UUID.randomUUID(), CONSUMER_NAME, event.eventId(), clock.instant()));
		if (claimed == 0) {
			log.info("Ignoring duplicate {} eventId={} correlationId={}",
					event.eventType(), event.eventId(), event.correlationId());
			return false;
		}

		OrderEmailDetails order = notificationMapper.findOrderDetails(orderId);
		if (order == null) {
			// Nothing to say and nobody to say it to; failing would only retry forever.
			log.warn("No order to email about for {} eventId={} orderId={}",
					event.eventType(), event.eventId(), orderId);
			return false;
		}
		List<OrderEmailSeat> seats = notificationMapper.findSeats(orderId);

		EmailMessage message = ORDER_PAID.equals(event.eventType())
				? templates.orderPaid(order, seats)
				: templates.orderRefunded(order, seats, refund(orderId));
		try {
			emailGateway.send(message);
		}
		catch (EmailDeliveryException ex) {
			businessMetrics.emailFailed();
			throw ex;
		}
		businessMetrics.emailSent();
		log.info("Sent {} email eventId={} orderId={} correlationId={}",
				event.eventType(), event.eventId(), orderId, event.correlationId());
		return true;
	}

	private RefundRecord refund(UUID orderId) {
		RefundRecord refund = refundMapper.findByOrderId(orderId);
		if (refund == null) {
			throw new InvalidEventPayloadException(ORDER_REFUNDED);
		}
		return refund;
	}

	private UUID orderId(EventEnvelope<?> event) {
		UUID orderId;
		try {
			orderId = ORDER_PAID.equals(event.eventType())
					? objectMapper.convertValue(event.payload(), OrderPaidPayload.class).orderId()
					: objectMapper.convertValue(event.payload(), OrderRefundedPayload.class).orderId();
		}
		catch (IllegalArgumentException ex) {
			throw new InvalidEventPayloadException(event.eventType(), ex);
		}
		if (orderId == null) {
			throw new InvalidEventPayloadException(event.eventType());
		}
		return orderId;
	}
}
