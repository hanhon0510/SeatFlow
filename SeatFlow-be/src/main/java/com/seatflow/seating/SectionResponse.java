package com.seatflow.seating;

import java.time.Instant;
import java.util.UUID;

public record SectionResponse(
		UUID id,
		UUID venueId,
		String name,
		int displayOrder,
		Instant createdAt) {

	public static SectionResponse from(VenueSectionRecord section) {
		return new SectionResponse(
				section.id(),
				section.venueId(),
				section.name(),
				section.displayOrder(),
				section.createdAt());
	}
}
