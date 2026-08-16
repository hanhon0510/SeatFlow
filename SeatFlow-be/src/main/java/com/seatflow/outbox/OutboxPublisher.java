package com.seatflow.outbox;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seatflow.kafka.EventEnvelope;
import com.seatflow.kafka.KafkaEventPublisher;
import com.seatflow.kafka.SeatFlowKafkaProperties;

@Service
@ConditionalOnBean(KafkaEventPublisher.class)
public class OutboxPublisher {

	private final OutboxMapper outboxMapper;
	private final KafkaEventPublisher eventPublisher;
	private final SeatFlowKafkaProperties kafkaProperties;
	private final OutboxProperties outboxProperties;
	private final ObjectMapper objectMapper;
	private final Clock clock;

	public OutboxPublisher(
			OutboxMapper outboxMapper,
			KafkaEventPublisher eventPublisher,
			SeatFlowKafkaProperties kafkaProperties,
			OutboxProperties outboxProperties,
			ObjectMapper objectMapper,
			Clock clock) {
		this.outboxMapper = outboxMapper;
		this.eventPublisher = eventPublisher;
		this.kafkaProperties = kafkaProperties;
		this.outboxProperties = outboxProperties;
		this.objectMapper = objectMapper;
		this.clock = clock;
	}

	@Transactional
	public int publishPending() {
		return publishPending(outboxProperties.publisher().batchSize());
	}

	@Transactional
	public int publishPending(int batchSize) {
		List<OutboxEventRecord> events = outboxMapper.lockPending(batchSize);
		for (OutboxEventRecord event : events) {
			publish(event);
		}
		return events.size();
	}

	private void publish(OutboxEventRecord event) {
		try {
			eventPublisher.publish(topic(event), envelope(event))
					.get(outboxProperties.publisher().publishTimeout().toMillis(), TimeUnit.MILLISECONDS);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			scheduleRetry(event);
			return;
		}
		catch (Exception ex) {
			scheduleRetry(event);
			return;
		}

		if (outboxMapper.markPublished(event.id(), clock.instant()) != 1) {
			throw new OutboxPublishException();
		}
	}

	private void scheduleRetry(OutboxEventRecord event) {
		Instant nextAttemptAt = clock.instant().plus(outboxProperties.publisher().retryDelay());
		if (outboxMapper.scheduleRetry(event.id(), nextAttemptAt) != 1) {
			throw new OutboxPublishException();
		}
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
