package com.seatflow.kafka;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record EventEnvelope<T>(
		UUID eventId,
		String eventType,
		int eventVersion,
		UUID aggregateId,
		UUID correlationId,
		Instant occurredAt,
		T payload) {

	public EventEnvelope {
		Objects.requireNonNull(eventId, "eventId is required");
		Objects.requireNonNull(eventType, "eventType is required");
		Objects.requireNonNull(aggregateId, "aggregateId is required");
		Objects.requireNonNull(correlationId, "correlationId is required");
		Objects.requireNonNull(occurredAt, "occurredAt is required");
		Objects.requireNonNull(payload, "payload is required");
		if (eventType.isBlank()) {
			throw new IllegalArgumentException("eventType is required");
		}
		if (eventVersion < 1) {
			throw new IllegalArgumentException("eventVersion must be positive");
		}
	}

	public static <T> EventEnvelope<T> create(
			String eventType,
			int eventVersion,
			UUID aggregateId,
			UUID correlationId,
			T payload) {
		return new EventEnvelope<>(
				UUID.randomUUID(),
				eventType,
				eventVersion,
				aggregateId,
				correlationId,
				Instant.now(),
				payload);
	}
}
