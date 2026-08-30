package com.seatflow.outbox;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seatflow.kafka.EventEnvelope;
import com.seatflow.kafka.KafkaEventPublisher;
import com.seatflow.kafka.SeatFlowKafkaProperties;
import com.seatflow.observability.BusinessMetrics;

@Service
@ConditionalOnBean(KafkaEventPublisher.class)
public class OutboxPublisher {

	private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

	private static final String ORDER_PAID_EVENT = "OrderPaid";
	private static final int MAX_FAILURE_REASON_LENGTH = 500;

	private final OutboxMapper outboxMapper;
	private final KafkaEventPublisher eventPublisher;
	private final SeatFlowKafkaProperties kafkaProperties;
	private final OutboxProperties outboxProperties;
	private final ObjectMapper objectMapper;
	private final Clock clock;
	private final BusinessMetrics businessMetrics;

	public OutboxPublisher(
			OutboxMapper outboxMapper,
			KafkaEventPublisher eventPublisher,
			SeatFlowKafkaProperties kafkaProperties,
			OutboxProperties outboxProperties,
			ObjectMapper objectMapper,
			Clock clock,
			BusinessMetrics businessMetrics) {
		this.outboxMapper = outboxMapper;
		this.eventPublisher = eventPublisher;
		this.kafkaProperties = kafkaProperties;
		this.outboxProperties = outboxProperties;
		this.objectMapper = objectMapper;
		this.clock = clock;
		this.businessMetrics = businessMetrics;
	}

	public int publishPending() {
		return publishPending(outboxProperties.publisher().batchSize());
	}

	/**
	 * Claims a batch, then publishes it <em>outside</em> any database transaction.
	 *
	 * <p>Publishing used to happen inside {@code @Transactional}, which meant a broker outage
	 * held one transaction open for {@code batchSize * publishTimeout} — long enough to pin
	 * the xmin horizon and stop VACUUM from reclaiming dead tuples on the booking tables.
	 * The claim is now a single atomic statement and every status write is its own
	 * auto-committed statement, so no transaction outlives a single round trip.
	 *
	 * @return the number of events actually published in this pass
	 */
	public int publishPending(int batchSize) {
		OutboxProperties.Publisher publisher = outboxProperties.publisher();
		Instant now = clock.instant();
		List<OutboxEventRecord> events = outboxMapper.claimPending(
				batchSize,
				now,
				now.plus(publisher.claimLease()));

		long deadlineNanos = System.nanoTime() + publisher.passTimeout().toNanos();
		int published = 0;
		for (int index = 0; index < events.size(); index++) {
			if (System.nanoTime() - deadlineNanos >= 0) {
				log.warn(
						"Outbox pass exceeded its {} budget with {} of {} claimed events unattempted; "
								+ "they stay claimed until the lease lapses",
						publisher.passTimeout(),
						events.size() - index,
						events.size());
				break;
			}
			if (publish(events.get(index))) {
				published++;
			}
		}
		return published;
	}

	private boolean publish(OutboxEventRecord event) {
		// Resolved before the try so that a payload we can never serialise, or a type with no
		// topic, is treated as the permanent failure it is rather than as a transport blip that
		// would be retried forever.
		String topic;
		EventEnvelope<JsonNode> envelope;
		try {
			topic = topic(event);
			envelope = envelope(event);
		}
		catch (RuntimeException ex) {
			markFailed(event, ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
			return false;
		}

		try {
			eventPublisher.publish(topic, envelope)
					.get(outboxProperties.publisher().publishTimeout().toMillis(), TimeUnit.MILLISECONDS);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			scheduleRetry(event);
			return false;
		}
		catch (Exception ex) {
			scheduleRetry(event);
			return false;
		}

		if (outboxMapper.markPublished(event.id(), clock.instant()) != 1) {
			throw new OutboxPublishException();
		}
		return true;
	}

	/**
	 * Retries a transport failure, or gives up once the attempt cap is reached. Without the cap
	 * an event that the broker keeps rejecting is re-attempted indefinitely, with nothing to show
	 * for it but a growing attempt_count.
	 */
	private void scheduleRetry(OutboxEventRecord event) {
		int maxAttempts = outboxProperties.publisher().maxAttempts();
		if (event.attemptCount() + 1 >= maxAttempts) {
			markFailed(event, "Giving up after %d publish attempts".formatted(maxAttempts));
			return;
		}
		Instant nextAttemptAt = clock.instant().plus(retryDelay(event.attemptCount()));
		if (outboxMapper.scheduleRetry(event.id(), nextAttemptAt) != 1) {
			throw new OutboxPublishException();
		}
		businessMetrics.outboxPublishFailure();
	}

	private void markFailed(OutboxEventRecord event, String failureReason) {
		log.error(
				"Giving up on outbox event {} of type {} after {} attempts: {}",
				event.id(),
				event.eventType(),
				event.attemptCount(),
				failureReason);
		if (outboxMapper.markFailed(event.id(), truncate(failureReason)) != 1) {
			throw new OutboxPublishException();
		}
		businessMetrics.outboxPublishFailure();
		businessMetrics.outboxEventAbandoned();
	}

	private static String truncate(String failureReason) {
		String reason = failureReason == null || failureReason.isBlank() ? "unknown" : failureReason;
		return reason.length() <= MAX_FAILURE_REASON_LENGTH
				? reason
				: reason.substring(0, MAX_FAILURE_REASON_LENGTH);
	}

	private Duration retryDelay(int attemptCount) {
		OutboxProperties.Publisher publisher = outboxProperties.publisher();
		Duration delay = publisher.retryDelay();
		Duration maxDelay = publisher.retryMaxDelay();
		int growthSteps = Math.min(Math.max(attemptCount, 0), 20);
		for (int i = 0; i < growthSteps && delay.compareTo(maxDelay) < 0; i++) {
			try {
				delay = delay.multipliedBy(2);
			}
			catch (ArithmeticException ex) {
				return maxDelay;
			}
		}
		return delay.compareTo(maxDelay) > 0 ? maxDelay : delay;
	}

	private EventEnvelope<JsonNode> envelope(OutboxEventRecord event) {
		try {
			return new EventEnvelope<>(
					event.id(),
					event.eventType(),
					event.eventVersion(),
					event.aggregateId(),
					event.correlationId(),
					event.createdAt(),
					objectMapper.readTree(event.payload()));
		}
		catch (JsonProcessingException ex) {
			throw new OutboxPublishException(ex);
		}
	}

	/**
	 * An unmapped event type used to fall through to the dead-letter topic, where the send
	 * succeeded and the row was marked PUBLISHED as though it had been delivered normally - no
	 * error, no metric, no log. Adding a second event type would have gone straight there
	 * unnoticed. Treat it as the programming error it is instead.
	 */
	private String topic(OutboxEventRecord event) {
		if (ORDER_PAID_EVENT.equals(event.eventType())) {
			return kafkaProperties.topics().orderEvents();
		}
		throw new IllegalStateException("No topic mapped for outbox event type " + event.eventType());
	}
}
