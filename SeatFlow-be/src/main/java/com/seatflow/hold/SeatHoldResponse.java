package com.seatflow.hold;

import java.time.Instant;
import java.util.UUID;

public record SeatHoldResponse(
		UUID holdId,
		UUID eventId,
		UUID eventSeatId,
		UUID userId,
		Instant expiresAt) {
}
