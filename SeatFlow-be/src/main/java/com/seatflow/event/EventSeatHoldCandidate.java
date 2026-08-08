package com.seatflow.event;

import java.time.Instant;
import java.util.UUID;

public record EventSeatHoldCandidate(
		UUID eventId,
		UUID eventSeatId,
		UUID seatId,
		EventStatus eventStatus,
		Instant salesStartTime,
		Instant salesEndTime,
		EventSeatStatus permanentStatus) {
}
