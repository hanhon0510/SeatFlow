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
		boolean transfersAllowed,
		EventStatus status,
		Instant createdAt,
		Instant updatedAt) {

	/** An event that allows ticket transfers, the default. */
	public EventRecord(
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
		this(id, venueId, name, description, startTime, salesStartTime, salesEndTime, true, status, createdAt,
				updatedAt);
	}

	public static EventRecord forInsert(
			UUID id,
			UUID venueId,
			String name,
			String description,
			Instant startTime,
			Instant salesStartTime,
			Instant salesEndTime) {
		return forInsert(id, venueId, name, description, startTime, salesStartTime, salesEndTime, true);
	}

	public static EventRecord forInsert(
			UUID id,
			UUID venueId,
			String name,
			String description,
			Instant startTime,
			Instant salesStartTime,
			Instant salesEndTime,
			boolean transfersAllowed) {
		return new EventRecord(
				id,
				venueId,
				name,
				description,
				startTime,
				salesStartTime,
				salesEndTime,
				transfersAllowed,
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
		return forInsert(id, venueId, name, description, startTime, salesStartTime, salesEndTime, true);
	}

	public static EventRecord forUpdate(
			UUID id,
			UUID venueId,
			String name,
			String description,
			Instant startTime,
			Instant salesStartTime,
			Instant salesEndTime,
			boolean transfersAllowed) {
		return forInsert(id, venueId, name, description, startTime, salesStartTime, salesEndTime, transfersAllowed);
	}
}
