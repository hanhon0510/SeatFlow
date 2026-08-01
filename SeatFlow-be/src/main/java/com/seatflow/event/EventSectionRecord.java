package com.seatflow.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record EventSectionRecord(
		UUID id,
		UUID eventId,
		UUID venueSectionId,
		BigDecimal price,
		boolean salesEnabled,
		Instant createdAt,
		Instant updatedAt) {

	public static EventSectionRecord forInsert(
			UUID id,
			UUID eventId,
			UUID venueSectionId,
			BigDecimal price,
			boolean salesEnabled) {
		return new EventSectionRecord(id, eventId, venueSectionId, price, salesEnabled, null, null);
	}
}
