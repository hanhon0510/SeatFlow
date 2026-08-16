package com.seatflow.consumer;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.seatflow.kafka.EventEnvelope;
import com.seatflow.outbox.OrderPaidPayload;

@Service
public class OrderPaidAnalyticsConsumer {

	static final String CONSUMER_NAME = "order-paid-analytics";

	private static final Logger log = LoggerFactory.getLogger(OrderPaidAnalyticsConsumer.class);
	private static final String EVENT_TYPE = "OrderPaid";
	private static final int EVENT_VERSION = 1;

	private final ProcessedEventMapper processedEventMapper;
	private final OrderPaidAnalyticsMapper analyticsMapper;
	private final ObjectMapper objectMapper;
	private final Clock clock;

	public OrderPaidAnalyticsConsumer(
			ProcessedEventMapper processedEventMapper,
			OrderPaidAnalyticsMapper analyticsMapper,
			ObjectMapper objectMapper,
			Clock clock) {
		this.processedEventMapper = processedEventMapper;
		this.analyticsMapper = analyticsMapper;
		this.objectMapper = objectMapper;
		this.clock = clock;
	}

	@Transactional
	public boolean process(EventEnvelope<?> event) {
		validateEnvelope(event);
		OrderPaidPayload payload = payload(event);
		Instant now = clock.instant();
		int claimed = processedEventMapper.insert(new ProcessedEventRecord(
				UUID.randomUUID(),
				CONSUMER_NAME,
				event.eventId(),
				now));
		if (claimed == 0) {
			log.info(
					"Ignoring duplicate {} eventId={} correlationId={}",
					event.eventType(),
					event.eventId(),
					event.correlationId());
			return false;
		}

		if (analyticsMapper.insert(new OrderPaidAnalyticsRecord(
				UUID.randomUUID(),
				event.eventId(),
				payload.orderId(),
				payload.reservationId(),
				payload.userId(),
				payload.paymentId(),
				payload.totalAmount(),
				payload.currency(),
				payload.eventSeatIds().size(),
				event.occurredAt(),
				event.correlationId(),
				now)) != 1) {
			throw new ConsumerSideEffectException(CONSUMER_NAME);
		}

		log.info(
				"Processed {} eventId={} orderId={} correlationId={}",
				event.eventType(),
				event.eventId(),
				payload.orderId(),
				event.correlationId());
		return true;
	}

	private void validateEnvelope(EventEnvelope<?> event) {
		Objects.requireNonNull(event, "event is required");
		if (!EVENT_TYPE.equals(event.eventType())) {
			throw new UnsupportedEventTypeException(event.eventType(), EVENT_TYPE);
		}
		if (event.eventVersion() != EVENT_VERSION) {
			throw new UnsupportedEventVersionException(event.eventType(), event.eventVersion(), EVENT_VERSION);
		}
	}

	private OrderPaidPayload payload(EventEnvelope<?> event) {
		OrderPaidPayload payload;
		try {
			payload = objectMapper.convertValue(event.payload(), OrderPaidPayload.class);
		}
		catch (IllegalArgumentException ex) {
			throw new InvalidEventPayloadException(EVENT_TYPE, ex);
		}
		if (payload.orderId() == null
				|| payload.reservationId() == null
				|| payload.userId() == null
				|| payload.paymentId() == null
				|| payload.totalAmount() == null
				|| payload.currency() == null
				|| payload.currency().isBlank()
				|| payload.eventSeatIds() == null
				|| payload.eventSeatIds().isEmpty()
				|| payload.paidAt() == null
				|| payload.eventSeatIds().stream().anyMatch(Objects::isNull)) {
			throw new InvalidEventPayloadException(EVENT_TYPE);
		}
		return payload;
	}
}
