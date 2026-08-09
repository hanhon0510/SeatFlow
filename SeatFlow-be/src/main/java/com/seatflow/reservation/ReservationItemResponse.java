package com.seatflow.reservation;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ReservationItemResponse(
		UUID id,
		UUID eventSeatId,
		BigDecimal price,
		Instant createdAt) {

	public static ReservationItemResponse from(ReservationItemRecord item) {
		return new ReservationItemResponse(
				item.id(),
				item.eventSeatId(),
				item.price(),
				item.createdAt());
	}
}
