package com.seatflow.hold;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SeatHoldRecord(
		UUID holdId,
		UUID eventId,
		List<UUID> eventSeatIds,
		List<UUID> seatIds,
		UUID userId,
		Instant expiresAt) {

	public SeatHoldRecord(
			UUID holdId,
			UUID eventId,
			UUID eventSeatId,
			UUID seatId,
			UUID userId,
			Instant expiresAt) {
		this(holdId, eventId, List.of(eventSeatId), List.of(seatId), userId, expiresAt);
	}

	public SeatHoldRecord {
		eventSeatIds = List.copyOf(eventSeatIds);
		seatIds = List.copyOf(seatIds);
	}

	public UUID eventSeatId() {
		return eventSeatIds.getFirst();
	}

	public UUID seatId() {
		return seatIds.getFirst();
	}
}
