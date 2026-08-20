package com.seatflow.seatupdates;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SeatStateUpdateMessage(
		SeatStateChangeType type,
		UUID eventId,
		List<UUID> eventSeatIds,
		Instant occurredAt) {

	public SeatStateUpdateMessage {
		eventSeatIds = List.copyOf(eventSeatIds);
	}
}
