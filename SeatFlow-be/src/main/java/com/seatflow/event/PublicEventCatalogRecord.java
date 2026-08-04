package com.seatflow.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PublicEventCatalogRecord(
		UUID id,
		UUID venueId,
		String venueName,
		String venueAddress,
		String venueCity,
		String venueCountry,
		String venueTimezone,
		String name,
		String description,
		Instant startTime,
		Instant salesStartTime,
		Instant salesEndTime,
		BigDecimal minimumPrice) {
}
