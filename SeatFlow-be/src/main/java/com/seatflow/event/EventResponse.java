package com.seatflow.event;

import java.time.Instant;
import java.util.UUID;

public record EventResponse(
		UUID id,
		UUID venueId,
		String name,
		String description,
		Instant startTime,
		Instant salesStartTime,
		Instant salesEndTime,
		EventStatus status,
		Instant createdAt,
		Instant updatedAt) {

	public static EventResponse from(EventRecord event) {
		return new EventResponse(
				event.id(),
				event.venueId(),
				event.name(),
				event.description(),
				event.startTime(),
				event.salesStartTime(),
				event.salesEndTime(),
				event.status(),
				event.createdAt(),
				event.updatedAt());
	}
}
