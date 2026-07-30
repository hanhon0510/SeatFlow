package com.seatflow.venue;

import java.time.Instant;
import java.util.UUID;

public record VenueRecord(
		UUID id,
		String name,
		String address,
		String city,
		String country,
		String timezone,
		VenueStatus status,
		Instant createdAt,
		Instant updatedAt) {

	public static VenueRecord forInsert(
			UUID id,
			String name,
			String address,
			String city,
			String country,
			String timezone) {
		return new VenueRecord(id, name, address, city, country, timezone, null, null, null);
	}

	public static VenueRecord forUpdate(
			UUID id,
			String name,
			String address,
			String city,
			String country,
			String timezone) {
		return new VenueRecord(id, name, address, city, country, timezone, null, null, null);
	}
}
