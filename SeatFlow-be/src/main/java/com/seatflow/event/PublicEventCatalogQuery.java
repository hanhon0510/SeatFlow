package com.seatflow.event;

import java.time.Instant;
import java.util.UUID;

public record PublicEventCatalogQuery(
		String search,
		UUID venueId,
		Instant startDate,
		Instant endDate,
		String sort,
		int limit,
		long offset) {
}
