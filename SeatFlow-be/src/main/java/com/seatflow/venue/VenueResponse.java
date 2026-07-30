package com.seatflow.venue;

import java.time.Instant;
import java.util.UUID;

public record VenueResponse(
		UUID id,
		String name,
		String address,
		String city,
		String country,
		String timezone,
		VenueStatus status,
		Instant createdAt,
		Instant updatedAt) {

	public static VenueResponse from(VenueRecord venue) {
		return new VenueResponse(
				venue.id(),
				venue.name(),
				venue.address(),
				venue.city(),
				venue.country(),
				venue.timezone(),
				venue.status(),
				venue.createdAt(),
				venue.updatedAt());
	}
}
