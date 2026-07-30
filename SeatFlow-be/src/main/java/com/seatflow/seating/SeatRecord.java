package com.seatflow.seating;

import java.time.Instant;
import java.util.UUID;

public record SeatRecord(
		UUID id,
		UUID sectionId,
		String rowLabel,
		int seatNumber,
		String seatLabel,
		boolean accessible,
		Instant createdAt) {

	public static SeatRecord forInsert(
			UUID id,
			UUID sectionId,
			String rowLabel,
			int seatNumber,
			String seatLabel,
			boolean accessible) {
		return new SeatRecord(id, sectionId, rowLabel, seatNumber, seatLabel, accessible, null);
	}
}
