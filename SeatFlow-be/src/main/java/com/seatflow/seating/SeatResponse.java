package com.seatflow.seating;

import java.time.Instant;
import java.util.UUID;

public record SeatResponse(
		UUID id,
		UUID sectionId,
		String rowLabel,
		int seatNumber,
		String seatLabel,
		boolean accessible,
		Instant createdAt) {

	public static SeatResponse from(SeatRecord seat) {
		return new SeatResponse(
				seat.id(),
				seat.sectionId(),
				seat.rowLabel(),
				seat.seatNumber(),
				seat.seatLabel(),
				seat.accessible(),
				seat.createdAt());
	}

	public static SeatResponse fromLayoutRow(SeatLayoutRow row) {
		return new SeatResponse(
				row.seatId(),
				row.sectionId(),
				row.rowLabel(),
				row.seatNumber(),
				row.seatLabel(),
				Boolean.TRUE.equals(row.accessible()),
				row.seatCreatedAt());
	}
}
