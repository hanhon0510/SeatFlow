package com.seatflow.event;

import java.time.Instant;
import java.util.UUID;

public record EventRecord(
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

	public static EventRecord forInsert(
			UUID id,
			UUID venueId,
			String name,
			String description,
			Instant startTime,
			Instant salesStartTime,
			Instant salesEndTime) {
		return new EventRecord(
				id,
				venueId,
				name,
				description,
				startTime,
				salesStartTime,
				salesEndTime,
				null,
				null,
				null);
	}

	public static EventRecord forUpdate(
			UUID id,
			UUID venueId,
			String name,
			String description,
			Instant startTime,
			Instant salesStartTime,
			Instant salesEndTime) {
		return new EventRecord(
				id,
				venueId,
				name,
				description,
				startTime,
				salesStartTime,
				salesEndTime,
				null,
				null,
				null);
	}
}
