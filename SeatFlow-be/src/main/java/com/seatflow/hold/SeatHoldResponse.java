package com.seatflow.hold;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SeatHoldResponse(
		UUID holdId,
		UUID eventId,
		UUID eventSeatId,
		List<UUID> eventSeatIds,
		UUID userId,
		Instant expiresAt) {

	public SeatHoldResponse(UUID holdId, UUID eventId, UUID eventSeatId, UUID userId, Instant expiresAt) {
		this(holdId, eventId, eventSeatId, List.of(eventSeatId), userId, expiresAt);
	}

	public SeatHoldResponse {
		eventSeatIds = List.copyOf(eventSeatIds);
	}
}
