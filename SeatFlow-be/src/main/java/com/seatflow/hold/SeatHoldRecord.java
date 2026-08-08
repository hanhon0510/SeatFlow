package com.seatflow.hold;

import java.time.Instant;
import java.util.UUID;

public record SeatHoldRecord(
		UUID holdId,
		UUID eventId,
		UUID eventSeatId,
		UUID seatId,
		UUID userId,
		Instant expiresAt) {
}
