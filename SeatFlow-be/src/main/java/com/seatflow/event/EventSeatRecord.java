package com.seatflow.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record EventSeatRecord(
		UUID id,
		UUID eventId,
		UUID seatId,
		BigDecimal price,
		EventSeatStatus permanentStatus,
		int version,
		Instant createdAt,
		Instant updatedAt) {
}
