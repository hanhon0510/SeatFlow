package com.seatflow.seating;

import java.time.Instant;
import java.util.UUID;

public record SeatLayoutRow(
		UUID sectionId,
		String sectionName,
		int displayOrder,
		Instant sectionCreatedAt,
		UUID seatId,
		String rowLabel,
		Integer seatNumber,
		String seatLabel,
		Boolean accessible,
		Instant seatCreatedAt) {
}
