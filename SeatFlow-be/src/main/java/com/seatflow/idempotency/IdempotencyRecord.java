package com.seatflow.idempotency;

import java.time.Instant;
import java.util.UUID;

public record IdempotencyRecord(
		UUID id,
		UUID userId,
		IdempotencyOperation operation,
		String idempotencyKey,
		String requestHash,
		Integer responseStatus,
		String responseBody,
		Instant createdAt,
		Instant expiresAt) {
}
