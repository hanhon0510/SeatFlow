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
		try {
			eventPublisher.publish(topic(event), envelope(event))
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

	private void scheduleRetry(OutboxEventRecord event) {
		Instant nextAttemptAt = clock.instant().plus(retryDelay(event.attemptCount()));
		if (outboxMapper.scheduleRetry(event.id(), nextAttemptAt) != 1) {
			throw new OutboxPublishException();
		}
		businessMetrics.outboxPublishFailure();
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

	private String topic(OutboxEventRecord event) {
		if ("OrderPaid".equals(event.eventType())) {
			return kafkaProperties.topics().orderEvents();
		}
		return kafkaProperties.topics().deadLetter();
	}
}
