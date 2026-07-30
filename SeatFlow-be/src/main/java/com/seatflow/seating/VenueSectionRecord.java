package com.seatflow.seating;

import java.time.Instant;
import java.util.UUID;

public record VenueSectionRecord(
		UUID id,
		UUID venueId,
		String name,
		int displayOrder,
		Instant createdAt) {

	public static VenueSectionRecord forInsert(UUID id, UUID venueId, String name, int displayOrder) {
		return new VenueSectionRecord(id, venueId, name, displayOrder, null);
	}

	public static VenueSectionRecord forUpdate(UUID id, String name, int displayOrder) {
		return new VenueSectionRecord(id, null, name, displayOrder, null);
	}
}
