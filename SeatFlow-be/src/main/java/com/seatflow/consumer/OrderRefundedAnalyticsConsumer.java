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
import com.seatflow.outbox.OrderRefundedPayload;

/** Keeps the paid-order analytics honest by flagging orders that were later refunded. */
@Service
public class OrderRefundedAnalyticsConsumer {

	static final String CONSUMER_NAME = "order-refunded-analytics";
	static final String EVENT_TYPE = "OrderRefunded";

	private static final Logger log = LoggerFactory.getLogger(OrderRefundedAnalyticsConsumer.class);
	private static final int EVENT_VERSION = 1;

	private final ProcessedEventMapper processedEventMapper;
	private final OrderPaidAnalyticsMapper analyticsMapper;
	private final ObjectMapper objectMapper;
	private final Clock clock;

	public OrderRefundedAnalyticsConsumer(
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
		OrderRefundedPayload payload = payload(event);
		int claimed = processedEventMapper.insert(new ProcessedEventRecord(
				UUID.randomUUID(),
				CONSUMER_NAME,
				event.eventId(),
				clock.instant()));
		if (claimed == 0) {
			log.info("Ignoring duplicate {} eventId={} correlationId={}",
					event.eventType(), event.eventId(), event.correlationId());
			return false;
		}

		// Events for one order share a partition key, so the paid row normally exists by now. If it
		// does not (its OrderPaid event was dead-lettered), there is nothing to correct; failing here
		// would only dead-letter the refund too.
		if (analyticsMapper.markRefunded(payload.orderId(), payload.refundedAt()) == 0) {
			log.warn("No paid analytics row to mark refunded for orderId={} correlationId={}",
					payload.orderId(), event.correlationId());
		}
		log.info("Processed {} eventId={} orderId={} correlationId={}",
				event.eventType(), event.eventId(), payload.orderId(), event.correlationId());
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

	private OrderRefundedPayload payload(EventEnvelope<?> event) {
		OrderRefundedPayload payload;
		try {
			payload = objectMapper.convertValue(event.payload(), OrderRefundedPayload.class);
		}
		catch (IllegalArgumentException ex) {
			throw new InvalidEventPayloadException(EVENT_TYPE, ex);
		}
		if (payload.orderId() == null || payload.refundId() == null || payload.refundedAt() == null) {
			throw new InvalidEventPayloadException(EVENT_TYPE);
		}
		return payload;
	}
}
