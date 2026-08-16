package com.seatflow.outbox;

import java.time.Instant;
import java.util.UUID;

public record OutboxEventRecord(
		UUID id,
		String aggregateType,
		UUID aggregateId,
		String eventType,
		int eventVersion,
		String payload,
		UUID correlationId,
		OutboxEventStatus status,
		int attemptCount,
		Instant createdAt,
		Instant publishedAt,
		Instant nextAttemptAt) {
}
