package com.seatflow.reservation;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ReservationResponse(
		UUID id,
		UUID userId,
		UUID eventId,
		UUID holdId,
		ReservationStatus status,
		Instant expiresAt,
		BigDecimal totalAmount,
		List<ReservationItemResponse> items,
		Instant createdAt,
		Instant updatedAt) {

	public static ReservationResponse from(
			ReservationRecord reservation,
			List<ReservationItemRecord> items) {
		return new ReservationResponse(
				reservation.id(),
				reservation.userId(),
				reservation.eventId(),
				reservation.holdId(),
				reservation.status(),
				reservation.expiresAt(),
				reservation.totalAmount(),
				items.stream().map(ReservationItemResponse::from).toList(),
				reservation.createdAt(),
				reservation.updatedAt());
	}

	public ReservationResponse {
		items = List.copyOf(items);
	}
}
