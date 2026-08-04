package com.seatflow.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PublicEventResponse(
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

	public static PublicEventResponse from(PublicEventCatalogRecord event) {
		return new PublicEventResponse(
				event.id(),
				event.venueId(),
				event.venueName(),
				event.venueAddress(),
				event.venueCity(),
				event.venueCountry(),
				event.venueTimezone(),
				event.name(),
				event.description(),
				event.startTime(),
				event.salesStartTime(),
				event.salesEndTime(),
				event.minimumPrice());
	}
}
