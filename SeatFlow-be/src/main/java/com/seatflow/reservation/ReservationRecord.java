package com.seatflow.reservation;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ReservationRecord(
		UUID id,
		UUID userId,
		UUID eventId,
		UUID holdId,
		ReservationStatus status,
		Instant expiresAt,
		BigDecimal totalAmount,
		Instant createdAt,
		Instant updatedAt) {

	public static ReservationRecord pending(
			UUID id,
			UUID userId,
			UUID eventId,
			UUID holdId,
			Instant expiresAt,
			BigDecimal totalAmount,
			Instant now) {
		return new ReservationRecord(
				id,
				userId,
				eventId,
				holdId,
				ReservationStatus.PENDING_PAYMENT,
				expiresAt,
				totalAmount,
				now,
				now);
	}
}
