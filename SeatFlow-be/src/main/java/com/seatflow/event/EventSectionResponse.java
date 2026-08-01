package com.seatflow.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record EventSectionResponse(
		UUID id,
		UUID eventId,
		UUID venueSectionId,
		BigDecimal price,
		boolean salesEnabled,
		Instant createdAt,
		Instant updatedAt) {

	public static EventSectionResponse from(EventSectionRecord section) {
		return new EventSectionResponse(
				section.id(),
				section.eventId(),
				section.venueSectionId(),
				section.price(),
				section.salesEnabled(),
				section.createdAt(),
				section.updatedAt());
	}
}
