package com.seatflow.consumer;

import java.time.Instant;
import java.util.UUID;

public record ProcessedEventRecord(
		UUID id,
		String consumerName,
		UUID eventId,
		Instant processedAt) {
}
